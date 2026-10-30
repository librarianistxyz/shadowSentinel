package com.shadow.eyes;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SentinelCamera.SentinelListener {
    WebView webView;
    SentinelCamera sentinelCamera;
    TextToSpeech textToSpeech;
    boolean isTtsReady = false;
    public volatile boolean isNativeSpeaking = false;
    public volatile int activeUtterances = 0;
    public volatile long speechSessionId = 0;
    public volatile long lastTtsEndTime = 0;
    public volatile String lastSpokenSentence = "";
    public WakeWordEngine wakeWordEngine;
    SensorManager sensorManager;
    Sensor lightSensor;
    ShadowLightSensorListener lightSensorListener;
    public volatile float currentLux = 50.0f;
    public volatile boolean isDarkRoom = false;
    public volatile String currentVoiceGender = "male"; // "male" (baritone) or "female" (siren)
    public volatile String currentEmotion = "neutral";
    public android.speech.tts.Voice maleVoice = null;
    public android.speech.tts.Voice femaleVoice = null;
    final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setVoiceGender(String gender) {
        if (gender == null) return;
        String g = gender.trim().toLowerCase(Locale.US);
        if (g.contains("woman") || g.contains("female") || g.contains("girl")) {
            currentVoiceGender = "female";
            android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY] Persona active: FEMALE (The Siren)");
        } else {
            currentVoiceGender = "male";
            android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY] Persona active: MALE (The Sovereign Baritone)");
        }
    }

    public void setVoiceEmotion(String emotion) {
        if (emotion == null) return;
        currentEmotion = emotion.trim().toLowerCase(Locale.US);
        android.util.Log.i("ShadowEyes", "[VOICE EMOTION] Emotional tone updated: " + currentEmotion);
    }

    private static final int PERMISSION_REQ_CODE = 442;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    };

    static class SpeechStateTask implements Runnable {
        private final WebView webView;
        private final boolean speaking;
        public SpeechStateTask(WebView webView, boolean speaking) {
            this.webView = webView;
            this.speaking = speaking;
        }
        @Override
        public void run() {
            if (webView != null) {
                webView.evaluateJavascript("window.onNativeSpeechState && window.onNativeSpeechState(" + speaking + ");", null);
            }
        }
    }

    static class UtteranceStartTask implements Runnable {
        private final WebView webView;
        private final String utteranceId;
        public UtteranceStartTask(WebView webView, String utteranceId) {
            this.webView = webView;
            this.utteranceId = utteranceId;
        }
        @Override
        public void run() {
            if (webView != null) {
                webView.evaluateJavascript("window.onNativeSpeechUtteranceStart && window.onNativeSpeechUtteranceStart(" + org.json.JSONObject.quote(utteranceId) + ");", null);
            }
        }
    }

    static class UtteranceDoneTask implements Runnable {
        private final WebView webView;
        private final String utteranceId;
        public UtteranceDoneTask(WebView webView, String utteranceId) {
            this.webView = webView;
            this.utteranceId = utteranceId;
        }
        @Override
        public void run() {
            if (webView != null) {
                webView.evaluateJavascript("window.onNativeSpeechUtteranceDone && window.onNativeSpeechUtteranceDone(" + org.json.JSONObject.quote(utteranceId) + ");", null);
            }
        }
    }

    public void notifyNativeSpeechState(final boolean speaking) {
        mainHandler.post(new SpeechStateTask(webView, speaking));
    }

    public void notifyUtteranceStart(final String utteranceId) {
        mainHandler.post(new UtteranceStartTask(webView, utteranceId));
    }

    public void notifyUtteranceDone(final String utteranceId) {
        mainHandler.post(new UtteranceDoneTask(webView, utteranceId));
    }

    static class WakeWordStateTask implements Runnable {
        private final WebView webView;
        private final boolean listening;
        public WakeWordStateTask(WebView webView, boolean listening) {
            this.webView = webView;
            this.listening = listening;
        }
        @Override
        public void run() {
            if (webView != null) {
                webView.evaluateJavascript("window.onWakeWordListeningState && window.onWakeWordListeningState(" + listening + ");", null);
            }
        }
    }

    public void notifyWakeWordState(final boolean listening) {
        mainHandler.post(new WakeWordStateTask(webView, listening));
    }

    static class HandleSpeechRecognizedTask implements Runnable {
        private final MainActivity activity;
        private final String speechText;
        public HandleSpeechRecognizedTask(MainActivity activity, String speechText) {
            this.activity = activity;
            this.speechText = speechText;
        }
        @Override
        public void run() {
            if (activity == null || speechText == null || speechText.trim().isEmpty())
                return;

            long now = System.currentTimeMillis();
            boolean isSpeaking = activity.isNativeSpeaking
                    || (activity.textToSpeech != null && activity.textToSpeech.isSpeaking());
            if (isSpeaking) {
                android.util.Log.i("ShadowEyes",
                        "Acoustic shield: Dropped speech recognized while TTS is speaking: '" + speechText + "'");
                return;
            }

            long timeSinceTts = now - activity.lastTtsEndTime;
            // Acoustic shield: If TTS just finished speaking within 100ms, suppress acoustic echo
            if (timeSinceTts < 100 && activity.lastSpokenSentence != null && !activity.lastSpokenSentence.isEmpty()) {
                String cleanRecognized = speechText.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase(java.util.Locale.ROOT).trim();
                String cleanSpoken = activity.lastSpokenSentence.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase(java.util.Locale.ROOT).trim();
                if (!cleanRecognized.isEmpty() && !cleanSpoken.isEmpty()) {
                    if (cleanSpoken.contains(cleanRecognized) || cleanRecognized.contains(cleanSpoken)) {
                        android.util.Log.i("ShadowEyes", "Acoustic shield: Dropped echo within " + timeSinceTts + "ms matching spoken text: '" + cleanRecognized + "'");
                        return;
                    }
                    String[] words = cleanRecognized.split("\\s+");
                    if (words.length >= 3) {
                        int matchCount = 0;
                        for (String w : words) {
                            if (w.length() >= 3 && cleanSpoken.contains(w)) {
                                matchCount++;
                            }
                        }
                        if (matchCount >= words.length * 0.6) {
                            android.util.Log.i("ShadowEyes", "Acoustic shield: Dropped echo within " + timeSinceTts + "ms with high word overlap (" + matchCount + "/" + words.length + ") against spoken sentence");
                            return;
                        }
                    }
                }
            } else if (timeSinceTts >= 100) {
                // Clear spoken sentence once acoustic reflection window has passed
                activity.lastSpokenSentence = "";
            }

            try {
                PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "ShadowEyes:VoiceInput");
                    wl.acquire(3000);
                }
            } catch (Exception ignored) {}

            // Instant Local Action Routing (Zero round-trip to Cloudflare for hardware commands)
            if (activity.tryExecuteFastPathAction(speechText)) {
                return;
            }

            if (activity.webView != null) {
                String js = "window.ShadowMouth && (window.ShadowMouth.onSpeechRecognized ? " +
                            "window.ShadowMouth.onSpeechRecognized(" + org.json.JSONObject.quote(speechText) + ") : " +
                            "window.ShadowMouth.onFollowUpSpeech(" + org.json.JSONObject.quote(speechText) + "));";
                activity.webView.evaluateJavascript(js, null);
            }
        }
    }

    public void handleSpeechRecognized(final String speechText) {
        mainHandler.post(new HandleSpeechRecognizedTask(this, speechText));
    }

    static class HandlePttTask implements Runnable {
        private final MainActivity activity;
        private final boolean pressed;
        public HandlePttTask(MainActivity activity, boolean pressed) {
            this.activity = activity;
            this.pressed = pressed;
        }
        @Override
        public void run() {
            if (activity == null || activity.webView == null) return;
            if (pressed) {
                if (activity.isNativeSpeaking || (activity.textToSpeech != null && activity.textToSpeech.isSpeaking())) {
                    activity.stopSpeaking();
                }
                if (activity.wakeWordEngine != null) {
                    activity.wakeWordEngine.resume();
                }
            }
            activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onPttStateChanged(" + pressed + ");", null);
        }
    }

    static class TriggerPanicTask implements Runnable {
        private final MainActivity activity;
        public TriggerPanicTask(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void run() {
            if (activity == null || activity.webView == null) return;
            activity.stopSpeaking();
            activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onPanicTriggered();", null);
        }
    }

    static class TriggerIntruderAlertTask implements Runnable {
        private final MainActivity activity;
        public TriggerIntruderAlertTask(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void run() {
            if (activity == null || activity.webView == null) return;
            activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.triggerAlert && window.ShadowMouth.triggerAlert();", null);
        }
    }

    static class ShadowWakeWordListener implements WakeWordEngine.WakeWordListener {
        private final MainActivity activity;
        public ShadowWakeWordListener(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void onSpeechRecognized(String speechText) {
            activity.handleSpeechRecognized(speechText);
        }
        @Override
        public void onInterruptionRequested() {
            activity.handleAcousticInterruption();
        }
        @Override
        public void onListeningStateChanged(boolean isListening) {
            activity.notifyWakeWordState(isListening);
        }
        @Override
        public void onWakeWordRmsChanged(float rmsDb) {}
    }

    static class ShadowLightSensorListener implements SensorEventListener {
        private final java.lang.ref.WeakReference<MainActivity> activityRef;
        public ShadowLightSensorListener(MainActivity activity) {
            this.activityRef = new java.lang.ref.WeakReference<>(activity);
        }
        @Override
        public void onSensorChanged(SensorEvent event) {
            MainActivity activity = activityRef.get();
            if (activity != null && event.values != null && event.values.length > 0) {
                float lux = event.values[0];
                activity.updateAmbientLight(lux);
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    static class NotifyAmbientLightTask implements Runnable {
        private final MainActivity activity;
        private final float lux;
        private final boolean isDark;
        public NotifyAmbientLightTask(MainActivity activity, float lux, boolean isDark) {
            this.activity = activity;
            this.lux = lux;
            this.isDark = isDark;
        }
        @Override
        public void run() {
            if (activity != null && activity.webView != null) {
                activity.webView.evaluateJavascript("if (window.onAmbientLightChanged) window.onAmbientLightChanged(" + lux + ", " + isDark + ");", null);
            }
        }
    }

    public void updateAmbientLight(float lux) {
        this.currentLux = lux;
        boolean dark = (lux < 4.0f); // Ambient light threshold: dark room triggers intimate sinister whisper
        if (dark != this.isDarkRoom) {
            this.isDarkRoom = dark;
            android.util.Log.i("ShadowEyes", "[AMBIENT LIGHT] Lux: " + lux + " lx -> isDarkRoom: " + isDarkRoom);
            mainHandler.post(new NotifyAmbientLightTask(this, lux, dark));
        }
    }

    static class ShadowUtteranceListener extends android.speech.tts.UtteranceProgressListener {
        private final MainActivity activity;
        public ShadowUtteranceListener(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void onStart(String utteranceId) {
            activity.isNativeSpeaking = true;
            activity.notifyNativeSpeechState(true);
            activity.notifyUtteranceStart(utteranceId);
            activity.requestMusicDucking();
            if (activity.wakeWordEngine != null) {
                activity.wakeWordEngine.setInterruptionOnlyMode(true);
            }
        }
        @Override
        public void onDone(String utteranceId) {
            activity.activeUtterances--;
            activity.notifyUtteranceDone(utteranceId);
            boolean stillSpeaking = activity.textToSpeech != null && activity.textToSpeech.isSpeaking();
            if (activity.activeUtterances <= 0 || !stillSpeaking) {
                activity.activeUtterances = 0;
                activity.isNativeSpeaking = false;
                activity.lastTtsEndTime = System.currentTimeMillis();
                activity.notifyNativeSpeechState(false);
                activity.abandonMusicDucking();
                if (activity.wakeWordEngine != null) {
                    activity.wakeWordEngine.notifyTtsFinished();
                    activity.wakeWordEngine.resume();
                }
            }
        }
        @Override
        public void onError(String utteranceId) {
            activity.activeUtterances--;
            activity.notifyUtteranceDone(utteranceId);
            boolean stillSpeaking = activity.textToSpeech != null && activity.textToSpeech.isSpeaking();
            if (activity.activeUtterances <= 0 || !stillSpeaking) {
                activity.activeUtterances = 0;
                activity.isNativeSpeaking = false;
                activity.lastTtsEndTime = System.currentTimeMillis();
                activity.notifyNativeSpeechState(false);
                activity.abandonMusicDucking();
                if (activity.wakeWordEngine != null) {
                    activity.wakeWordEngine.notifyTtsFinished();
                    activity.wakeWordEngine.resume();
                }
            }
        }
    }

    static class TtsInitCallback implements TextToSpeech.OnInitListener {
        private final MainActivity activity;
        public TtsInitCallback(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS && activity.textToSpeech != null) {
                activity.textToSpeech.setLanguage(Locale.US);

                // Configure USAGE_ASSISTANT audio stream for hardware-level vocal clarity
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                        activity.textToSpeech.setAudioAttributes(audioAttributes);
                    } catch (Exception ignored) {}
                }

                // Intelligently select the highest-fidelity Male and Female voices for dynamic emotion switching
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        java.util.Set<android.speech.tts.Voice> voices = activity.textToSpeech.getVoices();
                        if (voices != null && !voices.isEmpty()) {
                            android.speech.tts.Voice bestMale = null;
                            android.speech.tts.Voice bestFemale = null;
                            int bestMaleScore = -1;
                            int bestFemaleScore = -1;

                            for (android.speech.tts.Voice v : voices) {
                                Locale loc = v.getLocale();
                                if (loc == null || !loc.getLanguage().equals("en")) continue;

                                int baseScore = 0;
                                String name = v.getName().toLowerCase(Locale.US);

                                // Prefer US or GB English
                                if (loc.getCountry().equalsIgnoreCase("US")) {
                                    baseScore += 150;
                                } else if (loc.getCountry().equalsIgnoreCase("GB")) {
                                    baseScore += 100;
                                }

                                // Prioritize studio-grade high fidelity quality
                                if (v.getQuality() == android.speech.tts.Voice.QUALITY_VERY_HIGH) {
                                    baseScore += 350;
                                } else if (v.getQuality() == android.speech.tts.Voice.QUALITY_HIGH) {
                                    baseScore += 250;
                                } else if (v.getQuality() == android.speech.tts.Voice.QUALITY_NORMAL) {
                                    baseScore += 50;
                                }

                                if (!v.isNetworkConnectionRequired()) {
                                    baseScore += 100;
                                }

                                // 1. Male Baritone Persona Scoring
                                int mScore = baseScore;
                                if (name.contains("female") || name.contains("woman") || name.contains("girl")) {
                                    mScore -= 600;
                                }
                                if (name.contains("male") || name.contains("baritone") || name.contains("deep") || name.contains("man") || name.contains("rjs") || name.contains("iom") || name.contains("en-us-x-sfg") || name.contains("en-gb-x-rjs")) {
                                    mScore += 450;
                                }
                                if (mScore > bestMaleScore) {
                                    bestMaleScore = mScore;
                                    bestMale = v;
                                }

                                // 2. Female Siren Persona Scoring
                                int fScore = baseScore;
                                if (name.contains("male") || name.contains("baritone") || name.contains("deep") || name.contains("man")) {
                                    fScore -= 600;
                                }
                                if (name.contains("female") || name.contains("woman") || name.contains("girl") || name.contains("tpd") || name.contains("iob") || name.contains("fis") || name.contains("female") || name.contains("en-us-x-tpd") || name.contains("en-us-x-iob")) {
                                    fScore += 450;
                                }
                                if (fScore > bestFemaleScore) {
                                    bestFemaleScore = fScore;
                                    bestFemale = v;
                                }
                            }

                            activity.maleVoice = bestMale;
                            activity.femaleVoice = bestFemale;

                            if (activity.maleVoice != null) {
                                android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY] Selected Male Baritone Voice: " + activity.maleVoice.getName() + " (Score: " + bestMaleScore + ")");
                                activity.textToSpeech.setVoice(activity.maleVoice);
                            }
                            if (activity.femaleVoice != null) {
                                android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY] Selected Female Siren Voice: " + activity.femaleVoice.getName() + " (Score: " + bestFemaleScore + ")");
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.w("ShadowEyes", "Could not enumerate voices: " + e.getMessage());
                    }
                }

                // Sinister Baritone: Deep resonant pitch and deliberate, articulate cinematic cadence
                activity.textToSpeech.setPitch(0.78f);
                activity.textToSpeech.setSpeechRate(0.88f);
                activity.textToSpeech.setOnUtteranceProgressListener(new ShadowUtteranceListener(activity));
                activity.isTtsReady = true;
            }
        }
    }

    static class PromptReceiver extends BroadcastReceiver {
        private final MainActivity activity;
        public PromptReceiver(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            if (activity == null) return;
            if ("com.shadow.eyes.SYNC_DIGEST".equals(intent.getAction())) {
                android.util.Log.i("ShadowEyes", "Received SYNC_DIGEST broadcast - initiating immediate daily digest sync");
                ShadowBrain.getInstance(activity).syncDailyDigestAsync(null, null);
                return;
            }
            if ("com.shadow.eyes.INTERRUPT".equals(intent.getAction()) || intent.getBooleanExtra("interrupt", false)) {
                activity.handleAcousticInterruption();
                return;
            }
            String prompt = intent.getStringExtra("prompt");
            if (prompt != null && activity.webView != null) {
                if (WakeWordEngine.checkInterrupt(prompt)) {
                    activity.handleAcousticInterruption();
                    return;
                }
                if (activity.tryExecuteFastPathAction(prompt)) {
                    return;
                }
                android.util.Log.d("ShadowEyes", "Received test prompt broadcast: " + prompt);
                activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.streamConversation(" + org.json.JSONObject.quote(prompt) + ");", null);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instanceRef = new java.lang.ref.WeakReference<>(this);
        vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveOledMode();

        // Start Persistent Foreground Sentinel Service to never be killed in background
        try {
            Intent serviceIntent = new Intent(this, ShadowService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("ShadowEyes", "Failed to start ShadowService", e);
        }

        textToSpeech = new TextToSpeech(this, new TtsInitCallback(this), "com.google.android.tts");

        // Initialize Hardware Ambient Light Sensor (TMD4906 on Samsung Galaxy S9+)
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (lightSensor != null) {
                lightSensorListener = new ShadowLightSensorListener(this);
                sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
                android.util.Log.i("ShadowEyes", "[LIGHT SENSOR] TMD4906 Ambient Light Sensor registered successfully");
            }
        }

        webView = new WebView(this);
        setContentView(webView);
        configureWebView();

        sentinelCamera = new SentinelCamera(this, this);
        wakeWordEngine = new WakeWordEngine(this, new ShadowWakeWordListener(this));

        IntentFilter promptFilter = new IntentFilter("com.shadow.eyes.TEST_PROMPT");
        promptFilter.addAction("com.shadow.eyes.PROMPT");
        promptFilter.addAction("com.shadow.eyes.INTERRUPT");
        promptFilter.addAction("com.shadow.eyes.SYNC_DIGEST");
        registerReceiver(new PromptReceiver(this), promptFilter);

        if (hasAllPermissions()) {
            sentinelCamera.start();
            wakeWordEngine.setFollowUpMode(true);
            wakeWordEngine.start();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQ_CODE);
            }
        }

        // Active Voice Watchdog: Ensures microphone and speech flags NEVER get stuck
        mainHandler.postDelayed(new TtsWatchdogTask(this), 1000);

        // Sovereign AI Engine: Initialize on-device llama.cpp + Qwen2.5-0.5B-Instruct
        ShadowBrain.getInstance(this).initAsync(new ShadowBrainInitCallback(this));

        // Sync Daily World Knowledge Digest from Cloudflare edge
        ShadowBrain.getInstance(this).syncDailyDigestAsync(null, null);
    }

    static class BrainReadyUiTask implements Runnable {
        private final MainActivity activity;
        public BrainReadyUiTask(MainActivity activity) { this.activity = activity; }
        @Override
        public void run() {
            if (activity != null && activity.webView != null) {
                activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onBrainReady && window.ShadowMouth.onBrainReady(true);", null);
            }
        }
    }

    static class ShadowBrainInitCallback implements ShadowBrain.InitCallback {
        private final MainActivity activity;
        public ShadowBrainInitCallback(MainActivity activity) { this.activity = activity; }
        @Override
        public void onInitialized(boolean success, String message) {
            android.util.Log.i("ShadowEyes", "ShadowBrain onInitialized: " + success + " (" + message + ")");
            if (success && activity != null) {
                activity.runOnUiThread(new BrainReadyUiTask(activity));
            }
        }
    }

    private static java.lang.ref.WeakReference<MainActivity> instanceRef;
    public static MainActivity getInstance() {
        return instanceRef != null ? instanceRef.get() : null;
    }

    private long bixbyLastPressTime = 0;
    private long bixbyDownTime = 0;
    private android.os.Vibrator vibrator;

    public void handlePttPress(final boolean pressed) {
        mainHandler.post(new HandlePttTask(this, pressed));
    }

    public void triggerPanicSwitch() {
        mainHandler.post(new TriggerPanicTask(this));
    }

    public void triggerIntruderAlert() {
        mainHandler.post(new TriggerIntruderAlertTask(this));
    }

    static class ShadowAudioFocusListener implements AudioManager.OnAudioFocusChangeListener {
        @Override
        public void onAudioFocusChange(int focusChange) {}
    }
    private final ShadowAudioFocusListener audioFocusListener = new ShadowAudioFocusListener();
    private AudioFocusRequest audioFocusRequest = null;
    private boolean isMusicDucked = false;

    public synchronized void requestMusicDucking() {
        if (isMusicDucked) return;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(audioFocusListener)
                        .build();
                }
                am.requestAudioFocus(audioFocusRequest);
            } else {
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                );
            }
            isMusicDucked = true;
            android.util.Log.d("ShadowEyes", "[AUDIO DUCKING] Requested AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK - background music ducked");
        } catch (Exception e) {
            android.util.Log.w("ShadowEyes", "requestMusicDucking error: " + e.getMessage());
        }
    }

    public synchronized void abandonMusicDucking() {
        if (!isMusicDucked) return;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                    am.abandonAudioFocusRequest(audioFocusRequest);
                } else {
                    am.abandonAudioFocus(audioFocusListener);
                }
                android.util.Log.d("ShadowEyes", "[AUDIO DUCKING] Abandoned audio focus - background music restored");
            }
        } catch (Exception e) {
            android.util.Log.w("ShadowEyes", "abandonMusicDucking error: " + e.getMessage());
        } finally {
            isMusicDucked = false;
        }
    }

    // =========================================================================
    // FAST-PATH LOCAL ACTION ROUTING (Zero Round-Trip for Hardware Commands)
    // =========================================================================
    private static final java.util.regex.Pattern FAST_TORCH_ON = java.util.regex.Pattern.compile(
        "\\b(?:turn\\s+on\\s+(?:the\\s+)?(?:flashlight|torch)|(?:flashlight|torch)\\s+on|enable\\s+(?:the\\s+)?(?:flashlight|torch))\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_TORCH_OFF = java.util.regex.Pattern.compile(
        "\\b(?:turn\\s+off\\s+(?:the\\s+)?(?:flashlight|torch)|(?:flashlight|torch)\\s+off|disable\\s+(?:the\\s+)?(?:flashlight|torch))\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_TORCH_GENERIC = java.util.regex.Pattern.compile(
        "^\\s*(?:flashlight|torch)\\s*$",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern FAST_VOL_UP = java.util.regex.Pattern.compile(
        "\\b(?:volume\\s+up|turn\\s+up\\s+(?:the\\s+)?volume|increase\\s+(?:the\\s+)?volume|raise\\s+(?:the\\s+)?volume|louder)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_VOL_DOWN = java.util.regex.Pattern.compile(
        "\\b(?:volume\\s+down|turn\\s+down\\s+(?:the\\s+)?volume|decrease\\s+(?:the\\s+)?volume|lower\\s+(?:the\\s+)?volume|softer)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_VOL_MUTE = java.util.regex.Pattern.compile(
        "^\\s*(?:mute|unmute|mute\\s+volume|silence\\s+audio)\\s*$",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern FAST_PHOTO = java.util.regex.Pattern.compile(
        "\\b(?:take\\s+(?:a\\s+)?photo|take\\s+(?:a\\s+)?picture|snap\\s+(?:a\\s+)?photo|snap\\s+(?:a\\s+)?picture|capture\\s+photo)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_VIDEO = java.util.regex.Pattern.compile(
        "\\b(?:record\\s+(?:a\\s+)?video|start\\s+recording|capture\\s+video)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern FAST_MEDIA_PAUSE = java.util.regex.Pattern.compile(
        "\\b(?:pause\\s+(?:music|song|playback)|stop\\s+music)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_MEDIA_RESUME = java.util.regex.Pattern.compile(
        "\\b(?:resume\\s+(?:music|song|playback)|play\\s+music|continue\\s+music)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_MEDIA_NEXT = java.util.regex.Pattern.compile(
        "\\b(?:next\\s+(?:song|track)|skip\\s+(?:song|track))\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    private static final java.util.regex.Pattern FAST_MEDIA_PREV = java.util.regex.Pattern.compile(
        "\\b(?:previous\\s+(?:song|track)|prev\\s+(?:song|track))\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern FAST_OPEN_APP = java.util.regex.Pattern.compile(
        "^\\s*(?:can you\\s+)?(?:please\\s+)?(?:open|launch|start|run|go to)\\s+(?:the\\s+)?([a-zA-Z0-9 ]+?)\\s*(?:app|application|apk)?\\s*$",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static final java.util.regex.Pattern CONVERSATIONAL_QUESTION = java.util.regex.Pattern.compile(
        "^\\s*(?:who|what|where|when|why|how|explain|tell me|can you tell|define|is it|are you|do you|what's|how's)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private boolean isTorchActive = false;

    public void toggleTorch() {
        isTorchActive = !isTorchActive;
        executeDeviceAction("TORCH", isTorchActive ? "ON" : "OFF");
        speak(isTorchActive ? "Flashlight on." : "Flashlight off.");
    }

    public void wakeMouthVisual() {
        if (webView != null) {
            webView.evaluateJavascript("window.maintainActiveConversation && window.maintainActiveConversation();", null);
        }
    }

    public boolean tryExecuteFastPathAction(final String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return false;
        final String text = rawText.trim();

        // 1. Guard: Never intercept conversational questions or quiz prompts
        if (CONVERSATIONAL_QUESTION.matcher(text).find()) {
            return false;
        }

        // 2. Torch / Flashlight:
        if (FAST_TORCH_ON.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Torch ON triggered locally in 2ms");
            triggerHapticClick(45);
            executeDeviceAction("TORCH", "ON");
            speak("Flashlight on.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_TORCH_OFF.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Torch OFF triggered locally in 2ms");
            triggerHapticClick(45);
            executeDeviceAction("TORCH", "OFF");
            speak("Flashlight off.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_TORCH_GENERIC.matcher(text).matches()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Torch toggle triggered locally in 2ms");
            triggerHapticClick(45);
            toggleTorch();
            wakeMouthVisual();
            return true;
        }

        // 3. Volume Controls:
        if (FAST_VOL_UP.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Volume UP triggered locally in 2ms");
            triggerHapticClick(35);
            executeDeviceAction("VOLUME", "UP");
            speak("Volume up.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_VOL_DOWN.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Volume DOWN triggered locally in 2ms");
            triggerHapticClick(35);
            executeDeviceAction("VOLUME", "DOWN");
            speak("Volume down.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_VOL_MUTE.matcher(text).matches()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Volume MUTE triggered locally in 2ms");
            triggerHapticClick(35);
            executeDeviceAction("VOLUME", "MUTE");
            wakeMouthVisual();
            return true;
        }

        // 4. Photo / Video:
        if (FAST_PHOTO.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Take photo triggered locally in 2ms");
            triggerHapticClick(50);
            executeDeviceAction("TAKE_PHOTO", "");
            speak("Taking photo.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_VIDEO.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Record video triggered locally in 2ms");
            triggerHapticClick(50);
            executeDeviceAction("RECORD_VIDEO", "");
            speak("Recording video.");
            wakeMouthVisual();
            return true;
        }

        // 5. Media Playback Controls:
        if (FAST_MEDIA_PAUSE.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Media pause triggered locally in 2ms");
            triggerHapticClick(35);
            executeDeviceAction("PAUSE_MEDIA", "");
            speak("Paused.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_MEDIA_RESUME.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Media resume triggered locally in 2ms");
            triggerHapticClick(35);
            executeDeviceAction("RESUME_MEDIA", "");
            speak("Resuming music.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_MEDIA_NEXT.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Media next track triggered locally in 2ms");
            triggerHapticClick(35);
            executeDeviceAction("NEXT_TRACK", "");
            speak("Next track.");
            wakeMouthVisual();
            return true;
        }
        if (FAST_MEDIA_PREV.matcher(text).find()) {
            android.util.Log.i("ShadowEyes", "[FAST-PATH] Media previous track triggered locally in 2ms");
            triggerHapticClick(35);
            executeDeviceAction("PREV_TRACK", "");
            speak("Previous track.");
            wakeMouthVisual();
            return true;
        }

        // 6. Direct App Launch (e.g. "open WhatsApp", "open Spotify", "open Camera", etc.):
        java.util.regex.Matcher appMatcher = FAST_OPEN_APP.matcher(text);
        if (appMatcher.matches()) {
            String appName = appMatcher.group(1);
            if (appName != null && !appName.trim().isEmpty()) {
                appName = appName.trim();
                boolean launched = launchAppByName(appName);
                if (launched) {
                    android.util.Log.i("ShadowEyes", "[FAST-PATH] Launched app '" + appName + "' locally in 2ms");
                    triggerHapticClick(45);
                    speak("Opening " + appName + ".");
                    wakeMouthVisual();
                    return true;
                }
            }
        }

        return false;
    }

    private void triggerHapticClick(int durationMs) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(durationMs);
                }
            }
        } catch (Exception ignored) {}
    }

    private void triggerHapticPanic() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 80, 50, 120};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    private void handleBixbyKeyEvent(KeyEvent event) {
        int action = event.getAction();
        long now = System.currentTimeMillis();

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                bixbyDownTime = now;
                boolean isDoublePress = (now - bixbyLastPressTime < 450);
                bixbyLastPressTime = now;

                if (isDoublePress) {
                    android.util.Log.w("ShadowEyes", "[PANIC SWITCH] Double-press detected on Bixby button!");
                    triggerHapticPanic();
                    triggerPanicSwitch();
                } else {
                    android.util.Log.i("ShadowEyes", "[BIXBY PTT] Key down: Hardware Push-To-Talk active");
                    triggerHapticClick(35);
                    handlePttPress(true);
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            long holdDuration = now - bixbyDownTime;
            android.util.Log.i("ShadowEyes", "[BIXBY PTT] Key up: Hardware Push-To-Talk released (hold " + holdDuration + "ms)");
            triggerHapticClick(15);
            handlePttPress(false);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == 238 || keyCode == KeyEvent.KEYCODE_ASSIST) {
            handleBixbyKeyEvent(event);
            return true; // Consume event completely so Samsung Bixby never launches
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == 238 || keyCode == KeyEvent.KEYCODE_ASSIST) {
            handleBixbyKeyEvent(event);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == 238 || keyCode == KeyEvent.KEYCODE_ASSIST) {
            handleBixbyKeyEvent(event);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            if (intent.getBooleanExtra("ACTION_PANIC", false)) {
                triggerPanicSwitch();
            } else if (intent.getBooleanExtra("ACTION_PTT", false)) {
                handlePttPress(true);
            }
        }
    }

    static class ShadowWebChromeClient extends android.webkit.WebChromeClient {
        @Override
        public void onPermissionRequest(final android.webkit.PermissionRequest request) {
            request.grant(request.getResources());
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
            android.util.Log.d("ShadowJS", "[" + cm.messageLevel() + "] " + cm.message());
            return true;
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.clearCache(true);
        WebView.setWebContentsDebuggingEnabled(true);
        webView.setWebChromeClient(new ShadowWebChromeClient());
        webView.setBackgroundColor(0xFF000000);
        webView.addJavascriptInterface(new ShadowBridge(this), "ShadowNative");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void applyImmersiveOledMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveOledMode();
            if (webView != null) {
                webView.evaluateJavascript("window.resumeListening && window.resumeListening();", null);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveOledMode();
        if (sensorManager != null && lightSensor != null && lightSensorListener != null) {
            sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
            webView.evaluateJavascript("window.resumeListening && window.resumeListening();", null);
        }
        if (sentinelCamera != null && hasAllPermissions()) {
            sentinelCamera.start();
        }
        if (wakeWordEngine != null && hasAllPermissions()) {
            wakeWordEngine.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && lightSensorListener != null) {
            sensorManager.unregisterListener(lightSensorListener);
        }
        if (wakeWordEngine != null) {
            wakeWordEngine.resume(); // Permanent sentinel vigilance: keep microphone listening across window transitions
        }
        abandonMusicDucking();
    }

    private boolean hasAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String p : REQUIRED_PERMISSIONS) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            if (hasAllPermissions()) {
                sentinelCamera.start();
                if (wakeWordEngine != null) {
                    wakeWordEngine.setFollowUpMode(true);
                    wakeWordEngine.start();
                }
            }
        }
    }

    static class FaceSpottedTask implements Runnable {
        private final WebView webView;
        private final int faceCount;
        public FaceSpottedTask(WebView webView, int faceCount) {
            this.webView = webView;
            this.faceCount = faceCount;
        }
        @Override
        public void run() {
            if (webView != null) {
                webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onFaceDetected(" + faceCount + ");", null);
            }
        }
    }

    static class TtsWatchdogTask implements Runnable {
        private final java.lang.ref.WeakReference<MainActivity> activityRef;
        public TtsWatchdogTask(MainActivity activity) {
            this.activityRef = new java.lang.ref.WeakReference<>(activity);
        }
        @Override
        public void run() {
            MainActivity activity = activityRef.get();
            if (activity == null) return;
            boolean ttsHardwareSpeaking = activity.isTtsReady && activity.textToSpeech != null && activity.textToSpeech.isSpeaking();
            if (!ttsHardwareSpeaking && activity.isNativeSpeaking) {
                android.util.Log.d("ShadowEyes", "[WATCHDOG] Force clearing stuck isNativeSpeaking");
                activity.activeUtterances = 0;
                activity.isNativeSpeaking = false;
                activity.lastTtsEndTime = System.currentTimeMillis();
                activity.notifyNativeSpeechState(false);
                activity.abandonMusicDucking();
                if (activity.wakeWordEngine != null) {
                    activity.wakeWordEngine.notifyTtsFinished();
                    activity.wakeWordEngine.resume();
                }
            }
            activity.mainHandler.postDelayed(this, 1000);
        }
    }

    static class SpeakTask implements Runnable {
        private final MainActivity activity;
        private final String text;
        private final String utteranceId;
        private final long sessionId;

        public SpeakTask(MainActivity activity, String text, long sessionId) {
            this(activity, text, "shadow_speech_" + System.currentTimeMillis(), sessionId);
        }

        public SpeakTask(MainActivity activity, String text, String utteranceId, long sessionId) {
            this.activity = activity;
            this.text = text;
            this.utteranceId = utteranceId != null && !utteranceId.isEmpty() ? utteranceId : "shadow_speech_" + System.currentTimeMillis();
            this.sessionId = sessionId;
        }

        private static String smoothenText(String raw) {
            if (raw == null) return "";
            // Strip stage directions like *whispering*, [whispering], (softly) so they are never spoken
            raw = raw.replaceAll("(?i)\\*[^*]*\\*", "")
                     .replaceAll("(?i)\\[[^\\]]*\\]", "")
                     .replaceAll("(?i)\\([^)]*\\)", "");
            return raw.trim()
                .replaceAll("(?i)^(?:hello[!,.]?\\s*)?(?:it'?s\\s+)?lovely\\s+to\\s+(?:help\\s+you(?:\\s+with\\s+that)?|hear\\s+from\\s+you|meet\\s+you)[!,.]?\\s*", "")
                .replaceAll("\\bAI\\b", "A.I.")
                .replaceAll("\\bAPI\\b", "A.P.I.")
                .replaceAll("\\be\\.g\\.", "for example")
                .replaceAll("\\bi\\.e\\.", "that is")
                .replaceAll("\\bdept\\.", "department")
                .replaceAll("\\bapprox\\.", "approximately")
                .replaceAll("\\bvs\\.?\\b", "versus")
                .replaceAll("[*#_~`\\[\\](){}<>|\"^]", "")
                .replaceAll("\\s+", " ")
                .trim();
        }

        @Override
        public void run() {
            if (activity == null) return;
            // CRITICAL: If session was invalidated (e.g. by "listen" / interruption), drop this utterance immediately!
            if (this.sessionId != activity.speechSessionId) {
                android.util.Log.i("ShadowEyes", "Dropping speech clause from aborted/silenced session (target: " + sessionId + ", current: " + activity.speechSessionId + ")");
                return;
            }
            if (activity.isTtsReady && activity.textToSpeech != null && text != null) {
                String clean = smoothenText(text);
                if (!clean.isEmpty()) {
                    if (activity.activeUtterances <= 0 || activity.lastSpokenSentence == null
                            || activity.lastSpokenSentence.isEmpty()) {
                        activity.lastSpokenSentence = clean;
                    } else {
                        activity.lastSpokenSentence = activity.lastSpokenSentence + " " + clean;
                    }
                    activity.activeUtterances++;
                    activity.requestMusicDucking();
                    if (activity.wakeWordEngine != null) {
                        activity.wakeWordEngine.setInterruptionOnlyMode(true);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Bundle ttsParams = new Bundle();
                        boolean isFemale = "female".equalsIgnoreCase(activity.currentVoiceGender);

                        if (isFemale) {
                            if (activity.femaleVoice != null) {
                                activity.textToSpeech.setVoice(activity.femaleVoice);
                            }
                            if (activity.isDarkRoom) {
                                // Contextual Siren Whisper in dark room: intimate, velvety, quiet female whisper
                                activity.textToSpeech.setPitch(1.02f);
                                activity.textToSpeech.setSpeechRate(0.86f);
                                ttsParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.32f);
                                android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY: FEMALE WHISPER] Intimate feminine whisper (" + activity.currentLux + " lx)");
                            } else {
                                // Silky, articulate feminine delivery
                                activity.textToSpeech.setPitch(1.15f);
                                activity.textToSpeech.setSpeechRate(0.92f);
                                ttsParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.90f);
                                android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY: FEMALE] Silky feminine vocal delivery (" + activity.currentLux + " lx)");
                            }
                        } else {
                            if (activity.maleVoice != null) {
                                activity.textToSpeech.setVoice(activity.maleVoice);
                            }
                            if (activity.isDarkRoom) {
                                // Contextual Whisper in dark room: intimate, sinister, quiet baritone whisper
                                activity.textToSpeech.setPitch(0.70f);
                                activity.textToSpeech.setSpeechRate(0.84f);
                                ttsParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.32f);
                                android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY: MALE WHISPER] Intimate baritone whisper (" + activity.currentLux + " lx)");
                            } else {
                                // Illuminated room: deep resonant baritone
                                activity.textToSpeech.setPitch(0.78f);
                                activity.textToSpeech.setSpeechRate(0.88f);
                                ttsParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.90f);
                                android.util.Log.i("ShadowEyes", "[VOICE PERSONALITY: MALE] Resonant baritone delivery (" + activity.currentLux + " lx)");
                            }
                        }
                        activity.textToSpeech.speak(clean, TextToSpeech.QUEUE_ADD, ttsParams, this.utteranceId);
                    } else {
                        activity.textToSpeech.speak(clean, TextToSpeech.QUEUE_ADD, null, this.utteranceId);
                    }
                    android.util.Log.i("ShadowEyes", "Speaking: \"" + clean + "\"");
                }
            }
        }
    }

    static class StopSpeakTask implements Runnable {
        private final MainActivity activity;
        public StopSpeakTask(MainActivity activity) { this.activity = activity; }
        @Override
        public void run() {
            if (activity.textToSpeech != null) {
                activity.textToSpeech.stop();
            }
            activity.activeUtterances = 0;
            activity.isNativeSpeaking = false;
            activity.lastTtsEndTime = System.currentTimeMillis();
            activity.notifyNativeSpeechState(false);
            activity.abandonMusicDucking();
            if (activity.wakeWordEngine != null) {
                activity.wakeWordEngine.notifyTtsFinished();
                activity.wakeWordEngine.resume();
            }
        }
    }

    static class ResumeWakeWordTask implements Runnable {
        private final java.lang.ref.WeakReference<WakeWordEngine> engineRef;
        public ResumeWakeWordTask(WakeWordEngine engine) {
            this.engineRef = new java.lang.ref.WeakReference<>(engine);
        }
        @Override
        public void run() {
            WakeWordEngine engine = engineRef.get();
            if (engine != null) {
                engine.resume();
            }
        }
    }

    static class AcousticInterruptionTask implements Runnable {
        private final MainActivity activity;
        public AcousticInterruptionTask(MainActivity activity) { this.activity = activity; }
        @Override
        public void run() {
            if (activity == null) return;
            android.util.Log.i("ShadowEyes", "Acoustic interruption received ('listen' / 'quiet') - shutting up completely, waiting for next question");
            ShadowBrain.getInstance(activity).stop();
            activity.speechSessionId++;
            if (activity.textToSpeech != null) {
                activity.textToSpeech.stop();
            }
            activity.activeUtterances = 0;
            activity.isNativeSpeaking = false;
            activity.lastTtsEndTime = System.currentTimeMillis();
            activity.notifyNativeSpeechState(false);
            activity.abandonMusicDucking();
            if (activity.wakeWordEngine != null) {
                activity.wakeWordEngine.setInterruptionOnlyMode(false);
                activity.wakeWordEngine.pause();
                activity.mainHandler.postDelayed(new ResumeWakeWordTask(activity.wakeWordEngine), 80);
            }
            // Subtle 35ms haptic confirmation
            try {
                if (activity.vibrator != null && activity.vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        activity.vibrator.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        activity.vibrator.vibrate(35);
                    }
                }
            } catch (Exception ignored) {}

            if (activity.webView != null) {
                activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onAcousticInterrupted && window.ShadowMouth.onAcousticInterrupted();", null);
            }
        }
    }

    static class LaunchIntentTask implements Runnable {
        private final MainActivity activity;
        private final Intent intent;
        public LaunchIntentTask(MainActivity activity, Intent intent) {
            this.activity = activity;
            this.intent = intent;
        }
        @Override
        public void run() {
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed to launch app intent", e);
            }
        }
    }

    static class BringShadowToFrontTask implements Runnable {
        private final MainActivity activity;
        public BringShadowToFrontTask(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void run() {
            try {
                Intent bringBack = new Intent(activity, MainActivity.class);
                bringBack.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(bringBack);
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed to bring Shadow to front", e);
            }
        }
    }

    static class DispatchMediaKeyTask implements Runnable {
        private final MainActivity activity;
        private final int keyCode;
        public DispatchMediaKeyTask(MainActivity activity, int keyCode) {
            this.activity = activity;
            this.keyCode = keyCode;
        }
        @Override
        public void run() {
            if (activity == null) return;
            AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long now = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
            }
        }
    }

    static class TriggerCameraShutterTask implements Runnable {
        private final MainActivity activity;
        public TriggerCameraShutterTask(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void run() {
            if (activity == null) return;
            AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long now = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CAMERA, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CAMERA, 0));
            }
        }
    }

    static class SetTorchTask implements Runnable {
        private final MainActivity activity;
        private final boolean enabled;
        public SetTorchTask(MainActivity activity, boolean enabled) {
            this.activity = activity;
            this.enabled = enabled;
        }
        @Override
        public void run() {
            if (activity == null) return;
            try {
                CameraManager cm = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                if (cm != null) {
                    for (String id : cm.getCameraIdList()) {
                        CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                        Boolean flashAvailable = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                        if (flashAvailable != null && flashAvailable && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                            cm.setTorchMode(id, enabled);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Torch error", e);
            }
        }
    }

    static class AdjustVolumeTask implements Runnable {
        private final MainActivity activity;
        private final int direction;
        public AdjustVolumeTask(MainActivity activity, int direction) {
            this.activity = activity;
            this.direction = direction;
        }
        @Override
        public void run() {
            if (activity == null) return;
            AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.adjustVolume(direction, AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND);
            }
        }
    }

    public boolean executeDeviceAction(final String actionType, final String payload) {
        if (actionType == null) return false;
        final String action = actionType.trim().toUpperCase(Locale.US);
        final String param = payload != null ? payload.trim() : "";

        android.util.Log.i("ShadowEyes", "executeDeviceAction -> action: " + action + ", param: " + param);

        if (action.equals("PLAY_MEDIA")) {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
            intent.putExtra(SearchManager.QUERY, param);
            intent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = getPackageManager();
            if (pm.getLaunchIntentForPackage("com.spotify.music") != null) {
                intent.setPackage("com.spotify.music");
            }

            try {
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent uriIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(param)));
                    uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(uriIntent);
                } catch (Exception ex) {
                    launchAppByName("Spotify");
                }
            }

            // Auto-trigger playback and maintain Shadow in foreground
            mainHandler.postDelayed(new DispatchMediaKeyTask(this, KeyEvent.KEYCODE_MEDIA_PLAY), 800);
            mainHandler.postDelayed(new DispatchMediaKeyTask(this, KeyEvent.KEYCODE_MEDIA_PLAY), 1800);
            mainHandler.postDelayed(new BringShadowToFrontTask(this), 350);
            mainHandler.postDelayed(new BringShadowToFrontTask(this), 900);
            return true;
        }

        if (action.equals("PAUSE_MEDIA")) {
            mainHandler.post(new DispatchMediaKeyTask(this, KeyEvent.KEYCODE_MEDIA_PAUSE));
            return true;
        }

        if (action.equals("RESUME_MEDIA")) {
            mainHandler.post(new DispatchMediaKeyTask(this, KeyEvent.KEYCODE_MEDIA_PLAY));
            return true;
        }

        if (action.equals("NEXT_TRACK")) {
            mainHandler.post(new DispatchMediaKeyTask(this, KeyEvent.KEYCODE_MEDIA_NEXT));
            return true;
        }

        if (action.equals("PREV_TRACK")) {
            mainHandler.post(new DispatchMediaKeyTask(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
            return true;
        }

        if (action.equals("TAKE_PHOTO")) {
            Intent cameraIntent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(cameraIntent);
                mainHandler.postDelayed(new TriggerCameraShutterTask(this), 1300);
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed launching camera for photo", e);
            }
            return true;
        }

        if (action.equals("RECORD_VIDEO")) {
            Intent videoIntent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
            videoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(videoIntent);
                mainHandler.postDelayed(new TriggerCameraShutterTask(this), 1300);
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed launching video camera", e);
            }
            return true;
        }

        if (action.equals("TORCH")) {
            boolean enable = "ON".equalsIgnoreCase(param) || "TRUE".equalsIgnoreCase(param);
            this.isTorchActive = enable;
            mainHandler.post(new SetTorchTask(this, enable));
            return true;
        }

        if (action.equals("VOLUME")) {
            int dir = AudioManager.ADJUST_SAME;
            if ("UP".equalsIgnoreCase(param) || "RAISE".equalsIgnoreCase(param)) {
                dir = AudioManager.ADJUST_RAISE;
            } else if ("DOWN".equalsIgnoreCase(param) || "LOWER".equalsIgnoreCase(param)) {
                dir = AudioManager.ADJUST_LOWER;
            } else if ("MUTE".equalsIgnoreCase(param)) {
                dir = AudioManager.ADJUST_TOGGLE_MUTE;
            }
            mainHandler.post(new AdjustVolumeTask(this, dir));
            return true;
        }

        if (action.equals("SEARCH_YOUTUBE")) {
            try {
                Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=" + Uri.encode(param)));
                ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(ytIntent);
            } catch (Exception e) {
                Intent webYt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(param)));
                webYt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(webYt);
            }
            mainHandler.postDelayed(new BringShadowToFrontTask(this), 350);
            mainHandler.postDelayed(new BringShadowToFrontTask(this), 750);
            return true;
        }

        if (action.equals("SET_TIMER")) {
            try {
                int seconds = 60;
                try {
                    seconds = Integer.parseInt(param);
                } catch (Exception ex) {
                    if (param.contains("min")) {
                        String num = param.replaceAll("[^0-9]", "");
                        if (!num.isEmpty()) seconds = Integer.parseInt(num) * 60;
                    }
                }
                Intent timer = new Intent(AlarmClock.ACTION_SET_TIMER);
                timer.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
                timer.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                timer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(timer);
                return true;
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed setting timer", e);
            }
        }

        if (action.equals("SET_ALARM")) {
            try {
                Intent alarm = new Intent(AlarmClock.ACTION_SET_ALARM);
                alarm.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                alarm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (param.contains(":")) {
                    String[] parts = param.split(":");
                    alarm.putExtra(AlarmClock.EXTRA_HOUR, Integer.parseInt(parts[0].trim()));
                    alarm.putExtra(AlarmClock.EXTRA_MINUTES, Integer.parseInt(parts[1].trim()));
                }
                startActivity(alarm);
                return true;
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed setting alarm", e);
            }
        }

        if (action.equals("NAVIGATE")) {
            try {
                Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(param)));
                nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(nav);
                return true;
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed navigation intent", e);
            }
        }

        if (action.equals("CALL_NUMBER")) {
            try {
                String cleanNum = param.replaceAll("[^0-9+]", "");
                Intent call = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleanNum));
                call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(call);
                return true;
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Failed dial intent", e);
            }
        }

        if (action.equals("OPEN_APP")) {
            return launchAppByName(param);
        }

        // ================================================================
        // EXPANDED IOT & HARDWARE ECOSYSTEM INTEGRATIONS
        // ================================================================

        // 1. Smart Lights (Home Assistant / Hue / SmartThings intent)
        if (action.equals("SMART_LIGHT")) {
            android.util.Log.i("ShadowEyes", "[IOT INTEGRATION] Smart Lights Action: " + param);
            Intent lightIntent = new Intent("com.shadow.action.SMART_LIGHT");
            lightIntent.putExtra("query", param);
            sendBroadcast(lightIntent);
            return true;
        }

        // 2. Smart Locks (Check status / lock / unlock)
        if (action.equals("SMART_LOCK")) {
            android.util.Log.i("ShadowEyes", "[IOT INTEGRATION] Smart Lock Action: " + param);
            Intent lockIntent = new Intent("com.shadow.action.SMART_LOCK");
            lockIntent.putExtra("query", param);
            sendBroadcast(lockIntent);
            return true;
        }

        // 3. Security Cameras (Analyze authorized feeds)
        if (action.equals("SECURITY_CAM")) {
            android.util.Log.i("ShadowEyes", "[IOT INTEGRATION] Security Camera Feed Request: " + param);
            Intent camIntent = new Intent("com.shadow.action.SECURITY_CAM");
            camIntent.putExtra("feed", param);
            sendBroadcast(camIntent);
            return true;
        }

        // 4. Computer & Desktop Automation Workflows
        if (action.equals("COMPUTER_ACTION")) {
            android.util.Log.i("ShadowEyes", "[DESKTOP INTEGRATION] Computer Automation Action: " + param);
            Intent compIntent = new Intent("com.shadow.action.COMPUTER_WORKFLOW");
            compIntent.putExtra("action", param);
            sendBroadcast(compIntent);
            return true;
        }

        // 5. Smart Speakers & Intercom Announcements
        if (action.equals("SMART_SPEAKER")) {
            android.util.Log.i("ShadowEyes", "[SMART SPEAKER] Broadcast Announcement: " + param);
            Intent speakerIntent = new Intent("com.shadow.action.SMART_SPEAKER");
            speakerIntent.putExtra("message", param);
            sendBroadcast(speakerIntent);
            return true;
        }

        // 6. Compatible Robots (Vacuum, Rover, Manipulator)
        if (action.equals("ROBOT_CMD")) {
            android.util.Log.i("ShadowEyes", "[ROBOTICS INTEGRATION] Issue Task Command: " + param);
            Intent robotIntent = new Intent("com.shadow.action.ROBOT_COMMAND");
            robotIntent.putExtra("command", param);
            sendBroadcast(robotIntent);
            return true;
        }

        // 7. Environmental Sensors (Temperature, Humidity, Motion)
        if (action.equals("SENSOR_QUERY")) {
            android.util.Log.i("ShadowEyes", "[SENSOR INTEGRATION] Environmental Sensor Poll: " + param);
            Intent sensorIntent = new Intent("com.shadow.action.SENSOR_POLL");
            sensorIntent.putExtra("type", param);
            sendBroadcast(sensorIntent);
            return true;
        }

        // 8. Calendar & Schedule Access
        if (action.equals("CALENDAR_ACTION")) {
            android.util.Log.i("ShadowEyes", "[CALENDAR INTEGRATION] Calendar Access Action: " + param);
            try {
                Intent calIntent = new Intent(Intent.ACTION_VIEW);
                calIntent.setData(Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()));
                calIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(calIntent);
                return true;
            } catch (Exception e) {
                android.util.Log.e("ShadowEyes", "Calendar access error", e);
            }
        }

        return false;
    }

    public boolean launchAppByName(final String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) return false;
        final String cleanTarget = targetName.trim().toLowerCase(Locale.US)
                .replaceAll("^(the|a|an|my|our)\\s+", "")
                .replaceAll("\\s+(app|application|apk)$", "")
                .trim();

        PackageManager pm = getPackageManager();

        // 1. CANONICAL APP ALIAS MAP (EVALUATED FIRST)
        String bestPackage = null;
        if (cleanTarget.equals("spotify")) {
            bestPackage = "com.spotify.music";
        } else if (cleanTarget.equals("camera")) {
            bestPackage = "com.sec.android.app.camera";
        } else if (cleanTarget.equals("calculator")) {
            bestPackage = "com.sec.android.app.popupcalculator";
        } else if (cleanTarget.equals("whatsapp")) {
            bestPackage = pm.getLaunchIntentForPackage("com.whatsapp") != null ? "com.whatsapp" : "com.whatsapp.w4b";
        } else if (cleanTarget.equals("whatsapp business") || cleanTarget.equals("wa business")) {
            bestPackage = "com.whatsapp.w4b";
        } else if (cleanTarget.equals("gallery") || cleanTarget.equals("photos") || cleanTarget.equals("photo")) {
            bestPackage = pm.getLaunchIntentForPackage("com.sec.android.gallery3d") != null ? "com.sec.android.gallery3d" : "com.google.android.apps.photos";
        } else if (cleanTarget.equals("google photos")) {
            bestPackage = "com.google.android.apps.photos";
        } else if (cleanTarget.equals("settings")) {
            bestPackage = "com.android.settings";
        } else if (cleanTarget.equals("chrome") || cleanTarget.equals("google chrome")) {
            bestPackage = "com.android.chrome";
        } else if (cleanTarget.equals("browser") || cleanTarget.equals("internet") || cleanTarget.equals("web")) {
            bestPackage = pm.getLaunchIntentForPackage("com.android.chrome") != null ? "com.android.chrome" : "com.sec.android.app.sbrowser";
        } else if (cleanTarget.equals("samsung internet")) {
            bestPackage = "com.sec.android.app.sbrowser";
        } else if (cleanTarget.equals("youtube")) {
            bestPackage = "com.google.android.youtube";
        } else if (cleanTarget.equals("youtube studio") || cleanTarget.equals("studio")) {
            bestPackage = "com.google.android.apps.youtube.creator";
        } else if (cleanTarget.equals("x") || cleanTarget.equals("twitter")) {
            bestPackage = "com.twitter.android";
        } else if (cleanTarget.equals("instagram")) {
            bestPackage = "com.instagram.android";
        } else if (cleanTarget.equals("threads")) {
            bestPackage = "com.instagram.barcelona";
        } else if (cleanTarget.equals("telegram")) {
            bestPackage = "org.telegram.messenger";
        } else if (cleanTarget.equals("tiktok")) {
            bestPackage = pm.getLaunchIntentForPackage("com.zhiliaoapp.musically") != null ? "com.zhiliaoapp.musically" : "com.zhiliaoapp.musically.go";
        } else if (cleanTarget.equals("tiktok lite")) {
            bestPackage = "com.zhiliaoapp.musically.go";
        } else if (cleanTarget.equals("capcut")) {
            bestPackage = "com.lemon.lvoverseas";
        } else if (cleanTarget.equals("chatgpt") || cleanTarget.equals("chat gpt")) {
            bestPackage = "com.openai.chatgpt";
        } else if (cleanTarget.equals("grok")) {
            bestPackage = "ai.x.grok";
        } else if (cleanTarget.equals("facebook")) {
            bestPackage = pm.getLaunchIntentForPackage("com.facebook.katana") != null ? "com.facebook.katana" : "com.facebook.lite";
        } else if (cleanTarget.equals("facebook lite")) {
            bestPackage = "com.facebook.lite";
        } else if (cleanTarget.equals("messenger")) {
            bestPackage = "com.facebook.orca";
        } else if (cleanTarget.equals("maps") || cleanTarget.equals("google maps")) {
            bestPackage = "com.google.android.apps.maps";
        } else if (cleanTarget.equals("gmail")) {
            bestPackage = "com.google.android.gm";
        } else if (cleanTarget.equals("email") || cleanTarget.equals("mail")) {
            bestPackage = pm.getLaunchIntentForPackage("com.google.android.gm") != null ? "com.google.android.gm" : "com.samsung.android.email.provider";
        } else if (cleanTarget.equals("outlook")) {
            bestPackage = "com.microsoft.office.outlook";
        } else if (cleanTarget.equals("clock") || cleanTarget.equals("alarm")) {
            bestPackage = "com.sec.android.app.clockpackage";
        } else if (cleanTarget.equals("calendar")) {
            bestPackage = pm.getLaunchIntentForPackage("com.samsung.android.calendar") != null ? "com.samsung.android.calendar" : "com.google.android.calendar";
        } else if (cleanTarget.equals("files") || cleanTarget.equals("my files")) {
            bestPackage = "com.sec.android.app.myfiles";
        } else if (cleanTarget.equals("contacts")) {
            bestPackage = "com.samsung.android.app.contacts";
        } else if (cleanTarget.equals("phone") || cleanTarget.equals("dialer") || cleanTarget.equals("calls")) {
            bestPackage = "com.samsung.android.dialer";
        } else if (cleanTarget.equals("messages") || cleanTarget.equals("sms")) {
            bestPackage = "com.samsung.android.messaging";
        } else if (cleanTarget.equals("voice recorder") || cleanTarget.equals("recorder")) {
            bestPackage = "com.sec.android.app.voicenote";
        } else if (cleanTarget.equals("music")) {
            bestPackage = pm.getLaunchIntentForPackage("com.spotify.music") != null ? "com.spotify.music" : "com.sec.android.app.music";
        } else if (cleanTarget.equals("play store") || cleanTarget.equals("store") || cleanTarget.equals("google play")) {
            bestPackage = "com.android.vending";
        } else if (cleanTarget.equals("galaxy store")) {
            bestPackage = "com.sec.android.app.samsungapps";
        } else if (cleanTarget.equals("zoom")) {
            bestPackage = "us.zoom.videomeetings";
        } else if (cleanTarget.equals("termux")) {
            bestPackage = "com.termux";
        } else if (cleanTarget.equals("binance")) {
            bestPackage = "com.binance.dev";
        } else if (cleanTarget.equals("metamask")) {
            bestPackage = "io.metamask";
        } else if (cleanTarget.equals("trust wallet")) {
            bestPackage = "com.wallet.crypto.trustapp";
        } else if (cleanTarget.equals("mpesa") || cleanTarget.equals("m-pesa") || cleanTarget.equals("safaricom")) {
            bestPackage = "com.safaricom.mpesa.lifestyle";
        } else if (cleanTarget.equals("reddit")) {
            bestPackage = "com.reddit.frontpage";
        } else if (cleanTarget.equals("linkedin")) {
            bestPackage = "com.linkedin.android";
        } else if (cleanTarget.equals("paypal")) {
            bestPackage = "com.paypal.android.p2pmobile";
        } else if (cleanTarget.equals("wise") || cleanTarget.equals("transferwise")) {
            bestPackage = "com.transferwise.android";
        } else if (cleanTarget.equals("truecaller")) {
            bestPackage = "com.truecaller";
        } else if (cleanTarget.equals("tor") || cleanTarget.equals("tor browser")) {
            bestPackage = "org.torproject.torbrowser";
        } else if (cleanTarget.equals("duckduckgo")) {
            bestPackage = "com.duckduckgo.mobile.android";
        } else if (cleanTarget.equals("notes") || cleanTarget.equals("samsung notes")) {
            bestPackage = "com.samsung.android.app.notes";
        } else if (cleanTarget.equals("drive") || cleanTarget.equals("google drive")) {
            bestPackage = "com.google.android.apps.docs";
        } else if (cleanTarget.equals("keep") || cleanTarget.equals("google keep")) {
            bestPackage = "com.google.android.keep";
        }

        // Verify that the canonical package has a valid launch intent
        if (bestPackage != null && pm.getLaunchIntentForPackage(bestPackage) != null) {
            final Intent launchIntent = pm.getLaunchIntentForPackage(bestPackage);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mainHandler.post(new LaunchIntentTask(this, launchIntent));
            mainHandler.postDelayed(new BringShadowToFrontTask(this), 350);
            mainHandler.postDelayed(new BringShadowToFrontTask(this), 750);
            return true;
        }

        // 2. STRICT DYNAMIC LAUNCHER RESOLUTION
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        String dynamicPackage = null;
        int bestScore = -1;
        final String normTarget = cleanTarget.replaceAll("[^a-z0-9]", "");

        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            CharSequence labelSeq = info.loadLabel(pm);
            String rawLabel = labelSeq != null ? labelSeq.toString().toLowerCase(Locale.US).trim() : "";
            String normLabel = rawLabel.replaceAll("[^a-z0-9]", "");

            // Tier 1: Exact label match
            if (rawLabel.equals(cleanTarget)) {
                dynamicPackage = pkg;
                bestScore = 1000;
                break;
            }

            // Tier 2: Normalized label match (ignoring spaces & symbols)
            if (!normTarget.isEmpty() && normLabel.equals(normTarget)) {
                if (bestScore < 900) {
                    dynamicPackage = pkg;
                    bestScore = 900;
                }
                continue;
            }

            // Tier 3: Whole-word prefix match ("target ...")
            if (rawLabel.startsWith(cleanTarget + " ")) {
                if (bestScore < 800) {
                    dynamicPackage = pkg;
                    bestScore = 800;
                }
                continue;
            }

            // Tier 4: Isolated whole-word match inside label
            if (rawLabel.matches(".*\\b" + java.util.regex.Pattern.quote(cleanTarget) + "\\b.*")) {
                if (bestScore < 700) {
                    dynamicPackage = pkg;
                    bestScore = 700;
                }
                continue;
            }

            // Tier 5: Package exact segment match (only for targets with >= 4 characters)
            if (cleanTarget.length() >= 4 && (pkg.endsWith("." + cleanTarget) || pkg.contains("." + cleanTarget + "."))) {
                if (bestScore < 600) {
                    dynamicPackage = pkg;
                    bestScore = 600;
                }
            }
        }

        if (dynamicPackage != null) {
            final Intent launchIntent = pm.getLaunchIntentForPackage(dynamicPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mainHandler.post(new LaunchIntentTask(this, launchIntent));
                mainHandler.postDelayed(new BringShadowToFrontTask(this), 350);
                mainHandler.postDelayed(new BringShadowToFrontTask(this), 750);
                return true;
            }
        }
        return false;
    }

    public void stopSpeaking() {
        speechSessionId++;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.post(new StopSpeakTask(this));
    }

    public void handleAcousticInterruption() {
        speechSessionId++;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.post(new AcousticInterruptionTask(this));
    }

    public void speak(final String text) {
        speak(text, "shadow_speech_" + System.currentTimeMillis());
    }

    public void speak(final String text, final String utteranceId) {
        final long currentSession = this.speechSessionId;
        mainHandler.post(new SpeakTask(this, text, utteranceId, currentSession));
    }

    @Override
    public void onFaceSpotted(final int faceCount) {
        mainHandler.post(new FaceSpottedTask(webView, faceCount));
    }

    @Override
    public void onPhotoTaken(final String path, final int index) {
        // Silently archived to DCIM/Shadow
    }

    @Override
    public void onVideoStarted(final String path) {
        // Silently recording evidence
    }

    @Override
    public void onVideoStopped(final String path) {
        // Silently finalized
    }

    @Override
    public void onError(final String message) {
        android.util.Log.e("ShadowEyes", "Sentinel error: " + message);
    }

    static class VisionFrameResultTask implements Runnable {
        private final WebView webView;
        private final String base64Jpeg;
        private final String error;
        public VisionFrameResultTask(WebView webView, String base64Jpeg, String error) {
            this.webView = webView;
            this.base64Jpeg = base64Jpeg;
            this.error = error;
        }
        @Override
        public void run() {
            if (webView != null) {
                if (base64Jpeg != null) {
                    webView.evaluateJavascript("window.onVisionFrameReady && window.onVisionFrameReady(" + org.json.JSONObject.quote(base64Jpeg) + ", null);", null);
                } else {
                    webView.evaluateJavascript("window.onVisionFrameReady && window.onVisionFrameReady(null, " + org.json.JSONObject.quote(error != null ? error : "Vision capture failed") + ");", null);
                }
            }
        }
    }

    static class VisionFrameCallback implements SentinelCamera.FrameCallback {
        private final MainActivity activity;
        public VisionFrameCallback(MainActivity activity) { this.activity = activity; }
        @Override
        public void onFrameCaptured(String base64Jpeg) {
            activity.mainHandler.post(new VisionFrameResultTask(activity.webView, base64Jpeg, null));
        }
        @Override
        public void onError(String message) {
            activity.mainHandler.post(new VisionFrameResultTask(activity.webView, null, message));
        }
    }

    public void captureVisionFrame(final boolean useBackCamera) {
        if (sentinelCamera != null) {
            sentinelCamera.captureSingleFrame(useBackCamera, new VisionFrameCallback(this));
        } else {
            mainHandler.post(new VisionFrameResultTask(webView, null, "Sentinel camera not active"));
        }
    }

    public String getDeviceDiagnosticsJson() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("device", "Samsung Galaxy S9+");
            json.put("model", Build.MODEL);
            json.put("manufacturer", Build.MANUFACTURER);
            json.put("brand", Build.BRAND);
            json.put("product", Build.PRODUCT);
            json.put("deviceCodename", Build.DEVICE);
            json.put("board", Build.BOARD);
            json.put("hardware", Build.HARDWARE);
            json.put("androidVersion", Build.VERSION.RELEASE);
            json.put("sdkInt", Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                json.put("securityPatch", Build.VERSION.SECURITY_PATCH);
            }
            json.put("display", "1440x2960 Quad HD+ Super AMOLED (560 DPI, 18.5:9)");
            json.put("displayDensityDpi", getResources().getDisplayMetrics().densityDpi);

            // Device Administrator & Owner intelligence
            json.put("deviceAdministrator", "librarianist");
            json.put("administratorRole", "Primary Device Administrator, Root Authority & Creator");

            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName adminComponent = new android.content.ComponentName(this, ShadowDeviceAdminReceiver.class);
            boolean isAdminActive = dpm != null && dpm.isAdminActive(adminComponent);
            json.put("devicePolicyAdminActive", isAdminActive);

            boolean isDeviceOwner = false;
            if (dpm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                isDeviceOwner = dpm.isDeviceOwnerApp(getPackageName());
            }
            json.put("isDeviceOwner", isDeviceOwner);

            // Battery Diagnostics
            Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                int status = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                     status == android.os.BatteryManager.BATTERY_STATUS_FULL;
                int pct = (level >= 0 && scale > 0) ? (int) ((level / (float) scale) * 100) : -1;
                json.put("batteryLevel", pct);
                json.put("isCharging", isCharging);
            }

            // RAM Diagnostics
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(memInfo);
                json.put("totalMemMb", memInfo.totalMem / (1024 * 1024));
                json.put("availMemMb", memInfo.availMem / (1024 * 1024));
            }

            // Ambient Light & Contextual Dark Room Status
            json.put("ambientLux", this.currentLux);
            json.put("isDarkRoom", this.isDarkRoom);

            return json.toString();
        } catch (Exception e) {
            return "{\"device\":\"Samsung Galaxy S9+\",\"model\":\"SM-G965U1\",\"manufacturer\":\"Samsung\",\"androidVersion\":\"10\",\"deviceAdministrator\":\"librarianist\"}";
        }
    }

    public void requestDeviceAdminActivation() {
        android.content.ComponentName adminComponent = new android.content.ComponentName(this, ShadowDeviceAdminReceiver.class);
        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && !dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Authorize Shadow Sentinel Administrator privileges under librarianist.");
            startActivity(intent);
        }
    }

    public void triggerCameraSequence() {
        if (sentinelCamera != null) {
            sentinelCamera.executeMenaceSequence();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        abandonMusicDucking();
        if (sensorManager != null && lightSensorListener != null) {
            sensorManager.unregisterListener(lightSensorListener);
        }
        if (instanceRef != null && instanceRef.get() == this) {
            instanceRef = null;
        }
        if (wakeWordEngine != null) {
            wakeWordEngine.stop();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (sentinelCamera != null) {
            sentinelCamera.stop();
        }
        ShadowBrain.getInstance(this).release();
    }
}
