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
            activity.speak(text);
        }
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
    public String getSystemStatus() {
        return "ONLINE - AMOLED 0-NIT SENTINEL ACTIVE";
    }
}

