package com.xiaozhi.dialogue.stt.providers;

import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.HttpUtil;
import com.xiaozhi.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Slf4j
public class GoogleSttService implements SttService {

    private static final String PROVIDER_NAME = "google";
    private static final String DEFAULT_API_URL = "https://speech.googleapis.com/v1/speech:recognize";

    private final String apiKey;
    private final String apiUrl;

    private final OkHttpClient client = HttpUtil.client;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public GoogleSttService(SysConfig config) {
        this.apiKey = config.getApiKey();
        this.apiUrl = (StringUtils.hasText(config.getApiUrl()) ? config.getApiUrl() : DEFAULT_API_URL) + "?key=" + apiKey;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String recognition(byte[] audioData) {
        var params = new GoogleSttParams();
        // Google REST API expects raw PCM if encoding is LINEAR16, but sampleRate must match
        // Our AudioUtils.SAMPLE_RATE is 16000
        params.setConfig(new GoogleSttParams.RecognitionConfig("LINEAR16", AudioUtils.SAMPLE_RATE, "ru-RU"));
        params.setAudio(new GoogleSttParams.RecognitionAudio(Base64.getEncoder().encodeToString(audioData)));

        var request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JsonUtil.toJson(params), JSON))
                .build();

        try (var resp = client.newCall(request).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                String responseBody = resp.body().string();
                GoogleSttResp googleSttResp = JsonUtil.fromJson(responseBody, GoogleSttResp.class);
                if (googleSttResp != null && googleSttResp.getResults() != null && !googleSttResp.getResults().isEmpty()) {
                    return googleSttResp.getResults().get(0).getAlternatives().get(0).getTranscript();
                } else {
                    log.warn("Google STT returned no results. Body: {}", responseBody);
                }
            } else {
                String errorMsg = resp.body() != null ? resp.body().string() : "Empty response";
                log.error("Google STT failed: {} - Status: {}", errorMsg, resp.code());
            }
        } catch (IOException e) {
            log.error("Error sending Google STT request", e);
        }
        return "";
    }

    @Override
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
        throw new UnsupportedOperationException("Google STT streaming not implemented");
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Data
    public static class GoogleSttParams {
        private RecognitionConfig config;
        private RecognitionAudio audio;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class RecognitionConfig {
            private String encoding;
            private int sampleRateHertz;
            private String languageCode;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class RecognitionAudio {
            private String content;
        }
    }

    @Data
    public static class GoogleSttResp {
        private List<SpeechRecognitionResult> results;

        @Data
        public static class SpeechRecognitionResult {
            private List<SpeechRecognitionAlternative> alternatives;
        }

        @Data
        public static class SpeechRecognitionAlternative {
            private String transcript;
            private float confidence;
        }
    }
}
