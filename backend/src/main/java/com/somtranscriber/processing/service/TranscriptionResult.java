package com.somtranscriber.processing.service;

public record TranscriptionResult(
        String detectedLanguage,
        String englishText,
        String providerModel,
        long latencyMs
) {
}
