package com.khaled.translatorapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chunking, whitespace handling and retry policy, with a fake requester (no network). */
public class GoogleTranslateEngineTest {

    /** "Translates" by upper-casing and records each request. */
    private static final class FakeRequester implements GoogleTranslateEngine.Requester {
        final List<String> requests = new ArrayList<>();

        @Override
        public TranslationResult request(String text, String sl, String tl) {
            requests.add(text);
            return new TranslationResult(text.toUpperCase(Locale.ROOT), "en");
        }
    }

    @Test
    public void shortTextIsOneRequest() throws Exception {
        FakeRequester fake = new FakeRequester();
        TranslationResult r = new GoogleTranslateEngine(fake, 0).translate("hello world", "auto", "ar");
        assertEquals("HELLO WORLD", r.translatedText);
        assertEquals("en", r.detectedSourceLanguage);
        assertEquals(1, fake.requests.size());
    }

    @Test
    public void longTextIsChunkedAndRejoinedWithOriginalWhitespace() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) sb.append("paragraph number ").append(i).append(" is here.\n\n");
        String text = sb.toString();
        FakeRequester fake = new FakeRequester();

        TranslationResult r = new GoogleTranslateEngine(fake, 0).translate(text, "auto", "ar");

        assertTrue("expected several requests", fake.requests.size() > 1);
        for (String req : fake.requests) {
            assertTrue(req.length() <= GoogleTranslateEngine.MAX_CHUNK_CHARS);
            // Edges are trimmed before sending and restored after.
            assertEquals(req.trim(), req);
        }
        assertEquals(text.toUpperCase(Locale.ROOT), r.translatedText);
    }

    @Test
    public void whitespaceOnlyChunkIsNotSent() throws Exception {
        FakeRequester fake = new FakeRequester();
        TranslationResult r = new GoogleTranslateEngine(fake, 0).translate("   ", "auto", "ar");
        assertEquals("   ", r.translatedText);
        assertEquals(0, fake.requests.size());
    }

    @Test
    public void transientFailureIsRetriedOnce() throws Exception {
        int[] calls = {0};
        GoogleTranslateEngine engine = new GoogleTranslateEngine((t, sl, tl) -> {
            if (calls[0]++ == 0) throw new TranslationException(TranslationException.Kind.TIMEOUT, "slow");
            return new TranslationResult("ok", "en");
        }, 0);
        assertEquals("ok", engine.translate("hi", "auto", "ar").translatedText);
        assertEquals(2, calls[0]);
    }

    @Test
    public void secondTransientFailureIsReported() {
        int[] calls = {0};
        GoogleTranslateEngine engine = new GoogleTranslateEngine((t, sl, tl) -> {
            calls[0]++;
            throw new TranslationException(TranslationException.Kind.SERVER, "500");
        }, 0);
        try {
            engine.translate("hi", "auto", "ar");
            fail("expected exception");
        } catch (TranslationException e) {
            assertEquals(TranslationException.Kind.SERVER, e.kind);
            assertEquals(2, calls[0]);
        }
    }

    @Test
    public void rateLimitIsNotRetried() {
        int[] calls = {0};
        GoogleTranslateEngine engine = new GoogleTranslateEngine((t, sl, tl) -> {
            calls[0]++;
            throw new TranslationException(TranslationException.Kind.RATE_LIMITED, "429");
        }, 0);
        try {
            engine.translate("hi", "auto", "ar");
            fail("expected exception");
        } catch (TranslationException e) {
            assertEquals(TranslationException.Kind.RATE_LIMITED, e.kind);
            assertEquals(1, calls[0]);
        }
    }
}
