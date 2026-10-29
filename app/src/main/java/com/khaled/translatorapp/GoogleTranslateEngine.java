package com.khaled.translatorapp;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Google Translate's public, unofficial, no-API-key endpoint. It can
 * rate-limit or change without notice; that's why it sits behind
 * {@link TranslationEngine}.
 *
 * Text is sent as a POST body: in a GET query string the request fails
 * (HTTP 400) somewhere past ~15k characters.
 *
 * The default "Dalvik/..." User-Agent can get bounced (302) to Google's
 * /sorry bot-check page, so send our own and treat any redirect as
 * rate limiting rather than following it to an unparseable HTML page.
 */
final class GoogleTranslateEngine implements TranslationEngine {

    /** One HTTP round trip for a single chunk; swappable in tests. */
    interface Requester {
        TranslationResult request(String text, String sourceLang, String targetLang)
                throws TranslationException;
    }

    static final int MAX_CHUNK_CHARS = 4500;
    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single";
    private static final int TIMEOUT_MS = 15000;
    private static final long DEFAULT_RETRY_DELAY_MS = 800;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android) TranslatorApp/" + BuildConfig.VERSION_NAME;

    private final Requester requester;
    private final long retryDelayMs;

    GoogleTranslateEngine() {
        this(GoogleTranslateEngine::httpRequest, DEFAULT_RETRY_DELAY_MS);
    }

    GoogleTranslateEngine(Requester requester, long retryDelayMs) {
        this.requester = requester;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public TranslationResult translate(String text, String sourceLang, String targetLang)
            throws TranslationException {
        StringBuilder out = new StringBuilder();
        String detected = "";
        for (String chunk : TextChunker.split(text, MAX_CHUNK_CHARS)) {
            // The endpoint may trim surrounding whitespace, which would glue
            // chunks together; translate the core and keep the original edges.
            int lead = 0;
            while (lead < chunk.length() && Character.isWhitespace(chunk.charAt(lead))) lead++;
            int trail = chunk.length();
            while (trail > lead && Character.isWhitespace(chunk.charAt(trail - 1))) trail--;

            out.append(chunk, 0, lead);
            if (trail > lead) {
                TranslationResult part = translateWithRetry(chunk.substring(lead, trail), sourceLang, targetLang);
                out.append(part.translatedText);
                if (detected.isEmpty()) detected = part.detectedSourceLanguage;
            }
            out.append(chunk, trail, chunk.length());
        }
        return new TranslationResult(out.toString(), detected);
    }

    private TranslationResult translateWithRetry(String text, String sl, String tl)
            throws TranslationException {
        try {
            return requester.request(text, sl, tl);
        } catch (TranslationException e) {
            if (!e.kind.isTransient()) throw e;
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new TranslationException(TranslationException.Kind.CANCELLED, "Cancelled", ie);
            }
            return requester.request(text, sl, tl);
        }
    }

    private static TranslationResult httpRequest(String text, String sl, String tl)
            throws TranslationException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(ENDPOINT + "?client=gtx&dt=t&sl=" + enc(sl) + "&tl=" + enc(tl));
            byte[] body = ("q=" + enc(text)).getBytes(StandardCharsets.UTF_8);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(body.length);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int code = conn.getResponseCode();
            if (code == 429 || (code >= 300 && code < 400)) {
                throw new TranslationException(TranslationException.Kind.RATE_LIMITED, "HTTP " + code);
            }
            if (code >= 500) {
                throw new TranslationException(TranslationException.Kind.SERVER, "HTTP " + code);
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw new TranslationException(TranslationException.Kind.BAD_RESPONSE, "HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                return parse(readAll(in));
            }
        } catch (TranslationException e) {
            throw e;
        } catch (UnknownHostException e) {
            throw new TranslationException(TranslationException.Kind.OFFLINE, "Unknown host", e);
        } catch (SocketTimeoutException e) {
            throw new TranslationException(TranslationException.Kind.TIMEOUT, "Timed out", e);
        } catch (InterruptedIOException e) {
            throw new TranslationException(TranslationException.Kind.CANCELLED, "Cancelled", e);
        } catch (IOException e) {
            throw new TranslationException(TranslationException.Kind.NETWORK, String.valueOf(e.getMessage()), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Parses the translate_a/single response: [[[translated, original, ...], ...], null, "detected", ...]. */
    static TranslationResult parse(String body) throws TranslationException {
        try {
            JSONArray root = new JSONArray(body);
            JSONArray sentences = root.getJSONArray(0);
            StringBuilder translated = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                JSONArray sentence = sentences.optJSONArray(i);
                if (sentence != null && !sentence.isNull(0)) {
                    translated.append(sentence.getString(0));
                }
            }
            String detected = root.length() > 2 && !root.isNull(2) ? root.optString(2, "") : "";
            return new TranslationResult(translated.toString(), detected);
        } catch (Exception e) {
            throw new TranslationException(TranslationException.Kind.BAD_RESPONSE,
                    "Could not parse translation response", e);
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e); // UTF-8 is always supported
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toString("UTF-8");
    }
}
