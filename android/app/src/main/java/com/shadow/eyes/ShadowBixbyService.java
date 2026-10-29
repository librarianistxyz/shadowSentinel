package com.shadow.eyes;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * System-Wide Bixby Hardware Button Hijacker for Samsung Galaxy S9+ (SM-G965U1).
 * Intercepts KEYCODE_VOICE_ASSIST (238) across the entire OS.
 * Completely suppresses Samsung Bixby and routes key events to Shadow:
 * - Single-press / Hold: Hardware Push-To-Talk (PTT)
 * - Rapid Double-press (<450ms): Instant Hardware Panic Switch
 */
public class ShadowBixbyService extends AccessibilityService {
    private static final String TAG = "ShadowBixbyService";
    public static final int KEYCODE_BIXBY = 238; // KeyEvent.KEYCODE_VOICE_ASSIST

    private static ShadowBixbyService instance;
    private long lastPressTime = 0;
    private long lastDownTime = 0;
    private Vibrator vibrator;

    public static ShadowBixbyService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        Log.i(TAG, "Shadow Bixby Hardware Hijack Service initialized.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KEYCODE_BIXBY || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_ASSIST) {
            handleBixbyKeyEvent(event);
            return true; // Consume event completely so Samsung Knox/Bixby never sees it
        }
        return super.onKeyEvent(event);
    }

    private void handleBixbyKeyEvent(KeyEvent event) {
        int action = event.getAction();
        long now = System.currentTimeMillis();

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                lastDownTime = now;
                boolean isDoublePress = (now - lastPressTime < 450);
                lastPressTime = now;

                if (isDoublePress) {
                    Log.w(TAG, "[PANIC SWITCH] Double-click detected on Bixby button!");
                    triggerHapticPanic();
                    MainActivity main = MainActivity.getInstance();
                    if (main != null) {
                        main.triggerPanicSwitch();
                    } else {
                        launchMainActivity(true);
                    }
                } else {
                    Log.i(TAG, "[BIXBY PTT] Key down detected");
                    triggerHapticClick(35);
                    MainActivity main = MainActivity.getInstance();
                    if (main != null) {
                        main.handlePttPress(true);
                    } else {
                        launchMainActivity(false);
                    }
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            long holdDuration = now - lastDownTime;
            Log.i(TAG, "[BIXBY PTT] Key up detected (hold: " + holdDuration + "ms)");
            triggerHapticClick(15);
            MainActivity main = MainActivity.getInstance();
            if (main != null) {
                main.handlePttPress(false);
            }
        }
    }

    private void triggerHapticClick(int durationMs) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
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
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    private void launchMainActivity(boolean panicMode) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (panicMode) {
                intent.putExtra("ACTION_PANIC", true);
            } else {
                intent.putExtra("ACTION_PTT", true);
            }
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch MainActivity from Bixby trigger", e);
        }
    }
}

