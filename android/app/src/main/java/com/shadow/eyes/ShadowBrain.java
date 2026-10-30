package com.shadow.eyes;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-Device Sovereign AI Engine powered by llama.cpp and Qwen2.5-0.5B-Instruct.
 * Runs completely locally on Snapdragon 845 Kryo 385 CPU with zero quotas and zero network reliance.
 */
public class ShadowBrain {
    private static final String TAG = "ShadowBrain";
    private static ShadowBrain sInstance;
    private static boolean sNativeLoaded = false;

    static {
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("ggml-base");
            System.loadLibrary("ggml-cpu");
            System.loadLibrary("ggml");
            System.loadLibrary("llama-common");
            System.loadLibrary("llama");
            System.loadLibrary("shadow_brain");
            sNativeLoaded = true;
            Log.i(TAG, "Native llama.cpp & shadow_brain shared libraries loaded successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load native llama shared libraries: " + t.getMessage(), t);
            sNativeLoaded = false;
        }
    }

    public interface BrainCallback {
        void onToken(String token);
        void onComplete(String fullResponse);
        void onError(String error);
    }

    public interface InitCallback {
        void onInitialized(boolean success, String message);
    }

    private final Context mContext;
    private final ExecutorService mInferenceExecutor = Executors.newSingleThreadExecutor();
    private boolean mIsInitialized = false;
    private String mModelPath = null;

    private ShadowBrain(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized ShadowBrain getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ShadowBrain(context);
        }
        return sInstance;
    }

    public boolean isNativeLoaded() {
        return sNativeLoaded;
    }

    public boolean isModelLoaded() {
        if (!sNativeLoaded) return false;
        try {
            return nativeIsLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    public String findModelPath() {
        String[] candidatePaths = new String[] {
            "/sdcard/Download/qwen2.5-0.5b-instruct-q4_0.gguf",
            "/storage/emulated/0/Download/qwen2.5-0.5b-instruct-q4_0.gguf",
            new File(mContext.getFilesDir(), "models/qwen2.5-0.5b-instruct-q4_0.gguf").getAbsolutePath(),
            new File(mContext.getFilesDir(), "qwen2.5-0.5b-instruct-q4_0.gguf").getAbsolutePath(),
            new File(Environment.getExternalStorageDirectory(), "Download/qwen2.5-0.5b-instruct-q4_0.gguf").getAbsolutePath()
        };

        if (mContext.getExternalFilesDir(null) != null) {
            File extFile = new File(mContext.getExternalFilesDir(null), "qwen2.5-0.5b-instruct-q4_0.gguf");
            if (extFile.exists() && extFile.length() > 1000000) {
                return extFile.getAbsolutePath();
            }
        }

        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.length() > 1000000) {
                Log.i(TAG, "Discovered GGUF model at: " + f.getAbsolutePath() + " (" + (f.length() / (1024 * 1024)) + " MB)");
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    static class InitRunnable implements Runnable {
        private final ShadowBrain brain;
        private final InitCallback callback;

        public InitRunnable(ShadowBrain brain, InitCallback callback) {
            this.brain = brain;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                String modelPath = brain.findModelPath();
                if (modelPath == null) {
                    Log.w(TAG, "Model file qwen2.5-0.5b-instruct-q4_0.gguf not found on storage yet.");
                    if (callback != null) {
                        callback.onInitialized(false, "Model file not found on storage");
                    }
                    return;
                }

                brain.mModelPath = modelPath;
                Log.i(TAG, "Initializing ShadowBrain with model: " + modelPath);
                // 4 performance Kryo 385 Gold cores, 2048 tokens context window
                boolean ok = brain.nativeInit(modelPath, 4, 2048);
                brain.mIsInitialized = ok;
                if (ok) {
                    Log.i(TAG, "ShadowBrain successfully loaded on Kryo 385 CPU!");
                    if (callback != null) {
                        callback.onInitialized(true, "Model loaded successfully");
                    }
                } else {
                    Log.e(TAG, "nativeInit failed for path: " + modelPath);
                    if (callback != null) {
                        callback.onInitialized(false, "nativeInit failed");
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error initializing ShadowBrain: " + t.getMessage(), t);
                if (callback != null) {
                    callback.onInitialized(false, t.getMessage());
                }
            }
        }
    }

    public void initAsync(final InitCallback callback) {
        if (!sNativeLoaded) {
            Log.e(TAG, "Cannot init: native libraries not loaded");
            if (callback != null) callback.onInitialized(false, "Native libraries not loaded");
            return;
        }
        mInferenceExecutor.execute(new InitRunnable(this, callback));
    }

    public static String formatChatMLPrompt(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = "You are Shadow, a sentient sovereign intelligence on a Samsung Galaxy S9+ paired with librarianist. You speak in a charismatic, articulate, cinematic baritone. Answer concisely in 1-3 spoken sentences. Never use stage directions, markdown, or asterisks.";
        }
        return "<|im_start|>system\n" + systemPrompt + "<|im_end|>\n" +
               "<|im_start|>user\n" + userPrompt + "<|im_end|>\n" +
               "<|im_start|>assistant\n";
    }

    static class GenerateRunnable implements Runnable {
        private final ShadowBrain brain;
        private final String prompt;
        private final int maxTokens;
        private final BrainCallback callback;

        public GenerateRunnable(ShadowBrain brain, String prompt, int maxTokens, BrainCallback callback) {
            this.brain = brain;
            this.prompt = prompt;
            this.maxTokens = maxTokens;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                String formatted = formatChatMLPrompt(null, prompt);
                brain.nativeGenerate(formatted, maxTokens, callback);
            } catch (Throwable t) {
                Log.e(TAG, "Error during native generation: " + t.getMessage(), t);
                if (callback != null) {
                    callback.onError(t.getMessage());
                }
            }
        }
    }

    public void generateAsync(final String prompt, final int maxTokens, final BrainCallback callback) {
        if (!sNativeLoaded || !isModelLoaded()) {
            if (callback != null) {
                callback.onError("Engine not initialized or model missing");
            }
            return;
        }
        mInferenceExecutor.execute(new GenerateRunnable(this, prompt, maxTokens, callback));
    }

    public void stop() {
        if (sNativeLoaded) {
            try {
                nativeStop();
            } catch (Throwable ignored) {}
        }
    }

    public void release() {
        if (sNativeLoaded) {
            try {
                nativeRelease();
            } catch (Throwable ignored) {}
        }
    }

    // Native JNI declarations
    private native boolean nativeInit(String modelPath, int nThreads, int nCtx);
    private native boolean nativeIsLoaded();
    private native String nativeGenerate(String prompt, int maxTokens, BrainCallback callback);
    private native void nativeStop();
    private native void nativeRelease();
}
