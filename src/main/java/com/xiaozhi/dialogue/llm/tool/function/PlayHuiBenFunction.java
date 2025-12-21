package com.xiaozhi.dialogue.llm.tool.function;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.service.HuiBenService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlayHuiBenFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(PlayHuiBenFunction.class);

    @Resource
    private HuiBenService huiBenService;

    ToolCallback toolCallback = FunctionToolCallback
            .builder("func_playHuiBen", (Map<String, String> params, ToolContext toolContext) -> {
                ChatSession chatSession = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                Integer num = MapUtil.getInt(params, "num");
                try {
                    if (num == null || num < 5 || num > 1100) {
                        num = RandomUtil.randomInt(5, 1100);
                    }
                    huiBenService.playHuiBen(chatSession, num);
                    return "Попытка воспроизвести книгу «" + num + "»";

                } catch (Exception e) {
                    logger.error("Исключение при воспроизведении книги, номер книги: {}", num, e);
                    return "Не удалось воспроизвести книгу";
                }
            })
            .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
            .description("绘本播放助手，需要用户提供绘本数字编号")
            .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "num": {
                                    "type": "integer",
                                    "description": "要播放的绘本数字编号"
                                }
                            },
                            "required": ["num"]
                        }
                    """)
            .inputType(Map.class)
            .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
            .build();

    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        return toolCallback;
    }
}