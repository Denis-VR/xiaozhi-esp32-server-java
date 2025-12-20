package com.xiaozhi.dialogue.tts.providers;

import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.HttpUtil;
import com.xiaozhi.utils.JsonUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
public class OpenAiTtsService implements TtsService {

    private static final String PROVIDER_NAME = "openai";
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/audio/speech";

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String voice;
    private final String outputPath;
    private final Float speed;

    private final OkHttpClient client = HttpUtil.client;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public OpenAiTtsService(SysConfig config, String voice, Float speed, String outputPath) {
        this.apiKey = config.getApiKey();
        this.apiUrl = StringUtils.hasText(config.getApiUrl()) ? config.getApiUrl() : DEFAULT_API_URL;
        this.model = StringUtils.hasText(config.getAppId()) ? config.getAppId() : "tts-1"; // Using appId as model name if provided
        this.voice = StringUtils.hasText(voice) ? voice : "alloy";
        this.speed = speed != null ? speed : 1.0f;
        this.outputPath = outputPath;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String audioFormat() {
        return "mp3";
    }

    @Override
    public String textToSpeech(String text) throws Exception {
        var output = Paths.get(outputPath, getAudioFileName()).toString();
        sendRequest(text, output);
        return output;
    }

    private void sendRequest(String text, String filepath) {
        var params = new OpenAiTtsParams(model, text, voice, speed);
        
        var request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer %s".formatted(apiKey))
                .post(RequestBody.create(JsonUtil.toJson(params), JSON))
                .build();

        try (var resp = client.newCall(request).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                Files.write(Paths.get(filepath), resp.body().bytes());
            } else {
                String errorMsg = resp.body() != null ? resp.body().string() : "Empty response";
                log.error("OpenAI TTS failed: {}", errorMsg);
                throw new RuntimeException("OpenAI TTS failed: " + errorMsg);
            }
        } catch (IOException e) {
            log.error("Error sending OpenAI TTS request", e);
            throw new RuntimeException("OpenAI TTS request failed", e);
        }
    }

    @Data
    public static class OpenAiTtsParams {
        private String model;
        private String input;
        private String voice;
        private String response_format = "mp3";
        private float speed;

        public OpenAiTtsParams(String model, String input, String voice, float speed) {
            this.model = model;
            this.input = input;
            this.voice = voice;
            this.speed = speed;
        }
    }
}
