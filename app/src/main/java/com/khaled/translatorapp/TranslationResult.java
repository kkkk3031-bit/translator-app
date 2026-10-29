package com.khaled.translatorapp;

final class TranslationResult {
    final String translatedText;
    /** Language code the engine detected, or "" if unknown. */
    final String detectedSourceLanguage;

    TranslationResult(String translatedText, String detectedSourceLanguage) {
        this.translatedText = translatedText;
        this.detectedSourceLanguage = detectedSourceLanguage;
    }
}
