package com.shadow.eyes;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Robust On-Device Voice Engine for Shadow.
 * Directly listens for user commands and questions with zero wake-word barrier.
 * Also monitors for acoustic barge-in commands ("listen", "stop", "quiet").
 */
public class WakeWordEngine {
    private static final String TAG = "WakeWordEngine";

    public interface WakeWordListener {
        void onSpeechRecognized(String speechText);
        void onInterruptionRequested();
        void onListeningStateChanged(boolean isListening);
        void onWakeWordRmsChanged(float rmsDb);
    }

    private final Context context;
    private final Handler mainHandler;
    private final WakeWordListener listener;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile boolean isListening = false;
    private volatile boolean isInterruptionOnlyMode = false; // When true (during speech), only barge-in triggers like "listen" are processed
    private volatile long lastTtsEndTime = 0;
    private ClearInterruptionModeTask pendingClearInterruptionTask = null;

    private long lastWakeTriggerTime = 0;

    // Acoustic Barge-in Interruption Pattern:
    private static final Pattern INTERRUPT_PATTERN = Pattern.compile(
        "\\b(listen|listens|listening|listened|hey\\s+listen|shadow\\s+listen|listen\\s+shadow|stop|stops|stopping|stop\\s+it|stop\\s+talking|quiet|be\\s+quiet|quiet\\s+down|wait|wait\\s+a\\s+sec(?:ond)?|wait\\s+up|wait\\s+a\\s+min(?:ute)?|hold\\s+on|hold\\s+up|shh|shush|hush|shut\\s+up|pause|silence|enough|halt|cancel)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public WakeWordEngine(Context context, WakeWordListener listener) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.listener = listener;

        initRecognizerIntent();
    }

