package com.shadow.eyes;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-Device Local Wake-Word Engine for Shadow.
 * Continuously monitors audio from complete silence on-device.
 * Detects "Hey Shadow", "Shadow", "OK Shadow", "Hi Shadow", "Yo Shadow".
 */
public class WakeWordEngine {
    private static final String TAG = "WakeWordEngine";

    public interface WakeWordListener {
        void onWakeWord(String detectedPhrase, String trailingCommand);
        void onFollowUpSpeech(String speechText);
        void onListeningStateChanged(boolean isListening);
        void onWakeWordRmsChanged(float rmsDb);
    }

    public static class WakeMatchResult {
        public final boolean matched;
        public final String phrase;
        public final String trailingCommand;

        public WakeMatchResult(boolean matched, String phrase, String trailingCommand) {
            this.matched = matched;
            this.phrase = phrase;
            this.trailingCommand = trailingCommand;
        }
    }

    private final Context context;
    private final Handler mainHandler;
    private final WakeWordListener listener;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile boolean isListening = false;
    private volatile boolean isTriggered = false;
    private volatile boolean isFollowUpMode = true; // Alert conversational state active by default

    private long lastWakeTriggerTime = 0;
    private static final long WAKE_COOLDOWN_MS = 1500;

    // Multi-trigger pattern: conversational greetings, wake commands, and name triggers
    // Matches "hey hello", "hello", "hi", "hey", "wake up", "are you there", "good morning", "hey shadow", etc.
    private static final Pattern WAKE_WORD_PATTERN = Pattern.compile(
        "^\\s*(?:" +
        "(?:hey|ok|okay|hi|hello|yo)?\\s*(?:shadow|shadows|shado)\\b" +
        "|hey\\s+hello\\b" +
        "|hello(?:\\s+there)?\\b" +
        "|hi(?:\\s+there)?\\b" +
        "|hey(?:\\s+there)?\\b" +
        "|wake\\s+up\\b" +
        "|are\\s+you\\s+(?:there|awake)\\b" +
        "|you\\s+there\\b" +
        "|good\\s+(?:morning|afternoon|evening|day)\\b" +
        "|what(?:'s|s)\\s+up\\b" +
        ")",
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
        Log.i(TAG, "Starting on-device Wake-Word Engine...");
        mainHandler.post(new StartListeningTask(this));
    }

    public synchronized void stop() {
        isRunning = false;
        isPaused = false;
        isListening = false;
        Log.i(TAG, "Stopping on-device Wake-Word Engine...");
        mainHandler.post(new StopListeningTask(this));
    }

    public synchronized void pause() {
        if (!isRunning || isPaused) return;
        isPaused = true;
        Log.d(TAG, "Pausing Wake-Word Engine (Acoustic Echo Shield)");
        mainHandler.post(new CancelListeningTask(this));
    }

    public synchronized void resume() {
        if (!isRunning || !isPaused) return;
        isPaused = false;
        Log.d(TAG, "Resuming Wake-Word Engine after speech cooldown");
        mainHandler.postDelayed(new StartListeningTask(this), 250);
    }

    public void setFollowUpMode(boolean active) {
        this.isFollowUpMode = active;
        Log.d(TAG, "Follow-up conversation mode set to: " + active);
    }

    public boolean isFollowUpMode() {
        return isFollowUpMode;
    }

    public boolean isListening() {
        return isListening && isRunning && !isPaused;
    }

    public static WakeMatchResult checkWakeWord(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new WakeMatchResult(false, "", "");
        }
        String clean = input.toLowerCase()
            .replaceAll("[^a-z0-9' ]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        Matcher m = WAKE_WORD_PATTERN.matcher(clean);
        if (m.find()) {
            int end = m.end();
            String matchedPhrase = clean.substring(m.start(), end).trim();
            String trailing = clean.substring(end).trim();
            return new WakeMatchResult(true, matchedPhrase, trailing);
        }
        return new WakeMatchResult(false, "", "");
    }

