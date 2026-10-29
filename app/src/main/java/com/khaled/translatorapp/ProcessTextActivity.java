package com.khaled.translatorapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

/**
 * Handles ACTION_PROCESS_TEXT: shown when the user selects text in another
 * app and taps "Translate" in the Cut/Copy/Paste popup. Translates into the
 * saved target language, which can be changed from the chip.
 */
public class ProcessTextActivity extends AppCompatActivity {

    private String text;
    private TextView translatedView;
    private TextView detectedView;
    private ProgressBar progress;
    private Chip targetChip;
    private Button copyButton;
    private Button speakButton;
    private Button shareButton;
    private Button replaceButton;
    private Button retryButton;
    private TranslateViewModel viewModel;
    private LanguagePrefs prefs;
    private Speaker speaker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence selected = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        text = selected == null ? "" : selected.toString().trim();
        if (text.isEmpty()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_process_text);
        prefs = new LanguagePrefs(this);
        speaker = new Speaker(this);

        TextView originalView = findViewById(R.id.originalText);
        translatedView = findViewById(R.id.translatedText);
        detectedView = findViewById(R.id.detectedText);
        progress = findViewById(R.id.progress);
        targetChip = findViewById(R.id.targetChip);
        copyButton = findViewById(R.id.copyButton);
        speakButton = findViewById(R.id.speakButton);
        shareButton = findViewById(R.id.shareButton);
        replaceButton = findViewById(R.id.replaceButton);
        retryButton = findViewById(R.id.retryButton);

        originalView.setText(text);
        updateTargetChip();

        // "Replace" only makes sense when the selection came from an editable field.
        boolean readOnly = getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        replaceButton.setVisibility(readOnly ? View.GONE : View.VISIBLE);

        targetChip.setOnClickListener(v -> LanguagePicker.show(this, false, prefs.target(),
                prefs.recents(), code -> {
                    prefs.setTarget(code);
                    updateTargetChip();
                    translate();
                }));
        findViewById(R.id.closeButton).setOnClickListener(v -> finish());
        retryButton.setOnClickListener(v -> translate());
        copyButton.setOnClickListener(v -> withTranslation(t -> {
            TextActions.copy(this, t);
            finish();
        }));
        shareButton.setOnClickListener(v -> withTranslation(t -> TextActions.share(this, t)));
        speakButton.setOnClickListener(v -> {
            TranslateViewModel.State s = viewModel.getState().getValue();
            if (s != null && s.hasTranslation()) speaker.toggle(s.translation, s.targetLang);
        });
        replaceButton.setOnClickListener(v -> withTranslation(t -> {
            setResult(RESULT_OK, new Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, t));
            finish();
        }));
        findViewById(R.id.openButton).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        viewModel = new ViewModelProvider(this).get(TranslateViewModel.class);
        viewModel.getState().observe(this, this::render);
        // Only on first creation; after rotation the ViewModel already has it.
        if (viewModel.getState().getValue() == null) translate();
    }

    @Override
    protected void onDestroy() {
        if (speaker != null) speaker.shutdown();
        super.onDestroy();
    }

    private void translate() {
        viewModel.translate(text, Languages.AUTO, prefs.target());
    }

    private void updateTargetChip() {
        targetChip.setText(Languages.displayName(this, prefs.target()));
    }

    private interface TextConsumer {
        void accept(String text);
    }

    private void withTranslation(TextConsumer action) {
        TranslateViewModel.State s = viewModel.getState().getValue();
        if (s != null && s.hasTranslation()) action.accept(s.translation);
    }

    private void render(TranslateViewModel.State s) {
        boolean loading = s == null || s.loading;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            translatedView.setText("");
        } else if (s.errorRes != 0) {
            translatedView.setText(s.errorRes);
            TextActions.applyUiDirection(translatedView);
            translatedView.setTextColor(MaterialColors.getColor(translatedView, androidx.appcompat.R.attr.colorError));
        } else {
            translatedView.setText(s.translation);
            TextActions.applyDirection(translatedView, s.targetLang);
            translatedView.setTextColor(MaterialColors.getColor(translatedView,
                    com.google.android.material.R.attr.colorOnSurface));
        }

        boolean showDetected = !loading && !s.detectedLanguage.isEmpty();
        detectedView.setText(showDetected
                ? getString(R.string.detected_language, Languages.displayName(this, s.detectedLanguage))
                : "");

        retryButton.setVisibility(!loading && s.errorRes != 0 ? View.VISIBLE : View.GONE);

        boolean has = s != null && s.hasTranslation();
        copyButton.setEnabled(has);
        speakButton.setEnabled(has);
        shareButton.setEnabled(has);
        replaceButton.setEnabled(has);
    }
}
