package com.somtranscriber.processing.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.service.FormatterAdapter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class OllamaFormatterAdapter implements FormatterAdapter {

    private static final String SYSTEM_PROMPT = """
            You are editing a translated call note.
            Keep the original meaning and content exactly as provided.
            Only do light structural cleanup:
            - fix sentence order only when needed for clarity
            - fix grammar, punctuation, and capitalization
            - split or join sentences for readability
            Do not add, remove, summarize, expand, or infer details.
            Do not change names, dates, numbers, places, or actions.
            Do not use report format, headings, bullet points, labels, or templates.
            Return plain text only.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OllamaFormatterAdapter(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties appProperties) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public String format(String rawEnglishTranscript) {
        if (rawEnglishTranscript == null || rawEnglishTranscript.isBlank()) {
            throw new IllegalArgumentException("Transcript cannot be empty");
        }

        if (appProperties.ollama().baseUrl() == null || appProperties.ollama().baseUrl().isBlank()) {
            return rawEnglishTranscript;
        }

        Map<String, Object> payload = Map.of(
                "model", appProperties.ollama().model(),
                "stream", false,
                "prompt", SYSTEM_PROMPT + "\n\nTranscript:\n" + rawEnglishTranscript,
                "options", Map.of("temperature", 0.0)
        );

        try {
            String json = restClient.post()
                    .uri(appProperties.ollama().baseUrl() + "/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            String response = root.path("response").asText();
            if (response == null || response.isBlank()) {
                throw new IllegalStateException("Ollama returned empty response");
            }
            return response.trim();
        } catch (Exception exception) {
            throw new IllegalStateException("Formatter request failed", exception);
        }
    }
}
