package com.khaled.translatorapp;

final class HistoryEntry {
    final long id;
    final String sourceText;
    final String translatedText;
    /** As chosen by the user; may be {@link Languages#AUTO}. */
    final String sourceLang;
    final String targetLang;
    final String detectedLang;
    final boolean starred;
    final long createdAt;

    HistoryEntry(long id, String sourceText, String translatedText, String sourceLang,
                 String targetLang, String detectedLang, boolean starred, long createdAt) {
        this.id = id;
        this.sourceText = sourceText;
        this.translatedText = translatedText;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.detectedLang = detectedLang;
        this.starred = starred;
        this.createdAt = createdAt;
    }

    HistoryEntry withStarred(boolean starred) {
        return new HistoryEntry(id, sourceText, translatedText, sourceLang, targetLang, detectedLang,
                starred, createdAt);
    }

    /** The real source language: the detected one when the user chose auto-detect. */
    String effectiveSourceLang() {
        return Languages.AUTO.equals(sourceLang) && !detectedLang.isEmpty() ? detectedLang : sourceLang;
    }
}
