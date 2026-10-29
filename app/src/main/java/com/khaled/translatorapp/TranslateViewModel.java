package com.khaled.translatorapp;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

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
 * survives rotation and an in-flight request isn't re-sent.
 */
public class TranslateViewModel extends AndroidViewModel {

    static final class State {
        final boolean loading;
        @Nullable final String translation;
        final String detectedLanguage;
        /** String resource of the error message, or 0 if no error. */
        final int errorRes;

        private State(boolean loading, @Nullable String translation, String detectedLanguage, int errorRes) {
            this.loading = loading;
            this.translation = translation;
            this.detectedLanguage = detectedLanguage;
            this.errorRes = errorRes;
        }

        static State loading() { return new State(true, null, "", 0); }
        static State success(TranslationResult r) { return new State(false, r.translatedText, r.detectedSourceLanguage, 0); }
        static State error(int res) { return new State(false, null, "", res); }
    }

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
        if (inFlight != null) inFlight.cancel(true);
        final int gen = ++generation;

        if (!Connectivity.isOnline(getApplication())) {
            state.setValue(State.error(TranslationException.Kind.OFFLINE.messageRes()));
            return;
        }
        state.setValue(State.loading());
        inFlight = executor.submit(() -> {
            State result;
            try {
                result = State.success(engine.translate(text, sourceLang, targetLang));
            } catch (TranslationException e) {
                if (e.kind == TranslationException.Kind.CANCELLED) return;
                result = State.error(e.kind.messageRes());
            }
            State finalResult = result;
            // Deliver on the main thread so the generation check can't race.
            mainHandler.post(() -> {
                if (gen == generation) state.setValue(finalResult);
            });
        });
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
