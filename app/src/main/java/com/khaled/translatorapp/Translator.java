package com.khaled.translatorapp;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Talks to Google Translate's public (unofficial, no-API-key) endpoint. */
class Translator {

    static class Result {
        final String translatedText;
        final String detectedSourceLanguage;

        Result(String translatedText, String detectedSourceLanguage) {
            this.translatedText = translatedText;
            this.detectedSourceLanguage = detectedSourceLanguage;
        }
    }

    static Result translate(String text, String sourceLang, String targetLang) throws IOException {
        String encoded = URLEncoder.encode(text, "UTF-8");
        String urlStr = "https://translate.googleapis.com/translate_a/single"
                + "?client=gtx&sl=" + sourceLang + "&tl=" + targetLang + "&dt=t&q=" + encoded;

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        try {
            int code = conn.getResponseCode();
            InputStream stream = code == HttpURLConnection.HTTP_OK ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                throw new IOException("Empty response (HTTP " + code + ")");
            }
            String body = readAll(stream);
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            return parse(body);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private static Result parse(String body) throws IOException {
        try {
            JSONArray root = new JSONArray(body);
            JSONArray sentences = root.getJSONArray(0);
            StringBuilder translated = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                JSONArray sentence = sentences.getJSONArray(i);
                if (!sentence.isNull(0)) {
                    translated.append(sentence.getString(0));
                }
            }
            String detected = root.isNull(2) ? "" : root.optString(2, "");
            return new Result(translated.toString(), detected);
        } catch (Exception e) {
            throw new IOException("Could not parse translation response", e);
        }
    }
}
