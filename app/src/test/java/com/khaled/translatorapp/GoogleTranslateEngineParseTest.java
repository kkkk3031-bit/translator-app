package com.khaled.translatorapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class GoogleTranslateEngineParseTest {

    private static String fixture(String name) throws IOException {
        try (InputStream in = GoogleTranslateEngineParseTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void parsesRecordedResponseAndKeepsLineBreaks() throws Exception {
        TranslationResult r = GoogleTranslateEngine.parse(fixture("multiline_en_ar.json"));
        assertEquals("en", r.detectedSourceLanguage);
        String[] lines = r.translatedText.split("\n", -1);
        assertEquals(4, lines.length); // "a\n\nb\nc"
        assertEquals("", lines[1]);
        assertTrue(r.translatedText.matches("(?s).*[\\u0600-\\u06FF].*"));
    }

    @Test
    public void skipsNullSegments() throws Exception {
        TranslationResult r = GoogleTranslateEngine.parse("[[[\"مرحبا\",\"Hello\"],[null,null,\"Marhaba\"]],null,\"en\"]");
        assertEquals("مرحبا", r.translatedText);
        assertEquals("en", r.detectedSourceLanguage);
    }

    @Test
    public void missingDetectedLanguageIsEmpty() throws Exception {
        TranslationResult r = GoogleTranslateEngine.parse("[[[\"مرحبا\",\"Hello\"]]]");
        assertEquals("", r.detectedSourceLanguage);
    }

    @Test
    public void garbageIsBadResponse() {
        try {
            GoogleTranslateEngine.parse("<html>Too many requests</html>");
            fail("expected exception");
        } catch (TranslationException e) {
            assertEquals(TranslationException.Kind.BAD_RESPONSE, e.kind);
        }
    }
}
