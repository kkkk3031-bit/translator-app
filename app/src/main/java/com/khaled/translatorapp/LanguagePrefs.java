package com.khaled.translatorapp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Persists the chosen source/target languages and recently used ones. */
final class LanguagePrefs {

    static final int MAX_RECENT = 5;
    private static final String KEY_SOURCE = "source";
    private static final String KEY_TARGET = "target";
    private static final String KEY_RECENT = "recent";

    private final SharedPreferences prefs;

    LanguagePrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("languages", Context.MODE_PRIVATE);
    }

    String source() {
        return prefs.getString(KEY_SOURCE, Languages.AUTO);
    }

    String target() {
        return prefs.getString(KEY_TARGET, Languages.DEFAULT_TARGET);
    }

    void setSource(String code) {
        prefs.edit().putString(KEY_SOURCE, code).apply();
        addRecent(code);
    }

    void setTarget(String code) {
        prefs.edit().putString(KEY_TARGET, code).apply();
        addRecent(code);
    }

    List<String> recents() {
        String raw = prefs.getString(KEY_RECENT, "");
        return raw.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private void addRecent(String code) {
        if (Languages.AUTO.equals(code)) return;
        prefs.edit().putString(KEY_RECENT, String.join(",", pushRecent(recents(), code, MAX_RECENT))).apply();
    }

    /** Moves {@code code} to the front of {@code list}, keeping at most {@code max} entries. */
    static List<String> pushRecent(List<String> list, String code, int max) {
        List<String> out = new ArrayList<>();
        out.add(code);
        for (String c : list) {
            if (out.size() >= max) break;
            if (!c.equals(code)) out.add(c);
        }
        return out;
    }
}
