package com.xiaozhi.dialogue.llm;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.api.StreamResponseListener;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.ChatMemory;
import com.xiaozhi.dialogue.llm.tool.XiaoZhiToolCallingManager;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.mcp.McpSessionManager;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.utils.EmojiUtils;
import org.springframework.context.ApplicationContext;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;

/**
 *
 * 负责管理和协调LLM相关功能
 * 未来考虑：改成Domain Entity: ChatRole(聊天角色)，管理对话历史记录，管理对话工具调用等。
 * 未来考虑：在连接通过认证，可正常对话时，创建实例，构建好一个完整的Role。
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String TOOL_CONTEXT_SESSION_KEY = "session";

    // 句子结束标点符号模式（中英文句号、感叹号、问号）
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("[。！？!?]");

    // 逗号、分号等停顿标点
    private static final Pattern PAUSE_PATTERN = Pattern.compile("[，、；,;]");

    // 冒号和引号等特殊标点
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[：:\"]");

    // 换行符
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\n\r]");

    // 数字模式（用于检测小数点是否在数字中）
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.\\d+");

    // 表情符号模式
    private static final Pattern EMOJI_PATTERN = Pattern.compile("\\p{So}|\\p{Sk}|\\p{Sm}");

    // 最小句子长度（字符数）
    private static final int MIN_SENTENCE_LENGTH = 5;

    // 新句子判断的字符阈值
    private static final int NEW_SENTENCE_TOKEN_THRESHOLD = 8;


    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private McpSessionManager mcpSessionManager;

    @Resource
    private ToolCallingManager toolCallingManager;
    
    @Resource
    private SessionManager sessionManager;
    
    @Resource
    private ApplicationContext applicationContext;

    /**
     * 从流式响应中提取工具名称
     * 
     * @param session 聊天会话
     * @param message 用户消息
     * @param useFunctionCall 是否使用函数调用
     * @param chatResponses 收集的所有ChatResponse
     * @param fullResponse 完整的响应文本
     * @param hasToolCalls 是否有工具调用的标记
     * @return 提取到的工具名称，如果没有找到则返回空字符串
     */
    private String getToolName(ChatSession session, String message, boolean useFunctionCall,
                              List<ChatResponse> chatResponses, String fullResponse, boolean hasToolCalls) {
        StringBuilder toolName = new StringBuilder();
        
        // 在流式响应完成后，如果有工具调用但没有获取到工具名称，尝试从完整的响应中提取
        if (useFunctionCall && hasToolCalls && toolName.length() == 0 && !chatResponses.isEmpty()) {
            // 尝试从最后一个包含工具调用的响应中获取工具名称
            for (int i = chatResponses.size() - 1; i >= 0; i--) {
                ChatResponse response = chatResponses.get(i);
                Generation generation = response.getResult();
                if (generation != null) {
                    AssistantMessage assistantMessage = generation.getOutput();
                    if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                        String toolCallName = assistantMessage.getToolCalls().get(0).name();
                        if (StringUtils.hasText(toolCallName)) {
                            toolName.setLength(0);
                            toolName.append(toolCallName);
                            break;
                        }
                    }
                }
            }
            
            // 如果仍然没有获取到工具名称，尝试使用ToolCallingManager处理
            if (toolName.length() == 0) {
                try {
                    // 构建一个完整的ChatResponse用于工具调用处理
                    ChatResponse completeResponse = ChatResponse.builder()
                            .generations(chatResponses.stream()
                                    .map(ChatResponse::getResult)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList()))
                            .build();
                    
                    // 构建Prompt
                    UserMessage userMessage = new UserMessage(message);
                    Long userTimeMillis = session.getUserTimeMillis();
                    Conversation conversation = session.getConversation();
                    conversation.add(userMessage, userTimeMillis);
                    List<Message> messages = session.getConversation().messages();
                    ChatOptions chatOptions = ToolCallingChatOptions.builder()
                            .toolCallbacks(useFunctionCall && session.isSupportFunctionCall() ? session.getToolCallbacks() : new ArrayList<>())
                            .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                            .build();
                    Prompt prompt = new Prompt(messages, chatOptions);
                    
                    // 使用ToolCallingManager处理工具调用
                    ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, completeResponse);
                    
                    // 从工具执行结果中提取工具名称
                    List<Message> conversationHistory = toolExecutionResult.conversationHistory();
                    for (Message msg : conversationHistory) {
                        if (msg instanceof AssistantMessage) {
                            AssistantMessage assistantMsg = (AssistantMessage) msg;
                            if (!CollectionUtils.isEmpty(assistantMsg.getToolCalls())) {
                                String extractedToolName = assistantMsg.getToolCalls().get(0).name();
                                if (StringUtils.hasText(extractedToolName)) {
                                    toolName.setLength(0);
                                    toolName.append(extractedToolName);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Ошибка при попытке использовать ToolCallingManager для получения имени инструмента: {}", e.getMessage(), e);
                }
            }
        }
        
        // 如果仍然没有获取到工具名称，尝试从会话的工具回调中推断
        if (toolName.length() == 0 && useFunctionCall && session.isSupportFunctionCall()) {
            List<ToolCallback> toolCallbacks = session.getToolCallbacks();
            if (!CollectionUtils.isEmpty(toolCallbacks)) {
                // 如果只有一个工具回调，使用它的名称
                if (toolCallbacks.size() == 1) {
                    String inferredToolName = toolCallbacks.get(0).getToolDefinition().name();
                    toolName.setLength(0);
                    toolName.append(inferredToolName);
                } else {
                    // 如果有多个工具回调，尝试从响应内容中推断
                    String responseText = fullResponse.toLowerCase();
                    for (ToolCallback callback : toolCallbacks) {
                        String callbackName = callback.getToolDefinition().name().toLowerCase();
                        if (responseText.contains(callbackName.replace("_", " ")) || 
                            responseText.contains(callbackName.replace("xiaoZhi_mcp_client_", ""))) {
                            toolName.setLength(0);
                            toolName.append(callback.getToolDefinition().name());
                            break;
                        }
                    }
                }
            }
        }
        
        // 如果仍然没有获取到工具名称，尝试从XiaoZhiToolCallingManager的记录中获取
        if (toolName.length() == 0 && useFunctionCall) {
            // 获取对话时间戳
            Long conversationTimestamp = session.getAssistantTimeMillis();
            if (conversationTimestamp == null) {
                conversationTimestamp = System.currentTimeMillis();
            }
            
            String recordedToolName = XiaoZhiToolCallingManager.getRecentToolCall(session.getSessionId(), conversationTimestamp);
            if (StringUtils.hasText(recordedToolName)) {
                toolName.setLength(0);
                toolName.append(recordedToolName);
            }
        }
        
        return toolName.toString();
    }

    /**
     * 处理用户查询（同步方式）
     * 
     * @param session         会话信息
     * @param message         用户消息
     * @param useFunctionCall 是否使用函数调用
     * @return 模型回复
     */
    public String chat(ChatSession session, String message, boolean useFunctionCall) {
        try {
            if(useFunctionCall){
                //处理mcp自定义
                mcpSessionManager.customMcpHandler(session);
            }

            // 获取ChatModel
            ChatModel chatModel = chatModelFactory.takeChatModel(session);

            // 获取对话时间戳
            Long conversationTimestamp = session.getAssistantTimeMillis();
            if (conversationTimestamp == null) {
                conversationTimestamp = System.currentTimeMillis();
            }

            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(useFunctionCall && session.isSupportFunctionCall() ? session.getToolCallbacks() : new ArrayList<>())
                    .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                    .toolContext("conversationTimestamp", conversationTimestamp)
                    .build();

            UserMessage userMessage = new UserMessage(message);
            Long userTimeMillis = session.getUserTimeMillis();
            Conversation conversation = session.getConversation();
            conversation.add(userMessage, userTimeMillis);
            List<Message> messages = conversation.messages();
            Prompt prompt = new Prompt(messages,chatOptions);

            // Логирование всех параметров запроса
            logRequestParameters(chatModel, prompt, chatOptions, session);

            ChatResponse chatResponse = chatModel.call(prompt);
            if (chatResponse == null || chatResponse.getResult().getOutput().getText() == null) {
                logger.warn("Ответ модели пуст или не содержит сгенерированного содержимого");
                return "Извините, я столкнулся с проблемой при обработке вашего запроса. Пожалуйста, попробуйте позже.";
            }
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

            // 检查是否有工具调用，如果有则处理工具调用
            if (useFunctionCall && !CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                // 使用工具调用管理器处理工具调用
                ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
                
                // 获取工具调用后的最终响应
                List<Message> conversationHistory = toolExecutionResult.conversationHistory();
                if (!conversationHistory.isEmpty()) {
                    Message lastMessage = conversationHistory.get(conversationHistory.size() - 1);
                    if (lastMessage instanceof AssistantMessage) {
                        assistantMessage = (AssistantMessage) lastMessage;
                    }
                }
                
                // 如果工具调用返回直接结果，则直接返回
                if (toolExecutionResult.returnDirect()) {
                    return assistantMessage.getText();
                }
            }

            final AssistantMessage finalAssistantMessage = assistantMessage;
            Thread.startVirtualThread(() -> {// 异步持久化
                // 保存AI消息，会被持久化至数据库。
                session.getConversation().add(finalAssistantMessage,session.getAssistantTimeMillis());
            });
            return assistantMessage.getText();

        } catch (Exception e) {
            logger.error("Ошибка при обработке запроса: {}", e.getMessage(), e);
            return "Извините, я столкнулся с проблемой при обработке вашего запроса. Пожалуйста, попробуйте позже.";
        }
    }

    /**
     * 处理用户查询（流式方式）
     *
     * @param message         用户消息
     * @param useFunctionCall 是否使用函数调用
     */
    public Flux<ChatResponse> chatStream(ChatSession session, String message,
            boolean useFunctionCall) {
        if(useFunctionCall){
            //处理mcp自定义
            mcpSessionManager.customMcpHandler(session);
        }

        // 获取ChatModel
        ChatModel chatModel = chatModelFactory.takeChatModel(session);

        // 获取对话时间戳
        Long conversationTimestamp = session.getAssistantTimeMillis();
        if (conversationTimestamp == null) {
            conversationTimestamp = System.currentTimeMillis();
        }

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(useFunctionCall && session.isSupportFunctionCall() ? session.getToolCallbacks() : new ArrayList<>())
                .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                .toolContext("conversationTimestamp", conversationTimestamp)
                .build();

        UserMessage userMessage = new UserMessage(message);
        Long userTimeMillis = session.getUserTimeMillis();
        Conversation conversation = session.getConversation();
        conversation.add(userMessage, userTimeMillis);
        List<Message> messages = conversation.messages();
        Prompt prompt = new Prompt(messages, chatOptions);

        // Логирование всех параметров запроса
        logRequestParameters(chatModel, prompt, chatOptions, session);

        // 调用实际的流式聊天方法
        return chatModel.stream(prompt);
    }
    
    /**
     * Логирование всех параметров запроса для отладки
     */
    private void logRequestParameters(ChatModel chatModel, Prompt prompt, ChatOptions chatOptions, ChatSession session) {
        try {
            logger.info("=== ПАРАМЕТРЫ ЗАПРОСА К LLM ===");
            logger.info("ChatModel type: {}", chatModel.getClass().getName());
            
            // Логирование defaultOptions из ChatModel (если доступно)
            if (chatModel instanceof org.springframework.ai.openai.OpenAiChatModel openAiChatModel) {
                try {
                    var defaultOptions = openAiChatModel.getDefaultOptions();
                    if (defaultOptions != null) {
                        logger.info("DefaultOptions model: {}", defaultOptions.getModel());
                        logger.info("DefaultOptions temperature: {}", defaultOptions.getTemperature());
                        logger.info("DefaultOptions topP: {}", defaultOptions.getTopP());
                        logger.info("DefaultOptions maxTokens: {}", defaultOptions.getMaxTokens());
                        logger.info("DefaultOptions class: {}", defaultOptions.getClass().getName());
                    } else {
                        logger.warn("DefaultOptions is null!");
                    }
                } catch (Exception e) {
                    logger.warn("Не удалось получить defaultOptions: {}", e.getMessage());
                }
            }
            
            // Логирование ChatOptions из Prompt
            logger.info("Prompt ChatOptions type: {}", chatOptions != null ? chatOptions.getClass().getName() : "null");
            if (chatOptions instanceof org.springframework.ai.model.tool.ToolCallingChatOptions toolOptions) {
                logger.info("ToolCallingChatOptions toolCallbacks count: {}", 
                        toolOptions.getToolCallbacks() != null ? toolOptions.getToolCallbacks().size() : 0);
                logger.info("ToolCallingChatOptions toolContext keys: {}", 
                        toolOptions.getToolContext() != null ? toolOptions.getToolContext().keySet() : "null");
            }
            
            // Проверка, является ли ChatOptions также OpenAiChatOptions
            if (chatOptions instanceof org.springframework.ai.openai.OpenAiChatOptions openAiOptions) {
                logger.info("ChatOptions is OpenAiChatOptions!");
                logger.info("ChatOptions model: {}", openAiOptions.getModel());
                logger.info("ChatOptions temperature: {}", openAiOptions.getTemperature());
            } else {
                logger.warn("ChatOptions НЕ является OpenAiChatOptions! Тип: {}", 
                        chatOptions != null ? chatOptions.getClass().getName() : "null");
            }
            
            // Логирование сообщений
            List<Message> promptMessages = prompt.getInstructions();
            logger.info("Messages count: {}", promptMessages != null ? promptMessages.size() : 0);
            if (promptMessages != null) {
                for (int i = 0; i < promptMessages.size(); i++) {
                    Message msg = promptMessages.get(i);
                    String text = msg.getText() != null ? msg.getText() : "";
                    logger.info("Message[{}]: type={}, content length={}", 
                            i, msg.getClass().getSimpleName(), text.length());
                }
            }
            
            // Получение модели из конфигурации
            String modelName = chatModelFactory.getModelName(session);
            logger.info("Model from config: {}", modelName);
            
            logger.info("=== КОНЕЦ ПАРАМЕТРОВ ЗАПРОСА ===");
        } catch (Exception e) {
            logger.error("Ошибка при логировании параметров запроса: {}", e.getMessage(), e);
        }
    }

    public void chatStreamBySentence(ChatSession session, String message, boolean useFunctionCall,
            TriConsumer<String, Boolean, Boolean> sentenceHandler) {
        try {
            // 在对话开始时清除工具调用记录，确保每次对话都是干净的
            XiaoZhiToolCallingManager.clearRecentToolCall(session.getSessionId());
            
            // 创建流式响应监听器
            StreamResponseListener streamListener = new TokenStreamResponseListener(session, message, sentenceHandler, useFunctionCall);
            final StringBuilder toolName = new StringBuilder(); // 当前句子的缓冲区
            final StringBuilder fullResponse = new StringBuilder(); // 完整响应的缓冲区
            final List<ChatResponse> chatResponses = new ArrayList<>(); // 收集所有的ChatResponse
            final AtomicBoolean hasToolCalls = new AtomicBoolean(false); // 标记是否有工具调用
            
            AtomicReference<Usage> llmUsage = new AtomicReference<>();
            // 调用现有的流式方法
            chatStream(session, message, useFunctionCall)
                    .subscribe(
                            chatResponse -> {
                                // 收集所有的ChatResponse用于后续处理
                                chatResponses.add(chatResponse);
                                
                                String token = chatResponse.getResult() == null
                                        || chatResponse.getResult().getOutput() == null
                                        || chatResponse.getResult().getOutput().getText() == null ? ""
                                                : chatResponse.getResult().getOutput().getText();
                                if (!token.isEmpty()) {
                                    fullResponse.append(token);
                                    streamListener.onToken(token);
                                }
                                
                                // 检查是否有工具调用
                                if (useFunctionCall) {
                                    Generation generation = chatResponse.getResult();
                                    if (generation != null) {
                                        // 检查AssistantMessage是否有工具调用
                                        AssistantMessage assistantMessage = generation.getOutput();
                                        if (assistantMessage.hasToolCalls() || !CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                                            hasToolCalls.set(true);
                                            if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                                                String toolCallName = assistantMessage.getToolCalls().get(0).name();
                                                if (StringUtils.hasText(toolCallName)) {
                                                    toolName.setLength(0);
                                                    toolName.append(toolCallName);
                                                }
                                            }
                                        }
                                    }
                                }
                                if(chatResponse.getMetadata().getUsage() != null && chatResponse.getMetadata().getUsage().getTotalTokens() > 0) {
                                    llmUsage.set(chatResponse.getMetadata().getUsage());
                                }
                            },
                            streamListener::onError,
                            () -> {
                                // 使用提取的方法获取工具名称
                                String extractedToolName = getToolName(session, message, useFunctionCall, 
                                        chatResponses, fullResponse.toString(), hasToolCalls.get());
                                if (StringUtils.hasText(extractedToolName)) {
                                    toolName.setLength(0);
                                    toolName.append(extractedToolName);
                                }
                                
                                streamListener.onComplete(toolName.toString(), llmUsage.get());
                            });
        } catch (Exception e) {
            String errorMessage = "Извините, я столкнулся с проблемой при обработке вашего запроса.";
            
            // Извлекаем детальное сообщение об ошибке из WebClientResponseException
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientException) {
                try {
                    String responseBody = webClientException.getResponseBodyAsString();
                    if (responseBody != null && !responseBody.isEmpty()) {
                        // Пытаемся извлечь сообщение об ошибке из JSON ответа
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(responseBody);
                            if (jsonNode.has("error")) {
                                com.fasterxml.jackson.databind.JsonNode errorNode = jsonNode.get("error");
                                if (errorNode.has("message")) {
                                    String apiErrorMessage = errorNode.get("message").asText();
                                    logger.error("Ошибка API: {}", apiErrorMessage);
                                    errorMessage = "Ошибка модели: " + apiErrorMessage + ". Пожалуйста, проверьте настройки модели или используйте другую модель.";
                                } else if (errorNode.has("type")) {
                                    String errorType = errorNode.get("type").asText();
                                    logger.error("Тип ошибки API: {}", errorType);
                                    errorMessage = "Ошибка модели: " + errorType + ". Пожалуйста, проверьте настройки модели.";
                                }
                            } else {
                                logger.error("Тело ответа с ошибкой: {}", responseBody);
                            }
                        } catch (Exception parseException) {
                            logger.error("Не удалось разобрать ответ об ошибке: {}", responseBody);
                            errorMessage = "Ошибка API: " + webClientException.getStatusCode() + ". Пожалуйста, проверьте настройки модели.";
                        }
                    } else {
                        logger.error("Ошибка при обработке LLM: {} - {}", webClientException.getStatusCode(), webClientException.getMessage());
                        errorMessage = "Ошибка API: " + webClientException.getStatusCode() + ". Пожалуйста, проверьте настройки модели.";
                    }
                } catch (Exception ex) {
                    logger.error("Ошибка при извлечении деталей ошибки: {}", ex.getMessage(), ex);
                }
            } else {
                logger.error("Ошибка при обработке LLM: {}", e.getMessage(), e);
            }
            
            // Отправляем ошибку пользователю
            sentenceHandler.accept(errorMessage, true, true);
        }
    }


    /**
     * 判断文本是否包含实质性内容（不仅仅是空白字符或标点符号）
     *
     * @param text 要检查的文本
     * @return 是否包含实质性内容
     */
    private boolean containsSubstantialContent(String text) {
        if (text == null || text.trim().length() < MIN_SENTENCE_LENGTH) {
            return false;
        }

        // 移除所有标点符号和空白字符后，检查是否还有内容
        String stripped = text.replaceAll("[\\p{P}\\s]", "");
        return stripped.length() >= 2; // 至少有两个非标点非空白字符
    }

    /**
     * 三参数消费者接口
     */
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    class TokenStreamResponseListener implements StreamResponseListener {

        final StringBuilder currentSentence = new StringBuilder(); // 当前句子的缓冲区
        final StringBuilder contextBuffer = new StringBuilder(); // 上下文缓冲区，用于检测数字中的小数点
        final AtomicInteger sentenceCount = new AtomicInteger(0); // 已发送句子的计数
        final StringBuilder fullResponse = new StringBuilder(); // 完整响应的缓冲区
        final AtomicBoolean finalSentenceSent = new AtomicBoolean(false); // 跟踪最后一个句子是否已发送
        String message;// 用户消息内容
        ChatSession session;
        TriConsumer<String, Boolean, Boolean> sentenceHandler;
        boolean useFunctionCall;

        public TokenStreamResponseListener(ChatSession session, String message,
                TriConsumer<String, Boolean, Boolean> sentenceHandler, boolean useFunctionCall) {
            this.message = message;
            this.session = session;
            this.sentenceHandler = sentenceHandler;
            this.useFunctionCall = useFunctionCall;
        }

        @Override
        public void onToken(String token) {
            if (token == null || token.isEmpty()) {
                return;
            }
            // 将token添加到完整响应
            fullResponse.append(token);

            // 逐字符处理token
            for (int i = 0; i < token.length();) {
                int codePoint = token.codePointAt(i);
                String charStr = new String(Character.toChars(codePoint));

                // 将字符添加到上下文缓冲区（保留最近的字符以检测数字模式）
                contextBuffer.append(charStr);
                if (contextBuffer.length() > 20) { // 保留足够的上下文
                    contextBuffer.delete(0, contextBuffer.length() - 20);
                }

                // 将字符添加到当前句子缓冲区
                currentSentence.append(charStr);

                // 检查各种断句标记
                boolean shouldSendSentence = false;
                boolean isEndMark = SENTENCE_END_PATTERN.matcher(charStr).find();
                boolean isPauseMark = PAUSE_PATTERN.matcher(charStr).find();
                boolean isSpecialMark = SPECIAL_PATTERN.matcher(charStr).find();
                boolean isNewline = NEWLINE_PATTERN.matcher(charStr).find();
                boolean isEmoji = EmojiUtils.isEmoji(codePoint);

                // 检查当前句子是否包含颜文字
                boolean containsKaomoji = false;
                if (currentSentence.length() >= 3) { // 颜文字至少需要3个字符
                    containsKaomoji = EmojiUtils.containsKaomoji(currentSentence.toString());
                }

                // 如果当前字符是句号，检查它是否是数字中的小数点
                if (isEndMark && charStr.equals(".")) {
                    String context = contextBuffer.toString();
                    Matcher numberMatcher = NUMBER_PATTERN.matcher(context);
                    // 如果找到数字模式（如"0.271"），则不视为句子结束标点
                    if (numberMatcher.find() && numberMatcher.end() >= context.length() - 3) {
                        isEndMark = false;
                    }
                }

                // 判断是否应该发送当前句子
                if (isEndMark) {
                    // 句子结束标点是强断句信号
                    shouldSendSentence = true;
                } else if (isNewline) {
                    // 换行符也是强断句信号
                    shouldSendSentence = true;
                } else if ((isPauseMark || isSpecialMark || isEmoji || containsKaomoji)
                        && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                    // 停顿标点、特殊标点、表情符号或颜文字在句子足够长时可以断句
                    shouldSendSentence = true;
                }

                // 如果应该发送句子，且当前句子长度满足要求
                if (shouldSendSentence && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                    String sentence = currentSentence.toString().trim();

                    // 过滤颜文字
                    sentence = EmojiUtils.filterKaomoji(sentence);

                    if (containsSubstantialContent(sentence)) {
                        boolean isFirst = sentenceCount.get() == 0;
                        boolean isLast = false; // 只有在onComplete中才会有最后一个句子

                        sentenceHandler.accept(sentence, isFirst, isLast);
                        sentenceCount.incrementAndGet();

                        // 清空当前句子缓冲区
                        currentSentence.setLength(0);
                    }
                }

                // 移动到下一个码点
                i += Character.charCount(codePoint);
            }
        }

        @Override
        public void onComplete(String toolName, Usage llmUsage) {
            // 检查该会话是否已完成处理
            // 处理当前缓冲区剩余的内容（如果有）
            if (currentSentence.length() > 0 && !finalSentenceSent.get()) {
                String sentence = currentSentence.toString().trim();
                boolean isFirst = sentenceCount.get() == 0;
                boolean isLast = true; // 这是最后一个句子

                // 即使句子很短，也要发送（比如"再见"这样的词）
                sentenceHandler.accept(sentence, isFirst, isLast);
                sentenceCount.incrementAndGet();
                finalSentenceSent.set(true);
            } else if (!finalSentenceSent.get() && sentenceCount.get() > 0) {
                // 如果已经发送过句子但没有发送过最后一个句子标记，发送一个空的最后句子标记
                // 这确保对话能够正常结束
                boolean isFirst = false; // 不是第一句
                sentenceHandler.accept("", isFirst, true);
                finalSentenceSent.set(true);
            }

            persistMessages(toolName, llmUsage);

            // 记录处理的句子数量
            logger.debug("Всего обработано {} предложений", sentenceCount.get());
        }

        /**
         * 保存消息,只保存用户输入与输出。
         * Message在没有持久化前，是不会有messageId的。
         * 是否需要把content为空和角色为tool的入库?
         * 目前不入库（这类主要是function_call的二次调用llm进行总结时的过程消息）
         * 具体的细节逻辑，由Conversation处理，ChatService不再负责消息持久化的职能。
         */
        void persistMessages(String toolName, Usage llmUsage) {
            
            UserMessage userMessage = new UserMessage(message);
            Long userTimeMillis = session.getUserTimeMillis();

            AssistantMessage assistantMessage = new AssistantMessage(fullResponse.toString(), Map.of("toolName", toolName));
            // 将Usage附加于AssistantMessage的metadata。以后可以考虑封装AssistantMessage的子类，减少metadata样板代码。
            ChatMemory.setUsage(assistantMessage, llmUsage);
            // 首次模型响应时间、首次TTS响应时间都是AssistantMessage才具备的metadata，UserMessage没有实际也不应该有。
            // Message里塞的内容已经较多，后续再考虑优化。可能设计一个新的类，融合 SysMessage and Message是值得考虑的。
            assistantMessage.getMetadata().put(ChatSession.ATTR_FIRST_MODEL_RESPONSE_TIME,
                    session.getAttributes().get(ChatSession.ATTR_FIRST_MODEL_RESPONSE_TIME));
            
            // 获取TTS响应时间，如果还没有设置（流式TTS可能还在处理中），等待一下再获取
            Object ttsResponseTime = session.getAttributes().get(ChatSession.ATTR_FIRST_TTS_RESPONSE_TIME);
            if (ttsResponseTime == null) {
                // 如果TTS响应时间还没有设置，等待更长时间再获取
                try {
                    Thread.sleep(1500); // 等待1秒，让流式TTS有机会设置响应时间
                    ttsResponseTime = session.getAttributes().get(ChatSession.ATTR_FIRST_TTS_RESPONSE_TIME);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            assistantMessage.getMetadata().put(ChatSession.ATTR_FIRST_TTS_RESPONSE_TIME, ttsResponseTime);
            
            Long assistantTimeMillis = session.getAssistantTimeMillis();
            session.getConversation().add(assistantMessage, assistantTimeMillis);
        }

        @Override
        public void onError(Throwable e) {
            String errorMessage = "Извините, я столкнулся с проблемой при обработке вашего запроса.";
            
            // Извлекаем детальное сообщение об ошибке из WebClientResponseException
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientException) {
                try {
                    String responseBody = webClientException.getResponseBodyAsString();
                    if (responseBody != null && !responseBody.isEmpty()) {
                        // Пытаемся извлечь сообщение об ошибке из JSON ответа
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(responseBody);
                            if (jsonNode.has("error")) {
                                com.fasterxml.jackson.databind.JsonNode errorNode = jsonNode.get("error");
                                if (errorNode.has("message")) {
                                    String apiErrorMessage = errorNode.get("message").asText();
                                    logger.error("Ошибка API: {}", apiErrorMessage);
                                    errorMessage = "Ошибка модели: " + apiErrorMessage + ". Пожалуйста, проверьте настройки модели или используйте другую модель.";
                                } else if (errorNode.has("type")) {
                                    String errorType = errorNode.get("type").asText();
                                    logger.error("Тип ошибки API: {}", errorType);
                                    errorMessage = "Ошибка модели: " + errorType + ". Пожалуйста, проверьте настройки модели.";
                                }
                            } else {
                                logger.error("Тело ответа с ошибкой: {}", responseBody);
                            }
                        } catch (Exception parseException) {
                            logger.error("Не удалось разобрать ответ об ошибке: {}", responseBody);
                            errorMessage = "Ошибка API: " + webClientException.getStatusCode() + ". Пожалуйста, проверьте настройки модели.";
                        }
                    } else {
                        logger.error("Ошибка потокового ответа: {} - {}", webClientException.getStatusCode(), webClientException.getMessage());
                        errorMessage = "Ошибка API: " + webClientException.getStatusCode() + ". Пожалуйста, проверьте настройки модели.";
                    }
                } catch (Exception ex) {
                    logger.error("Ошибка при извлечении деталей ошибки: {}", ex.getMessage(), ex);
                }
            } else {
                logger.error("Ошибка потокового ответа: {}", e.getMessage(), e);
            }
            
            // Отправляем ошибку пользователю
            sentenceHandler.accept(errorMessage, true, true);
        }
    };
}