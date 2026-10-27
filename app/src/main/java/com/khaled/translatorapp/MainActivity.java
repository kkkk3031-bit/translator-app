package com.khaled.translatorapp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    static final String TARGET_LANG = "ar";

    private EditText inputText;
    private TextView outputText;
    private ProgressBar progress;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputText = findViewById(R.id.inputText);
        outputText = findViewById(R.id.outputText);
        progress = findViewById(R.id.progress);
        Button translateButton = findViewById(R.id.translateButton);
        Button copyButton = findViewById(R.id.copyButton);

        translateButton.setOnClickListener(v -> runTranslate());

        copyButton.setOnClickListener(v -> {
            CharSequence text = outputText.getText();
            if (text.length() == 0) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("translation", text));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        // Stop the single worker thread so a finished Activity doesn't leave a
        // non-daemon thread lingering (and, with an in-flight request, holding
        // a reference to this Activity).
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runTranslate() {
        String text = inputText.getText().toString().trim();
        if (text.isEmpty()) return;

        progress.setVisibility(View.VISIBLE);
        outputText.setText("");

        executor.execute(() -> {
            try {
                Translator.Result result = Translator.translate(text, "auto", TARGET_LANG);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    outputText.setText(result.translatedText);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Translation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
