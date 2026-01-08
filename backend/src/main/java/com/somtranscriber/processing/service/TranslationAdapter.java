package com.somtranscriber.processing.service;

public interface TranslationAdapter {
    String translateToEnglish(String sourceText, String detectedLanguage);
}
