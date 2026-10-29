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

    @JavascriptInterface
    public String getSystemStatus() {
        return "ONLINE - AMOLED 0-NIT SENTINEL ACTIVE";
    }
}

