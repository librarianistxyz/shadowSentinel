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
        return activity != null && (activity.isNativeSpeaking || (activity.textToSpeech != null && activity.textToSpeech.isSpeaking()));
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
    public String getSystemStatus() {
        return "ONLINE - AMOLED 0-NIT SENTINEL ACTIVE";
    }
}

