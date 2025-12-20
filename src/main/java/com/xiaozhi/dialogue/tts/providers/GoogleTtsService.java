package com.xiaozhi.dialogue.tts.providers;

import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

@Slf4j
public class GoogleTtsService implements TtsService {

    private static final String PROVIDER_NAME = "google";
    private static final String DEFAULT_API_URL = "https://texttospeech.googleapis.com/v1/text:synthesize";

    private final String apiKey;
    private final String apiUrl;
    private final String voiceName;
    private final String outputPath;
    private final Float pitch;
    private final Float speed;

    private final OkHttpClient client = HttpUtil.client;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public GoogleTtsService(SysConfig config, String voiceName, Float pitch, Float speed, String outputPath) {
        this.apiKey = config.getApiKey();
        this.apiUrl = (StringUtils.hasText(config.getApiUrl()) ? config.getApiUrl() : DEFAULT_API_URL) + "?key=" + apiKey;
        this.voiceName = StringUtils.hasText(voiceName) ? voiceName : "ru-RU-Wavenet-A";
        this.pitch = pitch != null ? pitch : 1.0f;
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
        var params = new GoogleTtsParams();
        params.setInput(new GoogleTtsParams.Input(text));
        
        // languageCode is usually first two parts of voiceName (e.g., en-US)
        String languageCode = "ru-RU";
        if (voiceName.contains("-")) {
            String[] parts = voiceName.split("-");
            if (parts.length >= 2) {
                languageCode = parts[0] + "-" + parts[1];
            }
        }
        
        params.setVoice(new GoogleTtsParams.VoiceSelection(languageCode, voiceName));
        
        // Google pitch range is [-20.0, 20.0], default 0.0. 
        // Our pitch is around 1.0. Let's map 1.0 to 0.0.
        // Simple mapping: (pitch - 1.0) * 10
        float googlePitch = (pitch - 1.0f) * 10f;
        googlePitch = Math.max(-20.0f, Math.min(20.0f, googlePitch));
        
        // Google speakingRate range is [0.25, 4.0], default 1.0. 
        // Our speed is around 1.0.
        float googleSpeed = speed;
        googleSpeed = Math.max(0.25f, Math.min(4.0f, googleSpeed));
        
        params.setAudioConfig(new GoogleTtsParams.AudioConfig("MP3", googleSpeed, googlePitch));

        var request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JsonUtil.toJson(params), JSON))
                .build();

        try (var resp = client.newCall(request).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                String responseBody = resp.body().string();
                GoogleTtsResp googleTtsResp = JsonUtil.fromJson(responseBody, GoogleTtsResp.class);
                if (googleTtsResp != null && googleTtsResp.getAudioContent() != null) {
                    byte[] audioBytes = Base64.getDecoder().decode(googleTtsResp.getAudioContent());
                    Files.write(Paths.get(filepath), audioBytes);
                } else {
                    log.error("Google TTS response error: {}", responseBody);
                    throw new RuntimeException("Google TTS response error");
                }
            } else {
                String errorMsg = resp.body() != null ? resp.body().string() : "Empty response";
                log.error("Google TTS failed: {}", errorMsg);
                throw new RuntimeException("Google TTS failed: " + errorMsg);
            }
        } catch (IOException e) {
            log.error("Error sending Google TTS request", e);
            throw new RuntimeException("Google TTS request failed", e);
        }
    }

    @Data
    public static class GoogleTtsParams {
        private Input input;
        private VoiceSelection voice;
        private AudioConfig audioConfig;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Input {
            private String text;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class VoiceSelection {
            private String languageCode;
            private String name;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class AudioConfig {
            private String audioEncoding;
            private float speakingRate;
            private float pitch;
        }
    }

    @Data
    public static class GoogleTtsResp {
        private String audioContent;
    }
}
