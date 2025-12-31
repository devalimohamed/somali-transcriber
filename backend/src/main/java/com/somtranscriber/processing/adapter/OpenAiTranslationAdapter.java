package com.somtranscriber.processing.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.service.TranslationAdapter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OpenAiTranslationAdapter implements TranslationAdapter {

    private static final String SYSTEM_PROMPT = """
            Translate spoken-call transcript text to English faithfully.
            Rules:
            - Preserve facts, uncertainty, dates, numbers, names, and actions exactly.
            - Do not summarize, add details, remove details, or infer anything.
            - Keep roughly the same amount of detail and meaning.
            - If the input is already English, return the content unchanged.
            - Output plain English text only.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OpenAiTranslationAdapter(RestClient.Builder builder, ObjectMapper objectMapper, AppProperties appProperties) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public String translateToEnglish(String sourceText, String detectedLanguage) {
        if (sourceText == null || sourceText.isBlank()) {
            throw new IllegalArgumentException("Source text cannot be empty");
        }

        if (isEnglishLanguage(detectedLanguage)) {
            return sourceText.trim();
        }

        if (appProperties.openai().apiKey() == null || appProperties.openai().apiKey().isBlank()) {
            return sourceText.trim();
        }

        String translationModel = appProperties.openai().translationModel();
        if (translationModel == null || translationModel.isBlank()) {
            translationModel = "gpt-4o-mini";
        }

        Map<String, Object> payload = Map.of(
                "model", translationModel,
                "temperature", 0.0,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserPrompt(sourceText, detectedLanguage))
                )
        );

        try {
            String rawResponse = restClient.post()
                    .uri(appProperties.openai().baseUrl() + "/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + appProperties.openai().apiKey())
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            String translated = extractTranslatedText(rawResponse);
            if (translated == null || translated.isBlank()) {
                throw new IllegalStateException("OpenAI translation returned empty text");
            }
            return translated.trim();
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI translation request failed", exception);
        }
    }

    private String buildUserPrompt(String sourceText, String detectedLanguage) {
        String language = detectedLanguage == null || detectedLanguage.isBlank()
                ? "unknown"
                : detectedLanguage;

        return """
                Detected language: %s

                Transcript:
                %s
                """.formatted(language, sourceText);
    }

    private String extractTranslatedText(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "";
        }

        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode messageContent = root.path("choices").path(0).path("message").path("content");

        if (messageContent.isTextual()) {
            return messageContent.asText();
        }

        if (messageContent.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : messageContent) {
                if (part.isTextual()) {
                    text.append(part.asText());
                    continue;
                }
                String partText = part.path("text").asText("");
                if (!partText.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append(' ');
                    }
                    text.append(partText);
                }
            }
            return text.toString();
        }

        return "";
    }

    private boolean isEnglishLanguage(String detectedLanguage) {
        if (detectedLanguage == null || detectedLanguage.isBlank()) {
            return false;
        }

        String normalized = detectedLanguage.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("en")
                || normalized.equals("eng")
                || normalized.startsWith("en-")
                || normalized.equals("english");
    }
}
