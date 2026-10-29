package com.khaled.translatorapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;

public class MainActivity extends AppCompatActivity {

    static final String TARGET_LANG = "ar";

    private EditText inputText;
    private TextView outputText;
    private ProgressBar progress;
    private Button copyButton;
    private TranslateViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputText = findViewById(R.id.inputText);
        outputText = findViewById(R.id.outputText);
        progress = findViewById(R.id.progress);
        copyButton = findViewById(R.id.copyButton);
        Button translateButton = findViewById(R.id.translateButton);

        viewModel = new ViewModelProvider(this).get(TranslateViewModel.class);
        viewModel.getState().observe(this, this::render);

        translateButton.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();
            if (!text.isEmpty()) viewModel.translate(text, "auto", TARGET_LANG);
        });

        copyButton.setOnClickListener(v -> {
            TranslateViewModel.State s = viewModel.getState().getValue();
            if (s == null || s.translation == null || s.translation.isEmpty()) return;
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("translation", s.translation));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
        render(viewModel.getState().getValue());
    }

    private void render(TranslateViewModel.State s) {
        boolean loading = s != null && s.loading;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        if (s == null || loading) {
            outputText.setText("");
        } else if (s.errorRes != 0) {
            outputText.setText(s.errorRes);
            outputText.setTextColor(MaterialColors.getColor(outputText, androidx.appcompat.R.attr.colorError));
        } else {
            outputText.setText(s.translation);
            outputText.setTextColor(MaterialColors.getColor(outputText, com.google.android.material.R.attr.colorOnSurface));
        }
        copyButton.setEnabled(s != null && s.translation != null && !s.translation.isEmpty());
    }
}
