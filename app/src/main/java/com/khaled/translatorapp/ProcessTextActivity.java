package com.khaled.translatorapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

/**
 * Handles ACTION_PROCESS_TEXT: shown when the user selects text in another
 * app and taps "Translate" in the Cut/Copy/Paste popup. Always translates
 * the selection to Arabic, no language picker.
 */
public class ProcessTextActivity extends AppCompatActivity {

    private TextView translatedView;
    private ProgressBar progress;
    private Button copyButton;
    private TranslateViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence selected = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String text = selected == null ? "" : selected.toString().trim();
        if (text.isEmpty()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_process_text);
        TextView originalView = findViewById(R.id.originalText);
        translatedView = findViewById(R.id.translatedText);
        progress = findViewById(R.id.progress);
        copyButton = findViewById(R.id.copyButton);
        Button closeButton = findViewById(R.id.closeButton);

        originalView.setText(text);
        closeButton.setOnClickListener(v -> finish());
        copyButton.setOnClickListener(v -> {
            TranslateViewModel.State s = viewModel.getState().getValue();
            if (s == null || s.translation == null || s.translation.isEmpty()) return;
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("translation", s.translation));
            finish();
        });

        viewModel = new ViewModelProvider(this).get(TranslateViewModel.class);
        viewModel.getState().observe(this, this::render);
        // Only on first creation; after rotation the ViewModel already has it.
        if (viewModel.getState().getValue() == null) {
            viewModel.translate(text, "auto", MainActivity.TARGET_LANG);
        }
    }

    private void render(TranslateViewModel.State s) {
        boolean loading = s == null || s.loading;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            translatedView.setText("");
        } else if (s.errorRes != 0) {
            translatedView.setText(s.errorRes);
        } else {
            translatedView.setText(s.translation);
        }
        copyButton.setEnabled(!loading && s.translation != null && !s.translation.isEmpty());
    }
}
