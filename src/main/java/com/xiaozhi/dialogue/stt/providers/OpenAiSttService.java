package com.xiaozhi.dialogue.stt.providers;

import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.HttpUtil;
import com.xiaozhi.utils.JsonUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.io.IOException;

@Slf4j
public class OpenAiSttService implements SttService {

    private static final String PROVIDER_NAME = "openai";
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/audio/transcriptions";

    private final String apiKey;
    private final String apiUrl;
    private final String model;

    private final OkHttpClient client = HttpUtil.client;

    public OpenAiSttService(SysConfig config) {
        this.apiKey = config.getApiKey();
        this.apiUrl = StringUtils.hasText(config.getApiUrl()) ? config.getApiUrl() : DEFAULT_API_URL;
        this.model = StringUtils.hasText(config.getAppId()) ? config.getAppId() : "whisper-1";
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String recognition(byte[] audioData) {
        // Convert PCM to WAV for OpenAI
        byte[] wavData = AudioUtils.pcmToWavBytes(audioData);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav",
                        RequestBody.create(wavData, MediaType.parse("audio/wav")))
                .addFormDataPart("model", model)
                .addFormDataPart("language", "ru")
                .build();

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer %s".formatted(apiKey))
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                OpenAiSttResp resp = JsonUtil.fromJson(responseBody, OpenAiSttResp.class);
                return resp != null ? resp.getText() : "";
            } else {
                String errorMsg = response.body() != null ? response.body().string() : "Empty response";
                log.error("OpenAI STT failed: {} - Status: {}", errorMsg, response.code());
            }
        } catch (IOException e) {
            log.error("Error sending OpenAI STT request", e);
        }
        return "";
    }

    @Override
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
        throw new UnsupportedOperationException("OpenAI STT does not support streaming via REST API directly in this way");
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Data
    public static class OpenAiSttResp {
        private String text;
    }
}
