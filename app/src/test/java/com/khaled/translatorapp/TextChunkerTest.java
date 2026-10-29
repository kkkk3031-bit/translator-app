package com.khaled.translatorapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class TextChunkerTest {

    private static void assertValid(String text, int max, List<String> chunks) {
        assertEquals(text, String.join("", chunks));
        for (String c : chunks) {
            assertTrue("chunk too long: " + c.length(), c.length() <= max);
            assertTrue("empty chunk", !c.isEmpty() || text.isEmpty());
        }
    }

    @Test
    public void shortTextIsOneChunk() {
        List<String> chunks = TextChunker.split("Hello world.", 100);
        assertEquals(1, chunks.size());
        assertEquals("Hello world.", chunks.get(0));
    }

    @Test
    public void emptyTextIsOneEmptyChunk() {
        assertEquals(List.of(""), TextChunker.split("", 10));
    }

    @Test
    public void prefersParagraphBreaks() {
        String text = "First paragraph here.\nSecond one.";
        List<String> chunks = TextChunker.split(text, 25);
        assertEquals("First paragraph here.\n", chunks.get(0));
        assertValid(text, 25, chunks);
    }

    @Test
    public void fallsBackToSentenceEnd() {
        String text = "One sentence. Two sentence. Three sentence.";
        List<String> chunks = TextChunker.split(text, 30);
        assertEquals("One sentence. Two sentence.", chunks.get(0));
        assertValid(text, 30, chunks);
    }

    @Test
    public void fallsBackToWhitespace() {
        String text = "alpha beta gamma delta epsilon zeta";
        List<String> chunks = TextChunker.split(text, 12);
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertTrue(chunks.get(i).endsWith(" "));
        }
        assertValid(text, 12, chunks);
    }

    @Test
    public void hardCutWhenNoBreaks() {
        String text = "x".repeat(25);
        List<String> chunks = TextChunker.split(text, 10);
        assertEquals(3, chunks.size());
        assertValid(text, 10, chunks);
    }

    @Test
    public void neverSplitsSurrogatePairs() {
        String text = "😀".repeat(10); // 10 emoji = 20 chars, no breaks
        List<String> chunks = TextChunker.split(text, 7);
        for (String c : chunks) {
            assertTrue(!Character.isHighSurrogate(c.charAt(c.length() - 1)));
            assertTrue(!Character.isLowSurrogate(c.charAt(0)));
        }
        assertValid(text, 7, chunks);
    }

    @Test
    public void largeRealisticText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("Sentence number ").append(i).append(" goes here. ");
            if (i % 7 == 0) sb.append("\n\n");
        }
        String text = sb.toString();
        List<String> chunks = TextChunker.split(text, GoogleTranslateEngine.MAX_CHUNK_CHARS);
        assertTrue(chunks.size() > 1);
        assertValid(text, GoogleTranslateEngine.MAX_CHUNK_CHARS, chunks);
    }
}