    public void playWakeChime() {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 110);
            mainHandler.postDelayed(new ReleaseToneTask(tg), 300);
        } catch (Exception e) {
            Log.w(TAG, "Could not play wake chime: " + e.getMessage());
        }
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

            isTriggered = false;
            speechRecognizer.startListening(recognizerIntent);
            Log.d(TAG, "Active wake-word listening online (waiting for 'Hey Shadow' from silence)");
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

    void handleWakeWordInternal(String phrase, String command) {
        long now = System.currentTimeMillis();
        if (now - lastWakeTriggerTime < WAKE_COOLDOWN_MS) {
            return;
        }
        lastWakeTriggerTime = now;
        isTriggered = true;

        Log.i(TAG, "WAKE-WORD DETECTED! Phrase: '" + phrase + "' | Command: '" + command + "'");

        playWakeChime();

        if (listener != null) {
            listener.onWakeWord(phrase, command);
        }

        // Brief pause after wake word trigger so app can process/speak
        pause();
    }

    void handleFollowUpInternal(String speechText) {
        long now = System.currentTimeMillis();
        if (now - lastWakeTriggerTime < 800) {
            return;
        }
        lastWakeTriggerTime = now;
        Log.i(TAG, "Conversational follow-up speech captured: '" + speechText + "'");
        if (listener != null) {
            listener.onFollowUpSpeech(speechText);
        }
        pause();
    }

    // =========================================================================
    // STATIC NAMED TASKS & LISTENER (STRICT D8 COMPATIBILITY)
    // =========================================================================

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

    static class ReleaseToneTask implements Runnable {
        private final ToneGenerator tg;
        public ReleaseToneTask(ToneGenerator tg) {
            this.tg = tg;
        }
        @Override
        public void run() {
            try {
                if (tg != null) {
                    tg.release();
                }
            } catch (Exception ignored) {}
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
            Log.d(TAG, "Audio detected in silence, parsing wake candidate...");
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

            // In complete silence, timeout (6) and no-match (7) are expected normal events
            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                engine.scheduleRestart(250);
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT || error == 13) {
                engine.scheduleResetAndRestart(600);
            } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Log.e(TAG, "SpeechRecognizer missing RECORD_AUDIO permission!");
            } else {
                Log.d(TAG, "SpeechRecognizer status code: " + error + ", continuing silent sentinel loop");
                engine.scheduleRestart(400);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            WakeWordEngine engine = engineRef.get();
            if (engine == null || !engine.isRunning || engine.isPaused || engine.isTriggered) return;

            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null) {
                for (String text : matches) {
                    WakeMatchResult match = checkWakeWord(text);
                    if (match.matched) {
                        // When slumbering (!isFollowUpMode), trigger instant wake from partial match.
                        // When alert and conversing (isFollowUpMode), wait for onResults so multi-word questions
                        // starting with "hello..." or "wake up..." aren't prematurely interrupted.
                        if (!engine.isFollowUpMode) {
                            engine.handleWakeWordInternal(match.phrase, match.trailingCommand);
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            WakeWordEngine engine = engineRef.get();
            if (engine == null || !engine.isRunning || engine.isPaused) return;

            if (!engine.isTriggered) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    for (String text : matches) {
                        WakeMatchResult match = checkWakeWord(text);
                        if (match.matched) {
                            engine.handleWakeWordInternal(match.phrase, match.trailingCommand);
                            return;
                        }
                    }
                    if (engine.isFollowUpMode) {
                        String best = matches.get(0).trim();
                        if (best.length() > 0) {
                            engine.handleFollowUpInternal(best);
                            return;
                        }
                    }
                }
            }

            // If not triggered, smoothly resume silent sentinel loop
            engine.scheduleRestart(100);
        }

        @Override
        public void onEvent(int eventType, Bundle params) {}
    }
}
