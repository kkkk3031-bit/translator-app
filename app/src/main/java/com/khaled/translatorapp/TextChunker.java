package com.khaled.translatorapp;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits long text into pieces no longer than a limit, preferring to cut at
 * paragraph breaks, then sentence ends, then whitespace. Concatenating the
 * pieces always reproduces the original text exactly.
 */
final class TextChunker {

    private TextChunker() {}

    static List<String> split(String text, int maxLength) {
        if (maxLength < 2) throw new IllegalArgumentException("maxLength must be >= 2");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (text.length() - start > maxLength) {
            int end = findCut(text, start, start + maxLength);
            chunks.add(text.substring(start, end));
            start = end;
        }
        if (start < text.length() || chunks.isEmpty()) {
            chunks.add(text.substring(start));
        }
        return chunks;
    }

    /** Returns an index in (start, limit] to cut before. */
    private static int findCut(String text, int start, int limit) {
        // Don't accept cuts in the first half of the window, so chunks stay large.
        int floor = start + (limit - start) / 2;

        int cut = text.lastIndexOf('\n', limit - 1);
        if (cut >= floor) return cut + 1;

        for (int i = limit - 1; i >= floor; i--) {
            if (isSentenceEnd(text.charAt(i)) && i + 1 < text.length()
                    && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        for (int i = limit - 1; i >= floor; i--) {
            if (Character.isWhitespace(text.charAt(i))) return i + 1;
        }
        // No good break: hard cut, but never between a surrogate pair.
        if (Character.isHighSurrogate(text.charAt(limit - 1))) return limit - 1;
        return limit;
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '؟' /* Arabic ? */
                || c == '。' /* 。 */ || c == '۔' /* Urdu full stop */;
    }
}
