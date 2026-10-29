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
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
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
    final Handler mainHandler = new Handler(Looper.getMainLooper());

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

    public void notifyNativeSpeechState(final boolean speaking) {
        mainHandler.post(new SpeechStateTask(webView, speaking));
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
            if (activity == null || speechText == null || speechText.trim().isEmpty()) return;

            long now = System.currentTimeMillis();
            boolean isSpeaking = activity.isNativeSpeaking || (activity.textToSpeech != null && activity.textToSpeech.isSpeaking());
            if (isSpeaking) {
                android.util.Log.i("ShadowEyes", "Acoustic shield: Dropped speech recognized while TTS is speaking: '" + speechText + "'");
                return;
            }

            if (now - activity.lastTtsEndTime < 850) {
                android.util.Log.i("ShadowEyes", "Acoustic shield: Dropped speech recognized within 850ms cooldown (" + (now - activity.lastTtsEndTime) + "ms): '" + speechText + "'");
                return;
            }

            // Substring / Echo suppression against last spoken sentence
            String cleanRecognized = speechText.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase(java.util.Locale.ROOT).trim();
            String cleanSpoken = activity.lastSpokenSentence.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase(java.util.Locale.ROOT).trim();
            if (!cleanRecognized.isEmpty() && !cleanSpoken.isEmpty()) {
                if (cleanSpoken.contains(cleanRecognized) || cleanRecognized.contains(cleanSpoken)) {
                    android.util.Log.i("ShadowEyes", "Acoustic shield: Dropped recognized audio matching Shadow's own spoken sentence ('" + cleanRecognized + "' in '" + cleanSpoken + "')");
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
                        android.util.Log.i("ShadowEyes", "Acoustic shield: Dropped recognized audio with high word overlap (" + matchCount + "/" + words.length + ") against spoken sentence");
                        return;
                    }
                }
            }

            try {
                PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "ShadowEyes:VoiceInput");
                    wl.acquire(3000);
                }
            } catch (Exception ignored) {}

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

    static class ShadowUtteranceListener extends android.speech.tts.UtteranceProgressListener {
        private final MainActivity activity;
        public ShadowUtteranceListener(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void onStart(String utteranceId) {
            activity.isNativeSpeaking = true;
            activity.notifyNativeSpeechState(true);
            if (activity.wakeWordEngine != null) {
                activity.wakeWordEngine.setInterruptionOnlyMode(true);
            }
        }
        @Override
        public void onDone(String utteranceId) {
            activity.activeUtterances--;
            boolean stillSpeaking = activity.textToSpeech != null && activity.textToSpeech.isSpeaking();
            if (activity.activeUtterances <= 0 || !stillSpeaking) {
                activity.activeUtterances = 0;
                activity.isNativeSpeaking = false;
                activity.lastTtsEndTime = System.currentTimeMillis();
                activity.notifyNativeSpeechState(false);
                if (activity.wakeWordEngine != null) {
                    activity.wakeWordEngine.notifyTtsFinished();
                    activity.wakeWordEngine.resume();
                }
            }
        }
        @Override
        public void onError(String utteranceId) {
            activity.activeUtterances--;
            boolean stillSpeaking = activity.textToSpeech != null && activity.textToSpeech.isSpeaking();
            if (activity.activeUtterances <= 0 || !stillSpeaking) {
                activity.activeUtterances = 0;
                activity.isNativeSpeaking = false;
                activity.lastTtsEndTime = System.currentTimeMillis();
                activity.notifyNativeSpeechState(false);
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

                // Intelligently select the highest-fidelity, smoothest sweet voice
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        java.util.Set<android.speech.tts.Voice> voices = activity.textToSpeech.getVoices();
                        if (voices != null && !voices.isEmpty()) {
                            android.speech.tts.Voice bestVoice = null;
                            int bestScore = -1;

                            for (android.speech.tts.Voice v : voices) {
                                Locale loc = v.getLocale();
                                if (loc == null || !loc.getLanguage().equals("en")) continue;

                                int score = 0;
                                String name = v.getName().toLowerCase(Locale.US);

                                // Prefer US or GB English
                                if (loc.getCountry().equalsIgnoreCase("US")) {
                                    score += 150;
                                } else if (loc.getCountry().equalsIgnoreCase("GB")) {
                                    score += 100;
                                }

                                // Prioritize studio-grade high fidelity quality
                                if (v.getQuality() == android.speech.tts.Voice.QUALITY_VERY_HIGH) {
                                    score += 350;
                                } else if (v.getQuality() == android.speech.tts.Voice.QUALITY_HIGH) {
                                    score += 250;
                                } else if (v.getQuality() == android.speech.tts.Voice.QUALITY_NORMAL) {
                                    score += 50;
                                }

                                // Prioritize melodious, warm, natural female voices
                                if (name.contains("female") || name.contains("sfg") || name.contains("tpd") || name.contains("iol") || name.contains("wavenet") || name.contains("neural")) {
                                    score += 300;
                                }

                                // Prefer offline local voices for zero network stutter
                                if (!v.isNetworkConnectionRequired()) {
                                    score += 100;
                                }

                                if (score > bestScore) {
                                    bestScore = score;
                                    bestVoice = v;
                                }
                            }

                            if (bestVoice != null) {
                                android.util.Log.i("ShadowEyes", "[VOICE] Selected sweet high-fidelity voice: " + bestVoice.getName() + " (Score: " + bestScore + ")");
                                activity.textToSpeech.setVoice(bestVoice);
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.w("ShadowEyes", "Could not enumerate voices: " + e.getMessage());
                    }
                }

                // Smooth sweet melodious pitch and human conversational cadence
                activity.textToSpeech.setPitch(1.04f);
                activity.textToSpeech.setSpeechRate(0.98f);
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

        webView = new WebView(this);
        setContentView(webView);
        configureWebView();

        sentinelCamera = new SentinelCamera(this, this);
        wakeWordEngine = new WakeWordEngine(this, new ShadowWakeWordListener(this));

        IntentFilter promptFilter = new IntentFilter("com.shadow.eyes.TEST_PROMPT");
        promptFilter.addAction("com.shadow.eyes.INTERRUPT");
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
        if (wakeWordEngine != null) {
            wakeWordEngine.pause();
        }
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

    static class SpeakTask implements Runnable {
        private final MainActivity activity;
        private final String text;
        private final long sessionId;

        public SpeakTask(MainActivity activity, String text, long sessionId) {
            this.activity = activity;
            this.text = text;
            this.sessionId = sessionId;
        }

        private static String smoothenText(String raw) {
            if (raw == null) return "";
            return raw.trim()
                .replaceAll("(?i)^(?:(?:hey|hi|hello)\\s+(?:shadow|there)?|hello[!,.]?|hi[!,.]?|hey[!,.]?)\\s*", "")
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
                    if (activity.activeUtterances <= 0 || activity.lastSpokenSentence == null || activity.lastSpokenSentence.isEmpty()) {
                        activity.lastSpokenSentence = clean;
                    } else {
                        activity.lastSpokenSentence = activity.lastSpokenSentence + " " + clean;
                    }
                    activity.activeUtterances++;
                    activity.isNativeSpeaking = true;
                    activity.notifyNativeSpeechState(true);
                    if (activity.wakeWordEngine != null) {
                        activity.wakeWordEngine.setInterruptionOnlyMode(true);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Bundle ttsParams = new Bundle();
                        ttsParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.90f);
                        activity.textToSpeech.speak(clean, TextToSpeech.QUEUE_ADD, ttsParams, "shadow_speech_" + System.currentTimeMillis());
                    } else {
                        activity.textToSpeech.speak(clean, TextToSpeech.QUEUE_ADD, null, "shadow_speech_" + System.currentTimeMillis());
                    }
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
            activity.speechSessionId++;
            if (activity.textToSpeech != null) {
                activity.textToSpeech.stop();
            }
            activity.activeUtterances = 0;
            activity.isNativeSpeaking = false;
            activity.lastTtsEndTime = System.currentTimeMillis();
            activity.notifyNativeSpeechState(false);
            if (activity.wakeWordEngine != null) {
                activity.wakeWordEngine.setInterruptionOnlyMode(false);
                activity.wakeWordEngine.pause();
                activity.mainHandler.postDelayed(new ResumeWakeWordTask(activity.wakeWordEngine), 600);
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
        final long currentSession = this.speechSessionId;
        mainHandler.post(new SpeakTask(this, text, currentSession));
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
    }
}
