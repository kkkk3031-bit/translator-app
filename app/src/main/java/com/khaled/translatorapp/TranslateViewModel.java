package com.khaled.translatorapp;

import android.app.Application;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs translations off the main thread and holds the latest result, so it
 * survives rotation and an in-flight request isn't re-sent. Successful
 * translations are recorded in {@link HistoryStore}.
 */
public class TranslateViewModel extends AndroidViewModel {

    static final class State {
        final boolean loading;
        final String sourceText;
        final String sourceLang;
        final String targetLang;
        @Nullable final String translation;
        final String detectedLanguage;
        /** String resource of the error message, or 0 if no error. */
        final int errorRes;

        private State(boolean loading, String sourceText, String sourceLang, String targetLang,
                      @Nullable String translation, String detectedLanguage, int errorRes) {
            this.loading = loading;
            this.sourceText = sourceText;
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.translation = translation;
            this.detectedLanguage = detectedLanguage;
            this.errorRes = errorRes;
        }

        boolean hasTranslation() {
            return !loading && translation != null && !translation.isEmpty();
        }

        /** The real source language: the detected one when auto-detect was chosen. */
        String effectiveSourceLang() {
            return Languages.AUTO.equals(sourceLang) && !detectedLanguage.isEmpty()
                    ? detectedLanguage : sourceLang;
        }
    }

    private static final String TAG = "TranslateViewModel";

    private final TranslationEngine engine = new GoogleTranslateEngine();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>();
    @Nullable private Future<?> inFlight;
    /** Bumped per request; results from superseded requests are dropped. */
    private int generation;

    public TranslateViewModel(@NonNull Application application) {
        super(application);
    }

    LiveData<State> getState() {
        return state;
    }

    void translate(String text, String sourceLang, String targetLang) {
        cancelInFlight();
        final int gen = generation;

        if (!Connectivity.isOnline(getApplication())) {
            state.setValue(new State(false, text, sourceLang, targetLang, null, "",
                    TranslationException.Kind.OFFLINE.messageRes()));
            return;
        }
        state.setValue(new State(true, text, sourceLang, targetLang, null, "", 0));
        inFlight = executor.submit(() -> {
            State result;
            try {
                TranslationResult r = engine.translate(text, sourceLang, targetLang);
                result = new State(false, text, sourceLang, targetLang, r.translatedText,
                        r.detectedSourceLanguage, 0);
                record(result);
            } catch (TranslationException e) {
                if (e.kind == TranslationException.Kind.CANCELLED) return;
                result = new State(false, text, sourceLang, targetLang, null, "", e.kind.messageRes());
            }
            State finalResult = result;
            // Deliver on the main thread so the generation check can't race.
            mainHandler.post(() -> {
                if (gen == generation) state.setValue(finalResult);
            });
        });
    }

    /** Shows a past translation without hitting the network. */
    void show(HistoryEntry e) {
        cancelInFlight();
        state.setValue(new State(false, e.sourceText, e.sourceLang, e.targetLang, e.translatedText,
                e.detectedLang, 0));
    }

    private void cancelInFlight() {
        if (inFlight != null) inFlight.cancel(true);
        generation++;
    }

    private void record(State s) {
        if (s.translation == null || s.translation.isEmpty()) return;
        try {
            HistoryStore.get(getApplication()).add(s.sourceText, s.translation, s.sourceLang,
                    s.targetLang, s.detectedLanguage);
        } catch (SQLException e) {
            // History is a convenience; never fail a translation over it.
            Log.w(TAG, "Could not save history", e);
        }
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
