package com.khaled.translatorapp;

/**
 * A translation backend. Implementations block, so call them off the main
 * thread. Language codes are ISO 639-1 ("en", "ar"); "auto" means detect.
 */
interface TranslationEngine {

    TranslationResult translate(String text, String sourceLang, String targetLang)
            throws TranslationException;
}
