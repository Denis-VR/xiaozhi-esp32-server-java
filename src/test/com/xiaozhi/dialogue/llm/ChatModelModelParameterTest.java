package com.xiaozhi.dialogue.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест для проверки, что модель передается в HTTP-запрос к OpenAI API
 * 
 * Запуск: mvn test -Dtest=ChatModelModelParameterTest
 * или через IDE: Run -> ChatModelModelParameterTest
 */
public class ChatModelModelParameterTest {

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void testModelParameterIsSentInRequest() throws Exception {
        // Настройка мок-сервера для перехвата запроса
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\n" +
                        "  \"id\": \"chatcmpl-123\",\n" +
                        "  \"object\": \"chat.completion\",\n" +
                        "  \"created\": 1677652288,\n" +
                        "  \"choices\": [{\n" +
                        "    \"index\": 0,\n" +
                        "    \"message\": {\n" +
                        "      \"role\": \"assistant\",\n" +
                        "      \"content\": \"Hello! How can I help you?\"\n" +
                        "    },\n" +
                        "    \"finish_reason\": \"stop\"\n" +
                        "  }],\n" +
                        "  \"usage\": {\n" +
                        "    \"prompt_tokens\": 9,\n" +
                        "    \"completion_tokens\": 12,\n" +
                        "    \"total_tokens\": 21\n" +
                        "  }\n" +
                        "}")
                .setHeader("Content-Type", "application/json"));

        // Получаем URL мок-сервера
        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");

        // Создаем OpenAiChatModel с defaultOptions, содержащими модель
        String testModel = "gpt-5-nano";
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(testModel)
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(2000)
                .build();

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(new NoopApiKey())
                .baseUrl(baseUrl)
                .completionsPath("/v1/chat/completions")
                .webClientBuilder(WebClient.builder()
                        .clientConnector(new JdkClientHttpConnector(
                                HttpClient.newBuilder()
                                        .version(HttpClient.Version.HTTP_1_1)
                                        .connectTimeout(Duration.ofSeconds(30))
                                        .build())))
                .build();

        // Создаем ChatModel с defaultOptions
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .build();

        // Создаем Prompt с ChatOptions, содержащими модель (как в реальном коде)
        ChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(testModel)
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(2000)
                .build();

        Prompt prompt = new Prompt(List.of(new UserMessage("Hello, how are you?")), chatOptions);

        // Вызываем chatModel.call()
        ChatResponse response = chatModel.call(prompt);

        // Проверяем, что запрос был отправлен
        assertNotNull(response, "Response should not be null");

        // Ждем немного, чтобы запрос был обработан
        Thread.sleep(100);

        // Проверяем, что модель была передана в запросе
        // Получаем запрос из мок-сервера
        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest, "Request should be recorded");

        // Парсим тело запроса
        String requestBody = recordedRequest.getBody().readUtf8();
        assertNotNull(requestBody, "Request body should not be null");
        assertFalse(requestBody.isEmpty(), "Request body should not be empty");

        // Проверяем, что модель присутствует в теле запроса
        JsonNode requestJson = objectMapper.readTree(requestBody);
        assertTrue(requestJson.has("model"), "Request should contain 'model' field");
        String modelInRequest = requestJson.get("model").asText();
        assertEquals(testModel, modelInRequest, 
                "Model in request should match the model in ChatOptions: " + requestBody);

        System.out.println("✓ Тест пройден! Модель '" + testModel + "' успешно передана в HTTP-запрос.");
        System.out.println("Тело запроса: " + requestBody);
    }

    @Test
    void testModelParameterFromDefaultOptions() throws Exception {
        // Настройка мок-сервера
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\n" +
                        "  \"id\": \"chatcmpl-123\",\n" +
                        "  \"object\": \"chat.completion\",\n" +
                        "  \"created\": 1677652288,\n" +
                        "  \"choices\": [{\n" +
                        "    \"index\": 0,\n" +
                        "    \"message\": {\n" +
                        "      \"role\": \"assistant\",\n" +
                        "      \"content\": \"Hello!\"\n" +
                        "    },\n" +
                        "    \"finish_reason\": \"stop\"\n" +
                        "  }],\n" +
                        "  \"usage\": {\n" +
                        "    \"prompt_tokens\": 9,\n" +
                        "    \"completion_tokens\": 12,\n" +
                        "    \"total_tokens\": 21\n" +
                        "  }\n" +
                        "}")
                .setHeader("Content-Type", "application/json"));

        String baseUrl = mockWebServer.url("/").toString().replaceAll("/$", "");

        // Создаем OpenAiChatModel с defaultOptions, содержащими модель
        String testModel = "gpt-5-nano";
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(testModel)
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(2000)
                .build();

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(new NoopApiKey())
                .baseUrl(baseUrl)
                .completionsPath("/v1/chat/completions")
                .webClientBuilder(WebClient.builder()
                        .clientConnector(new org.springframework.http.client.reactive.JdkClientHttpConnector(
                                HttpClient.newBuilder()
                                        .version(HttpClient.Version.HTTP_1_1)
                                        .connectTimeout(Duration.ofSeconds(30))
                                        .build())))
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .build();

        // Создаем Prompt БЕЗ ChatOptions - должна использоваться модель из defaultOptions
        Prompt prompt = new Prompt(List.of(new UserMessage("Hello")));

        // Вызываем chatModel.call()
        ChatResponse response = chatModel.call(prompt);

        // Проверяем запрос
        assertNotNull(response);
        Thread.sleep(100);

        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);

        String requestBody = recordedRequest.getBody().readUtf8();
        JsonNode requestJson = objectMapper.readTree(requestBody);
        
        assertTrue(requestJson.has("model"), "Request should contain 'model' field from defaultOptions");
        String modelInRequest = requestJson.get("model").asText();
        assertEquals(testModel, modelInRequest, 
                "Model in request should match the model in defaultOptions: " + requestBody);

        System.out.println("✓ Тест пройден! Модель '" + testModel + "' из defaultOptions успешно передана в HTTP-запрос.");
        System.out.println("Тело запроса: " + requestBody);
    }
}

