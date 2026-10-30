package com.shadow.eyes;

import android.webkit.JavascriptInterface;

public class ShadowBridge {
    private final MainActivity activity;

    public ShadowBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void triggerSurveillance() {
        if (activity != null) {
            activity.triggerCameraSequence();
        }
    }

    @JavascriptInterface
    public void speakNative(String text) {
        if (activity != null) {
            activity.speak(text, "shadow_speech_" + System.currentTimeMillis());
        }
    }

    @JavascriptInterface
    public void speakNative(String text, String utteranceId) {
        if (activity != null) {
            activity.speak(text, utteranceId != null && !utteranceId.isEmpty() ? utteranceId : "shadow_speech_" + System.currentTimeMillis());
        }
    }

    @JavascriptInterface
    public void setVoiceGender(String gender) {
        if (activity != null) {
            activity.setVoiceGender(gender);
        }
    }

    @JavascriptInterface
    public void setVoiceEmotion(String emotion) {
        if (activity != null) {
            activity.setVoiceEmotion(emotion);
        }
    }

    @JavascriptInterface
    public String getVoiceGender() {
        if (activity != null) {
            return activity.currentVoiceGender;
        }
        return "male";
    }

    @JavascriptInterface
    public void stopSpeaking() {
        if (activity != null) {
            activity.stopSpeaking();
        }
    }

    @JavascriptInterface
    public boolean isSpeakingNative() {
        if (activity == null) return false;
        boolean ttsActive = activity.textToSpeech != null && activity.textToSpeech.isSpeaking();
        if (!ttsActive && activity.activeUtterances <= 0) {
            activity.isNativeSpeaking = false;
        }
        return activity.isNativeSpeaking || ttsActive;
    }

    @JavascriptInterface
    public void captureVisionFrame(final boolean useBackCamera) {
        if (activity != null) {
            activity.captureVisionFrame(useBackCamera);
        }
    }

    @JavascriptInterface
    public boolean launchApp(String appName) {
        if (activity != null) {
            return activity.launchAppByName(appName);
        }
        return false;
    }

    @JavascriptInterface
    public boolean executeAction(String actionType, String payload) {
        if (activity != null) {
            return activity.executeDeviceAction(actionType, payload != null ? payload : "");
        }
        return false;
    }

    @JavascriptInterface
    public String getDeviceDiagnosticsJson() {
        if (activity != null) {
            return activity.getDeviceDiagnosticsJson();
        }
        return "{}";
    }

    @JavascriptInterface
    public void requestDeviceAdminActivation() {
        if (activity != null) {
            activity.requestDeviceAdminActivation();
        }
    }

    @JavascriptInterface
    public boolean isWakeWordActive() {
        if (activity != null && activity.wakeWordEngine != null) {
            return activity.wakeWordEngine.isListening();
        }
        return false;
    }

    @JavascriptInterface
    public void pauseWakeWord() {
        if (activity != null && activity.wakeWordEngine != null) {
            activity.wakeWordEngine.pause();
        }
    }

    @JavascriptInterface
    public void resumeWakeWord() {
        if (activity != null && activity.wakeWordEngine != null) {
            activity.wakeWordEngine.resume();
        }
    }

    @JavascriptInterface
    public void setFollowUpMode(boolean active) {
        if (activity != null && activity.wakeWordEngine != null) {
            activity.wakeWordEngine.setFollowUpMode(active);
        }
    }

    @JavascriptInterface
    public void playWakeChime() {
        if (activity != null && activity.wakeWordEngine != null) {
            activity.wakeWordEngine.playWakeChime();
        }
    }

    @JavascriptInterface
    public boolean isDarkRoom() {
        if (activity != null) {
            return activity.isDarkRoom;
        }
        return false;
    }

    @JavascriptInterface
    public float getAmbientLux() {
        if (activity != null) {
            return activity.currentLux;
        }
        return 50.0f;
    }

    @JavascriptInterface
    public void requestMusicDucking() {
        if (activity != null) {
            activity.requestMusicDucking();
        }
    }

