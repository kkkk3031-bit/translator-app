package com.khaled.translatorapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles ACTION_PROCESS_TEXT: shown when the user selects text in another
 * app and taps "Translate" in the Cut/Copy/Paste popup. Always translates
 * the selection to Arabic, no language picker.
 */
public class ProcessTextActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String translatedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process_text);

        TextView originalView = findViewById(R.id.originalText);
        TextView translatedView = findViewById(R.id.translatedText);
        ProgressBar progress = findViewById(R.id.progress);
        Button copyButton = findViewById(R.id.copyButton);
        Button closeButton = findViewById(R.id.closeButton);

        CharSequence selected = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String text = selected == null ? "" : selected.toString().trim();

        if (text.isEmpty()) {
            finish();
            return;
        }

        originalView.setText(text);
        closeButton.setOnClickListener(v -> finish());
        copyButton.setOnClickListener(v -> {
            if (translatedText.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("translation", translatedText));
            finish();
        });

        executor.execute(() -> {
            try {
                Translator.Result result = Translator.translate(text, "auto", MainActivity.TARGET_LANG);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    translatedText = result.translatedText;
                    translatedView.setText(translatedText);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    translatedView.setText("Translation failed: " + e.getMessage());
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        // Stop the worker thread so a dismissed dialog Activity doesn't leave a
        // non-daemon thread lingering with a reference to this Activity.
        executor.shutdownNow();
        super.onDestroy();
    }
}
