package com.somtranscriber.processing.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.service.TranscriptionAdapter;
import com.somtranscriber.processing.service.TranscriptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

@Component
public class OpenAiTranscriptionAdapter implements TranscriptionAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptionAdapter.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OpenAiTranscriptionAdapter(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties appProperties) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public TranscriptionResult transcribe(Path filePath, String mimeType) {
        Instant start = Instant.now();

        if (appProperties.openai().apiKey() == null || appProperties.openai().apiKey().isBlank()) {
            return fallbackResult(start, "OpenAI API key missing");
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", appProperties.openai().model());
            // Some OpenAI transcription models reject verbose_json; json works across model variants.
            body.add("response_format", "json");
            body.add("file", new FileSystemResource(filePath));

            String rawResponse = restClient.post()
                    .uri(appProperties.openai().baseUrl() + "/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", "Bearer " + appProperties.openai().apiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String text;
            String language = "unknown";
            String trimmed = rawResponse == null ? "" : rawResponse.trim();
            if (trimmed.startsWith("{")) {
                JsonNode root = objectMapper.readTree(trimmed);
                text = root.path("text").asText();
                language = root.path("language").asText("unknown");
            } else {
                text = trimmed;
            }
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("OpenAI transcription returned empty text");
            }

            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            return new TranscriptionResult(language, text, appProperties.openai().model(), latencyMs);
        } catch (Exception exception) {
            log.warn("OpenAI transcription failed, using fallback transcript", exception);
            return fallbackResult(start, "OpenAI transcription request failed");
        }
    }

    private TranscriptionResult fallbackResult(Instant start, String reason) {
        String text = reason + ", using transcript fallback for local development.";
        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        return new TranscriptionResult("unknown", text, "mock-openai", latencyMs);
    }
}