    @JavascriptInterface
    public void abandonMusicDucking() {
        if (activity != null) {
            activity.abandonMusicDucking();
        }
    }

    @JavascriptInterface
    public void notifyTtsFinished() {
        if (activity != null) {
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

    static class BrainTokenTask implements Runnable {
        private final MainActivity activity;
        private final String token;
        public BrainTokenTask(MainActivity activity, String token) {
            this.activity = activity;
            this.token = token;
        }
        @Override
        public void run() {
            if (activity != null && activity.webView != null) {
                activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onBrainToken && window.ShadowMouth.onBrainToken(" + org.json.JSONObject.quote(token) + ");", null);
            }
        }
    }

    static class BrainCompleteTask implements Runnable {
        private final MainActivity activity;
        private final String fullResponse;
        public BrainCompleteTask(MainActivity activity, String fullResponse) {
            this.activity = activity;
            this.fullResponse = fullResponse;
        }
        @Override
        public void run() {
            if (activity != null && activity.webView != null) {
                activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onBrainComplete && window.ShadowMouth.onBrainComplete(" + org.json.JSONObject.quote(fullResponse) + ");", null);
            }
        }
    }

    static class BrainErrorTask implements Runnable {
        private final MainActivity activity;
        private final String error;
        public BrainErrorTask(MainActivity activity, String error) {
            this.activity = activity;
            this.error = error;
        }
        @Override
        public void run() {
            if (activity != null && activity.webView != null) {
                activity.webView.evaluateJavascript("window.ShadowMouth && window.ShadowMouth.onBrainError && window.ShadowMouth.onBrainError(" + org.json.JSONObject.quote(error) + ");", null);
            }
        }
    }

    static class LocalBrainCallback implements ShadowBrain.BrainCallback {
        private final MainActivity activity;
        public LocalBrainCallback(MainActivity activity) {
            this.activity = activity;
        }
        @Override
        public void onToken(String token) {
            if (activity != null) {
                activity.runOnUiThread(new BrainTokenTask(activity, token));
            }
        }
        @Override
        public void onComplete(String fullResponse) {
            if (activity != null) {
                activity.runOnUiThread(new BrainCompleteTask(activity, fullResponse));
            }
        }
        @Override
        public void onError(String error) {
            if (activity != null) {
                activity.runOnUiThread(new BrainErrorTask(activity, error));
            }
        }
    }

    @JavascriptInterface
    public boolean isBrainLoaded() {
        if (activity == null) return false;
        return ShadowBrain.getInstance(activity).isModelLoaded();
    }

    @JavascriptInterface
    public boolean isBrainNativeAvailable() {
        if (activity == null) return false;
        return ShadowBrain.getInstance(activity).isNativeLoaded();
    }

    @JavascriptInterface
    public void initBrain() {
        if (activity == null) return;
        ShadowBrain.getInstance(activity).initAsync(null);
    }

    @JavascriptInterface
    public void syncDailyDigest() {
        if (activity != null) {
            ShadowBrain.getInstance(activity).syncDailyDigestAsync(null, null);
        }
    }

    @JavascriptInterface
    public String getDailyDigestStatus() {
        if (activity == null) return "{}";
        ShadowBrain brain = ShadowBrain.getInstance(activity);
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("date", brain.getDailyDigestDate());
            obj.put("bulletCount", brain.getDailyDigestBulletCount());
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void generateLocal(final String prompt) {
        if (activity == null || prompt == null || prompt.trim().isEmpty()) return;
        ShadowBrain brain = ShadowBrain.getInstance(activity);
        if (!brain.isModelLoaded()) {
            android.util.Log.w("ShadowBridge", "generateLocal called but ShadowBrain is not loaded");
            return;
        }
        brain.generateAsync(prompt, 256, new LocalBrainCallback(activity));
    }

    @JavascriptInterface
    public void stopBrain() {
        if (activity != null) {
            ShadowBrain.getInstance(activity).stop();
        }
    }

    @JavascriptInterface
    public String getSystemStatus() {
        return "ONLINE - AMOLED 0-NIT SENTINEL ACTIVE";
    }
}
