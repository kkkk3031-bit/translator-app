package com.khaled.translatorapp;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

/**
 * Thin wrapper over TextToSpeech: initializes lazily on first use, speaks
 * long text in chunks, and tells the user when no voice is available.
 * Call {@link #shutdown()} from onDestroy.
 */
final class Speaker implements TextToSpeech.OnInitListener {

    private final Context context;
    private TextToSpeech tts;
    private boolean ready;
    private Runnable pending;

    Speaker(Context context) {
        this.context = context;
    }

    /** Speaks {@code text}, or stops if already speaking. */
    void toggle(String text, String lang) {
        Runnable action = () -> speakNow(text, lang);
        if (tts == null) {
            pending = action;
            tts = new TextToSpeech(context.getApplicationContext(), this);
        } else if (!ready) {
            pending = action;
        } else {
            action.run();
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(context, R.string.tts_unavailable, Toast.LENGTH_LONG).show();
            tts = null;
            pending = null;
            return;
        }
        ready = true;
        if (pending != null) {
            Runnable r = pending;
            pending = null;
            r.run();
        }
    }

    private void speakNow(String text, String lang) {
        if (tts.isSpeaking()) {
            tts.stop();
            return;
        }
        int res = tts.setLanguage(Locale.forLanguageTag(lang));
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(context,
                    context.getString(R.string.tts_no_voice, Languages.displayName(context, lang)),
                    Toast.LENGTH_LONG).show();
            return;
        }
        int max = Math.max(2, TextToSpeech.getMaxSpeechInputLength() - 1);
        int i = 0;
        for (String part : TextChunker.split(text, max)) {
            tts.speak(part, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "t" + i++);
        }
    }

    void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
        pending = null;
    }
}
