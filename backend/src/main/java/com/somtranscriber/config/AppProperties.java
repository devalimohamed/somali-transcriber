package com.somtranscriber.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Audio audio,
        OpenAi openai,
        Ollama ollama,
        Retry retry,
        Cors cors
) {

    public record Jwt(
            String issuer,
            long accessTtlMinutes,
            long refreshTtlDays,
            String secret
    ) {}

    public record Audio(
            String storageDir,
            int maxDurationSeconds
    ) {}

    public record OpenAi(
            String apiKey,
            String model,
            String translationModel,
            String baseUrl
    ) {}

    public record Ollama(
            String baseUrl,
            String model
    ) {}

    public record Retry(
            String queueKey,
            int maxAttempts,
            boolean asyncOnUpload
    ) {}

    public record Cors(
            List<String> allowedOriginPatterns
    ) {}
}