    private void initRecognizerIntent() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        isPaused = false;
        Log.i(TAG, "Starting on-device Voice Engine (direct speech listening without wake-word)...");
        mainHandler.post(new StartListeningTask(this));
    }

    public synchronized void stop() {
        isRunning = false;
        isPaused = false;
        isListening = false;
        Log.i(TAG, "Stopping on-device Voice Engine...");
        mainHandler.post(new StopListeningTask(this));
    }

    public synchronized void pause() {
        if (!isRunning || isPaused) return;
        isPaused = true;
        Log.d(TAG, "Pausing Voice Engine");
        mainHandler.post(new CancelListeningTask(this));
    }

    public synchronized void resume() {
        if (!isRunning || !isPaused) return;
        isPaused = false;
        Log.d(TAG, "Resuming Voice Engine");
        mainHandler.postDelayed(new StartListeningTask(this), 200);
    }

    public void setFollowUpMode(boolean active) {
        // No-op compatibility stub
    }

    public boolean isFollowUpMode() {
        return true;
    }

    public synchronized void setInterruptionOnlyMode(boolean active) {
        if (pendingClearInterruptionTask != null) {
            mainHandler.removeCallbacks(pendingClearInterruptionTask);
            pendingClearInterruptionTask = null;
        }
        this.isInterruptionOnlyMode = active;
        Log.d(TAG, "Interruption-only mode set to: " + active);
        if (active) {
            if (isRunning && isPaused) {
                isPaused = false;
                mainHandler.postDelayed(new StartListeningTask(this), 150);
            }
        }
    }

    public synchronized void notifyTtsFinished() {
        this.lastTtsEndTime = System.currentTimeMillis();
        this.isInterruptionOnlyMode = true; // Retain interruption mode during acoustic decay window
        if (pendingClearInterruptionTask != null) {
            mainHandler.removeCallbacks(pendingClearInterruptionTask);
        }
        pendingClearInterruptionTask = new ClearInterruptionModeTask(this);
        mainHandler.postDelayed(pendingClearInterruptionTask, 800);
        Log.d(TAG, "TTS finished reported. Interruption shield active for 800ms acoustic decay.");
    }

    public boolean isInterruptionOnlyMode() {
        return isInterruptionOnlyMode;
    }

    public boolean isListening() {
        return isListening && isRunning && !isPaused;
    }

    public static boolean checkInterrupt(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        return INTERRUPT_PATTERN.matcher(input.trim()).find();
    }

    public void playWakeChime() {
        // No-op compatibility stub (wake chime eliminated with wake words)
    }

    void startListeningInternal() {
        if (!isRunning || isPaused) return;

        try {
            if (speechRecognizer == null) {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                    speechRecognizer.setRecognitionListener(new ShadowRecognitionListener(this));
                } else {
                    Log.e(TAG, "SpeechRecognizer is NOT available on this device!");
                    return;
                }
            } else {
                try {
                    speechRecognizer.cancel();
                } catch (Exception ignored) {}
            }

            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Voice input active - listening for user commands/questions");
        } catch (Exception e) {
            Log.e(TAG, "startListening error", e);
            scheduleResetAndRestart(500);
        }
    }

    void stopListeningInternal() {
        isListening = false;
        if (listener != null) {
            listener.onListeningStateChanged(false);
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    void cancelListeningInternal() {
        isListening = false;
        if (listener != null) {
            listener.onListeningStateChanged(false);
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {}
        }
    }

    void scheduleRestart(long delayMs) {
        if (!isRunning || isPaused) return;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(new StartListeningTask(this), delayMs);
    }

    void scheduleResetAndRestart(long delayMs) {
        if (!isRunning || isPaused) return;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.post(new StopListeningTask(this));
        mainHandler.postDelayed(new StartListeningTask(this), delayMs);
    }

    void handleSpeechRecognizedInternal(String speechText) {
        if (speechText == null || speechText.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastWakeTriggerTime < 500) {
            return;
        }
        lastWakeTriggerTime = now;
        Log.i(TAG, "Speech recognized: '" + speechText + "'");
        if (listener != null) {
            listener.onSpeechRecognized(speechText.trim());
        }
    }

    void handleInterruptionInternal() {
        long now = System.currentTimeMillis();
        if (now - lastWakeTriggerTime < 400) {
            return;
        }
        lastWakeTriggerTime = now;
        isInterruptionOnlyMode = false;
        Log.i(TAG, "ACOUSTIC BARGE-IN INTERRUPTION TRIGGERED! User commanded 'listen' / 'quiet'");
        if (listener != null) {
            listener.onInterruptionRequested();
        }
        scheduleRestart(800);
    }

    // =========================================================================
    // STATIC NAMED TASKS & LISTENER (STRICT D8 COMPATIBILITY)
    // =========================================================================

    static class ClearInterruptionModeTask implements Runnable {
        private final WeakReference<WakeWordEngine> engineRef;
        public ClearInterruptionModeTask(WakeWordEngine engine) {
            this.engineRef = new WeakReference<>(engine);
        }
        @Override
        public void run() {
            WakeWordEngine engine = engineRef.get();
            if (engine != null) {
                engine.isInterruptionOnlyMode = false;
                Log.d(TAG, "Interruption-only mode auto-cleared after acoustic decay cooldown");
            }
        }
    }

    static class StartListeningTask implements Runnable {
        private final WeakReference<WakeWordEngine> engineRef;
        public StartListeningTask(WakeWordEngine engine) {
            this.engineRef = new WeakReference<>(engine);
        }
        @Override
        public void run() {
            WakeWordEngine engine = engineRef.get();
            if (engine != null) {
                engine.startListeningInternal();
            }
        }
    }

    static class StopListeningTask implements Runnable {
        private final WeakReference<WakeWordEngine> engineRef;
        public StopListeningTask(WakeWordEngine engine) {
            this.engineRef = new WeakReference<>(engine);
        }
        @Override
        public void run() {
            WakeWordEngine engine = engineRef.get();
            if (engine != null) {
                engine.stopListeningInternal();
            }
        }
    }

    static class CancelListeningTask implements Runnable {
        private final WeakReference<WakeWordEngine> engineRef;
        public CancelListeningTask(WakeWordEngine engine) {
            this.engineRef = new WeakReference<>(engine);
        }
        @Override
        public void run() {
            WakeWordEngine engine = engineRef.get();
            if (engine != null) {
                engine.cancelListeningInternal();
            }
        }
    }

    static class ShadowRecognitionListener implements RecognitionListener {
        private final WeakReference<WakeWordEngine> engineRef;

        public ShadowRecognitionListener(WakeWordEngine engine) {
            this.engineRef = new WeakReference<>(engine);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            WakeWordEngine engine = engineRef.get();
            if (engine != null) {
                engine.isListening = true;
                if (engine.listener != null) {
                    engine.listener.onListeningStateChanged(true);
                }
            }
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "User speech detected...");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            WakeWordEngine engine = engineRef.get();
            if (engine != null && engine.listener != null) {
                engine.listener.onWakeWordRmsChanged(rmsdB);
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            WakeWordEngine engine = engineRef.get();
            if (engine != null) {
                engine.isListening = false;
                if (engine.listener != null) {
                    engine.listener.onListeningStateChanged(false);
                }
            }
        }

        @Override
        public void onError(int error) {
            WakeWordEngine engine = engineRef.get();
            if (engine == null) return;

            engine.isListening = false;
            if (engine.listener != null) {
                engine.listener.onListeningStateChanged(false);
            }

            // Normal silence timeouts (6) and no-match (7) smoothly restart
            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                engine.scheduleRestart(250);
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT || error == 13) {
                engine.scheduleResetAndRestart(600);
            } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Log.e(TAG, "SpeechRecognizer missing RECORD_AUDIO permission!");
            } else {
                Log.d(TAG, "SpeechRecognizer status code: " + error);
                engine.scheduleRestart(350);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            WakeWordEngine engine = engineRef.get();
            if (engine == null || !engine.isRunning || engine.isPaused) return;

            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null) {
                for (String text : matches) {
                    if (checkInterrupt(text)) {
                        engine.handleInterruptionInternal();
                        return;
                    }
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            WakeWordEngine engine = engineRef.get();
            if (engine == null || !engine.isRunning || engine.isPaused) return;

            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                for (String text : matches) {
                    if (checkInterrupt(text)) {
                        engine.handleInterruptionInternal();
                        return;
                    }
                }

                long timeSinceTts = System.currentTimeMillis() - engine.lastTtsEndTime;
                if (engine.isInterruptionOnlyMode || timeSinceTts < 800) {
                    Log.d(TAG, "Acoustic echo shield: Discarded recognition within " + timeSinceTts + "ms of TTS finish or during speech (" + matches.get(0) + ")");
                } else {
                    String best = matches.get(0).trim();
                    if (best.length() > 0) {
                        engine.handleSpeechRecognizedInternal(best);
                    }
                }
            }

            engine.scheduleRestart(150);
        }

        @Override
        public void onEvent(int eventType, Bundle params) {}
    }
}
