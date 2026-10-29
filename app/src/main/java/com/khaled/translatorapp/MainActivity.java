package com.khaled.translatorapp;

import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

public class MainActivity extends AppCompatActivity {

    // The endpoint is unofficial and throttles bursts, so as-you-type is deliberately gentle.
    private static final long TYPE_DEBOUNCE_MS = 1000;
    private static final long MIN_AUTO_INTERVAL_MS = 1500;
    private static final long RATE_LIMIT_PAUSE_MS = 60_000;

    private EditText inputText;
    private TextView outputText;
    private TextView detectedText;
    private ProgressBar progress;
    private Button sourceButton;
    private Button targetButton;
    private Button copyButton;
    private Button shareButton;
    private Button speakButton;
    private Button clearButton;
    private Chip pasteChip;
    private TextView charCount;

    private TranslateViewModel viewModel;
    private LanguagePrefs prefs;
    private Speaker speaker;
    private Settings settings;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final Runnable typingTranslate = this::translateAsYouType;
    private long lastAutoTranslateAt;
    private long autoPausedUntil;

    private final ActivityResultLauncher<Intent> historyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    showHistoryEntry(HistoryActivity.entryFrom(result.getData()));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new LanguagePrefs(this);
        speaker = new Speaker(this);
        settings = new Settings(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.getMenu().findItem(R.id.action_type_translate).setChecked(settings.translateAsYouType());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_history) {
                viewModel.commitToHistory();
                historyLauncher.launch(new Intent(this, HistoryActivity.class));
                return true;
            }
            if (item.getItemId() == R.id.action_type_translate) {
                boolean enabled = !item.isChecked();
                item.setChecked(enabled);
                settings.setTranslateAsYouType(enabled);
                return true;
            }
            return false;
        });

        inputText = findViewById(R.id.inputText);
        outputText = findViewById(R.id.outputText);
        detectedText = findViewById(R.id.detectedText);
        progress = findViewById(R.id.progress);
        sourceButton = findViewById(R.id.sourceButton);
        targetButton = findViewById(R.id.targetButton);
        copyButton = findViewById(R.id.copyButton);
        shareButton = findViewById(R.id.shareButton);
        speakButton = findViewById(R.id.speakButton);
        clearButton = findViewById(R.id.clearButton);
        pasteChip = findViewById(R.id.pasteChip);
        charCount = findViewById(R.id.charCount);

        sourceButton.setOnClickListener(v -> LanguagePicker.show(this, true, prefs.source(),
                prefs.recents(), code -> {
                    prefs.setSource(code);
                    onLanguagesChanged();
                }));
        targetButton.setOnClickListener(v -> LanguagePicker.show(this, false, prefs.target(),
                prefs.recents(), code -> {
                    prefs.setTarget(code);
                    onLanguagesChanged();
                }));
        findViewById(R.id.swapButton).setOnClickListener(v -> swapLanguages());
        findViewById(R.id.translateButton).setOnClickListener(v -> translateInput());

        copyButton.setOnClickListener(v -> {
            TranslateViewModel.State s = viewModel.getState().getValue();
            if (s == null || !s.hasTranslation()) return;
            viewModel.commitToHistory();
            TextActions.copy(this, s.translation);
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
        shareButton.setOnClickListener(v -> {
            TranslateViewModel.State s = viewModel.getState().getValue();
            if (s == null || !s.hasTranslation()) return;
            viewModel.commitToHistory();
            TextActions.share(this, s.translation);
        });
        speakButton.setOnClickListener(v -> {
            TranslateViewModel.State s = viewModel.getState().getValue();
            if (s == null || !s.hasTranslation()) return;
            viewModel.commitToHistory();
            speaker.toggle(s.translation, s.targetLang);
        });
        clearButton.setOnClickListener(v -> inputText.setText(""));
        pasteChip.setOnClickListener(v -> pasteFromClipboard());

        inputText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                charCount.setText(getResources().getQuantityString(R.plurals.char_count, s.length(), s.length()));
                updatePasteChip();
                typingHandler.removeCallbacks(typingTranslate);
                if (s.toString().trim().isEmpty()) {
                    viewModel.clear();
                } else if (settings.translateAsYouType()) {
                    typingHandler.postDelayed(typingTranslate, TYPE_DEBOUNCE_MS);
                }
            }
        });

        viewModel = new ViewModelProvider(this).get(TranslateViewModel.class);
        viewModel.getState().observe(this, this::render);

        updateLanguageButtons();
        render(viewModel.getState().getValue());
        if (savedInstanceState == null) handleIncomingText(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingText(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The popup shares these settings and may have changed them.
        updateLanguageButtons();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Clipboard contents are only visible to the focused app on Android 10+.
        if (hasFocus) updatePasteChip();
    }

    @Override
    protected void onPause() {
        // Leaving the screen counts as accepting an as-you-type result.
        viewModel.commitToHistory();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        typingHandler.removeCallbacks(typingTranslate);
        speaker.shutdown();
        super.onDestroy();
    }

    /** Text shared to us (ACTION_SEND) or handed over from the popup's "Open in Translator". */
    private void handleIncomingText(Intent intent) {
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null || text.toString().trim().isEmpty()) return;
        inputText.setText(text);
        translateInput();
    }

    private void translateInput() {
        typingHandler.removeCallbacks(typingTranslate);
        String text = inputText.getText().toString().trim();
        if (!text.isEmpty()) viewModel.translate(text, prefs.source(), prefs.target());
    }

    private void translateAsYouType() {
        String text = inputText.getText().toString().trim();
        if (text.isEmpty()) return;
        TranslateViewModel.State s = viewModel.getState().getValue();
        // Skip if what's on screen already matches (e.g. text set by swap or history).
        if (s != null && s.errorRes == 0 && text.equals(s.sourceText)
                && prefs.source().equals(s.sourceLang) && prefs.target().equals(s.targetLang)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < autoPausedUntil) return; // recently rate-limited; the button still works
        long wait = lastAutoTranslateAt + MIN_AUTO_INTERVAL_MS - now;
        if (wait > 0) {
            typingHandler.postDelayed(typingTranslate, wait);
            return;
        }
        lastAutoTranslateAt = now;
        viewModel.translate(text, prefs.source(), prefs.target(), false);
    }

    private void onLanguagesChanged() {
        updateLanguageButtons();
        TranslateViewModel.State s = viewModel.getState().getValue();
        // Re-translate what's on screen so it matches the new languages.
        if (s != null && !inputText.getText().toString().trim().isEmpty()) translateInput();
    }

    private void swapLanguages() {
        String source = prefs.source();
        TranslateViewModel.State s = viewModel.getState().getValue();
        if (Languages.AUTO.equals(source)) {
            source = s != null && s.hasTranslation() ? s.effectiveSourceLang() : Languages.AUTO;
        }
        if (Languages.AUTO.equals(source)) {
            Toast.makeText(this, R.string.swap_needs_language, Toast.LENGTH_LONG).show();
            return;
        }
        String target = prefs.target();
        prefs.setSource(target);
        prefs.setTarget(source);
        updateLanguageButtons();
        if (s != null && s.hasTranslation()) {
            inputText.setText(s.translation);
            inputText.setSelection(inputText.length());
        }
        translateInput();
    }

    private void showHistoryEntry(HistoryEntry e) {
        prefs.setSource(e.sourceLang);
        prefs.setTarget(e.targetLang);
        updateLanguageButtons();
        inputText.setText(e.sourceText);
        viewModel.show(e);
    }

    private void updateLanguageButtons() {
        sourceButton.setText(Languages.displayName(this, prefs.source()));
        targetButton.setText(Languages.displayName(this, prefs.target()));
        TextActions.applyDirection(inputText, prefs.source());
    }

    private void updatePasteChip() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        // Only peek at the description: reading the clip itself triggers the
        // "pasted from clipboard" notice on Android 12+.
        ClipDescription d = cm.getPrimaryClipDescription();
        boolean hasText = d != null && (d.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                || d.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML));
        pasteChip.setVisibility(hasText && inputText.length() == 0 ? View.VISIBLE : View.GONE);
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (!cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return;
        CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) return;
        inputText.setText(text);
        inputText.setSelection(inputText.length());
        translateInput();
    }

    private void render(TranslateViewModel.State s) {
        boolean loading = s != null && s.loading;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);

        if (s == null || loading) {
            outputText.setText("");
        } else if (s.errorRes != 0) {
            if (s.errorRes == TranslationException.Kind.RATE_LIMITED.messageRes()) {
                autoPausedUntil = SystemClock.elapsedRealtime() + RATE_LIMIT_PAUSE_MS;
            }
            outputText.setText(s.errorRes);
            TextActions.applyUiDirection(outputText);
            outputText.setTextColor(MaterialColors.getColor(outputText, androidx.appcompat.R.attr.colorError));
        } else {
            outputText.setText(s.translation);
            TextActions.applyDirection(outputText, s.targetLang);
            outputText.setTextColor(MaterialColors.getColor(outputText,
                    com.google.android.material.R.attr.colorOnSecondaryContainer));
            outputText.scrollTo(0, 0);
        }

        boolean showDetected = s != null && !s.loading && Languages.AUTO.equals(s.sourceLang)
                && !s.detectedLanguage.isEmpty();
        // INVISIBLE rather than GONE so the output doesn't jump when it appears.
        detectedText.setVisibility(showDetected ? View.VISIBLE : View.INVISIBLE);
        if (showDetected) {
            detectedText.setText(getString(R.string.detected_language,
                    Languages.displayName(this, s.detectedLanguage)));
        }

        boolean has = s != null && s.hasTranslation();
        copyButton.setEnabled(has);
        shareButton.setEnabled(has);
        speakButton.setEnabled(has);
    }
}
