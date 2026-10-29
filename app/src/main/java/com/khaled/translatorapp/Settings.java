package com.khaled.translatorapp;

import android.content.Context;
import android.content.SharedPreferences;

/** App-wide toggles. */
final class Settings {

    private static final String KEY_TRANSLATE_AS_YOU_TYPE = "translate_as_you_type";

    private final SharedPreferences prefs;

    Settings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    boolean translateAsYouType() {
        return prefs.getBoolean(KEY_TRANSLATE_AS_YOU_TYPE, true);
    }

    void setTranslateAsYouType(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRANSLATE_AS_YOU_TYPE, enabled).apply();
    }
}
