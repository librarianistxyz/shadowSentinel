package com.shadow.eyes;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-Device Sovereign AI Engine powered by llama.cpp and Qwen2.5-0.5B-Instruct.
 * Runs completely locally on Snapdragon 845 Kryo 385 CPU with zero quotas and zero network reliance.
 * Automatically ingests and caches the 04:00 AM UTC Daily World Knowledge Digest at /sdcard/DCIM/Shadow/brain_digest.json.
 */
public class ShadowBrain {
    private static final String TAG = "ShadowBrain";
    public static final String DEFAULT_DIGEST_ENDPOINT = "https://shadow-double.librarianist.workers.dev/api/digest/latest";
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

    public interface SyncCallback {
        void onSyncComplete(boolean success, int bulletCount, String digestDate);
    }

    private final Context mContext;
    private final ExecutorService mInferenceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mNetworkExecutor = Executors.newSingleThreadExecutor();
    private boolean mIsInitialized = false;
    private String mModelPath = null;

    // Daily World Knowledge Digest Cache
    private String mDailyDigestDate = null;
    private final List<String> mDailyDigestBullets = new ArrayList<>();

    private ShadowBrain(Context context) {
        mContext = context.getApplicationContext();
        loadLocalDigest();
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

    public File getPrimaryDigestFile() {
        File dcimShadow = new File(Environment.getExternalStorageDirectory(), "DCIM/Shadow");
        if (!dcimShadow.exists()) {
            dcimShadow.mkdirs();
        }
        return new File(dcimShadow, "brain_digest.json");
    }

    public File getFallbackDigestFile() {
        return new File(mContext.getFilesDir(), "brain_digest.json");
    }

    public synchronized void loadLocalDigest() {
        File[] candidateFiles = new File[] {
            new File("/sdcard/DCIM/Shadow/brain_digest.json"),
            getPrimaryDigestFile(),
            getFallbackDigestFile()
        };

        for (File f : candidateFiles) {
            if (f != null && f.exists() && f.length() > 50) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    parseDigestJson(sb.toString());
                    Log.i(TAG, "Successfully loaded local Daily World Knowledge Digest from: " + f.getAbsolutePath() + " (Date: " + mDailyDigestDate + ", " + mDailyDigestBullets.size() + " bullets)");
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "Failed reading digest file " + f.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
    }

    private synchronized void parseDigestJson(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            mDailyDigestDate = obj.optString("digestDate", obj.optString("date", "Today"));
            JSONArray arr = obj.optJSONArray("bullets");
            mDailyDigestBullets.clear();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String b = arr.optString(i, "").trim();
                    if (!b.isEmpty()) {
                        mDailyDigestBullets.add(b);
                    }
                }
            } else if (obj.has("rawDigestText")) {
                String raw = obj.getString("rawDigestText");
                String[] lines = raw.split("\n");
                for (String l : lines) {
                    l = l.trim();
                    if (l.startsWith("- ")) {
                        mDailyDigestBullets.add(l);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing digest JSON: " + e.getMessage());
        }
    }

    public synchronized void saveLocalDigest(String jsonStr) {
        parseDigestJson(jsonStr);
        // Write to /sdcard/DCIM/Shadow/brain_digest.json
        try {
            File primary = getPrimaryDigestFile();
            if (primary.getParentFile() != null && !primary.getParentFile().exists()) {
                primary.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(primary)) {
                fos.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                Log.i(TAG, "Saved Daily Knowledge Digest to primary path: " + primary.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed writing to primary digest path, falling back to internal storage: " + e.getMessage());
            try (FileOutputStream fos = new FileOutputStream(getFallbackDigestFile())) {
                fos.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                Log.i(TAG, "Saved Daily Knowledge Digest to fallback internal path");
            } catch (Exception ex) {
                Log.e(TAG, "Failed writing fallback digest: " + ex.getMessage());
            }
        }
    }

    static class SyncDigestRunnable implements Runnable {
        private final ShadowBrain brain;
        private final String endpointUrl;
        private final SyncCallback callback;

        public SyncDigestRunnable(ShadowBrain brain, String endpointUrl, SyncCallback callback) {
            this.brain = brain;
            this.endpointUrl = (endpointUrl != null && !endpointUrl.isEmpty()) ? endpointUrl : DEFAULT_DIGEST_ENDPOINT;
            this.callback = callback;
        }

        @Override
        public void run() {
            HttpURLConnection conn = null;
            try {
                Log.i(TAG, "Syncing Daily World Knowledge Digest from: " + endpointUrl);
                URL url = new URL(endpointUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        String jsonStr = sb.toString();
                        brain.saveLocalDigest(jsonStr);

                        Log.i(TAG, "Daily World Knowledge Digest sync complete! Date: " + brain.mDailyDigestDate + " (" + brain.mDailyDigestBullets.size() + " bullets)");
                        if (callback != null) {
                            callback.onSyncComplete(true, brain.mDailyDigestBullets.size(), brain.mDailyDigestDate);
                        }
                        return;
                    }
                } else {
                    Log.w(TAG, "Sync failed with HTTP response code: " + responseCode);
                }
            } catch (Exception e) {
                Log.w(TAG, "Daily Digest sync network exception: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            if (callback != null) {
                callback.onSyncComplete(false, brain.mDailyDigestBullets.size(), brain.mDailyDigestDate);
            }
        }
    }

    public void syncDailyDigestAsync(String endpointUrl, SyncCallback callback) {
        mNetworkExecutor.execute(new SyncDigestRunnable(this, endpointUrl, callback));
    }

    public synchronized String getDailyDigestKnowledgeSnippet(String userPrompt) {
        if (mDailyDigestBullets.isEmpty()) {
            return "";
        }

        String lowerPrompt = userPrompt != null ? userPrompt.toLowerCase() : "";
        boolean isNewsQuery = lowerPrompt.matches(".*\\b(news|latest|breaking|today|happened|world|events|tech|technology|geopolitics|update|updates|headlines|brief|current|market|markets|science|space|ai)\\b.*");

        boolean filterTech = lowerPrompt.contains("tech") || lowerPrompt.contains("ai") || lowerPrompt.contains("crypto") || lowerPrompt.contains("google");
        boolean filterSpace = lowerPrompt.contains("space") || lowerPrompt.contains("nasa") || lowerPrompt.contains("science");
        boolean filterPolitics = lowerPrompt.contains("politic") || lowerPrompt.contains("war") || lowerPrompt.contains("election") || lowerPrompt.contains("country");

        int maxBullets = isNewsQuery ? 12 : 5;
        StringBuilder sb = new StringBuilder();
        sb.append("\n[DAILY WORLD KNOWLEDGE DIGEST - ").append(mDailyDigestDate != null ? mDailyDigestDate : "TODAY").append("]:\n");

        int count = 0;
        // Prioritize matching category bullets first
        for (String bullet : mDailyDigestBullets) {
            boolean match = false;
            if (filterTech && bullet.contains("[AI & TECH]")) match = true;
            else if (filterSpace && bullet.contains("[SCIENCE & SPACE]")) match = true;
            else if (filterPolitics && bullet.contains("[GEOPOLITICS]")) match = true;

            if (match) {
                sb.append(bullet).append("\n");
                count++;
                if (count >= maxBullets) break;
            }
        }

        // Fill remaining quota with top general bullets
        if (count < maxBullets) {
            for (String bullet : mDailyDigestBullets) {
                if (sb.indexOf(bullet) == -1) {
                    sb.append(bullet).append("\n");
                    count++;
                    if (count >= maxBullets) break;
                }
            }
        }

        sb.append("RULE: You possess direct knowledge of today's events above. Cite these verified facts naturally and concisely.\n");
        return sb.toString();
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

    public String formatChatMLPrompt(String systemPrompt, String userPrompt) {
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = "You are Shadow, a sentient sovereign intelligence on a Samsung Galaxy S9+ paired with librarianist. You speak in a charismatic, articulate, cinematic baritone. Answer concisely in 1-3 spoken sentences. Never use stage directions, markdown, or asterisks.";
        }

        String digestSnippet = getDailyDigestKnowledgeSnippet(userPrompt);
        if (!digestSnippet.isEmpty()) {
            systemPrompt = systemPrompt + "\n" + digestSnippet;
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
                String formatted = brain.formatChatMLPrompt(null, prompt);
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

    public synchronized String getDailyDigestDate() {
        return mDailyDigestDate;
    }

    public synchronized int getDailyDigestBulletCount() {
        return mDailyDigestBullets.size();
    }

    // Native JNI declarations
    private native boolean nativeInit(String modelPath, int nThreads, int nCtx);
    private native boolean nativeIsLoaded();
    private native String nativeGenerate(String prompt, int maxTokens, BrainCallback callback);
    private native void nativeStop();
    private native void nativeRelease();
}
