package com.khaled.translatorapp;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class TranslatorApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Wallpaper-based Material You colors on Android 12+; no-op elsewhere.
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
