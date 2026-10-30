#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <algorithm>
#include "llama.h"

#define TAG "ShadowBrain"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_batch_ext * g_batch = nullptr;
static llama_sampler * g_sampler = nullptr;
static std::atomic<bool> g_stop{false};
static std::atomic<bool> g_is_generating{false};
static int g_n_ctx = 2048;

static void batch_set_tokens(llama_batch_ext * batch, const llama_token * tokens, int32_t n_tokens, llama_pos pos_0) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < n_tokens; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = pos_0 + i;
        llama_batch_ext_set_pos(batch, idx, &pos);
    }
    llama_batch_ext_set_output_logits(batch, n_tokens - 1, true);
}

static void release_internal() {
    if (g_batch) {
        llama_batch_ext_free(g_batch);
        g_batch = nullptr;
    }
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_shadow_eyes_ShadowBrain_nativeInit(
    JNIEnv * env,
    jobject /* thiz */,
    jstring jmodel_path,
    jint n_threads,
    jint n_ctx) {

    std::lock_guard<std::mutex> lock(g_mutex);
    release_internal();

    const char * model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    if (!model_path) {
        LOGE("Invalid model path string");
        return JNI_FALSE;
    }

    LOGI("Initializing llama backend...");
    llama_backend_init();

    LOGI("Loading GGUF model from: %s", model_path);
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Snapdragon Kryo 385 CPU optimized

    g_model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);

    if (!g_model) {
        LOGE("Failed to load GGUF model");
        return JNI_FALSE;
    }

    g_n_ctx = (n_ctx > 0) ? n_ctx : 2048;
    int threads = (n_threads > 0) ? n_threads : 4;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = g_n_ctx;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    ctx_params.no_perf = true;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to initialize llama_context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_batch = llama_batch_ext_init(g_ctx);
    if (!g_batch) {
        LOGE("Failed to initialize llama_batch_ext");
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    auto sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(1337));

    LOGI("ShadowBrain native engine initialized successfully with context %d and %d threads!", g_n_ctx, threads);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_shadow_eyes_ShadowBrain_nativeIsLoaded(
    JNIEnv * /* env */,
    jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_shadow_eyes_ShadowBrain_nativeStop(
    JNIEnv * /* env */,
    jobject /* thiz */) {
    LOGI("Stop requested for generation");
    g_stop.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_shadow_eyes_ShadowBrain_nativeGenerate(
    JNIEnv * env,
    jobject /* thiz */,
    jstring jprompt,
    jint max_predict,
    jobject jcallback) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model || !g_ctx || !g_batch || !g_sampler) {
        LOGE("nativeGenerate called but engine is not loaded");
        if (jcallback) {
            jclass cbClass = env->GetObjectClass(jcallback);
            jmethodID onErrorMid = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
            if (onErrorMid) {
                jstring errStr = env->NewStringUTF("Model not initialized");
                env->CallVoidMethod(jcallback, onErrorMid, errStr);
                env->DeleteLocalRef(errStr);
            }
        }
        return env->NewStringUTF("");
    }

    const char * prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt_cstr) {
        return env->NewStringUTF("");
    }
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);

    // Reset KV cache and sampler
    llama_memory_clear(llama_get_memory(g_ctx), false);
    llama_sampler_reset(g_sampler);
    g_stop.store(false);
    g_is_generating.store(true);

    // Tokenize prompt
    int n_prompt = -llama_tokenize(vocab, prompt.c_str(), prompt.length(), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        n_prompt = 1;
    }
    std::vector<llama_token> prompt_tokens(n_prompt);
    int tokenized = llama_tokenize(vocab, prompt.c_str(), prompt.length(), prompt_tokens.data(), prompt_tokens.size(), true, true);
    if (tokenized < 0) {
        LOGE("Failed to tokenize prompt");
        return env->NewStringUTF("");
    }
    prompt_tokens.resize(tokenized);
    n_prompt = prompt_tokens.size();

    LOGI("Prompt tokenized into %d tokens", n_prompt);

    int max_tokens = (max_predict > 0) ? max_predict : 256;
    if (n_prompt + max_tokens > g_n_ctx) {
        max_tokens = std::max(16, g_n_ctx - n_prompt - 4);
    }

    // Set callback method IDs
    jclass cbClass = nullptr;
    jmethodID onTokenMid = nullptr;
    jmethodID onCompleteMid = nullptr;
    if (jcallback) {
        cbClass = env->GetObjectClass(jcallback);
        onTokenMid = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
        onCompleteMid = env->GetMethodID(cbClass, "onComplete", "(Ljava/lang/String;)V");
    }

    batch_set_tokens(g_batch, prompt_tokens.data(), n_prompt, 0);

    std::string full_response = "";
    int n_tokens = n_prompt;
    int n_decode = 0;

    for (int n_pos = 0; n_pos + n_tokens < n_prompt + max_tokens; ) {
        if (g_stop.load()) {
            LOGI("Generation stopped early at pos %d", n_pos);
            break;
        }

        if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, g_batch)) {
            LOGE("llama_process DECODE failed at pos %d", n_pos);
            break;
        }

        n_pos += n_tokens;

        llama_token new_token_id = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("Reached End-Of-Generation token");
            break;
        }

        char buf[128];
        int n_piece = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        std::string piece;
        if (n_piece > 0) {
            piece.assign(buf, n_piece);
        } else if (n_piece < 0) {
            // Buffer was too small; re-allocate
            std::vector<char> big_buf(-n_piece + 1);
            int n_p2 = llama_token_to_piece(vocab, new_token_id, big_buf.data(), big_buf.size(), 0, true);
            if (n_p2 > 0) {
                piece.assign(big_buf.data(), n_p2);
            }
        }

        if (!piece.empty()) {
            full_response += piece;
            if (jcallback && onTokenMid) {
                jstring jpiece = env->NewStringUTF(piece.c_str());
                env->CallVoidMethod(jcallback, onTokenMid, jpiece);
                env->DeleteLocalRef(jpiece);
            }
        }

        batch_set_tokens(g_batch, &new_token_id, 1, n_pos);
        n_tokens = 1;
        n_decode++;
    }

    g_is_generating.store(false);
    LOGI("Generation complete: %d tokens generated", n_decode);

    if (jcallback && onCompleteMid) {
        jstring jfull = env->NewStringUTF(full_response.c_str());
        env->CallVoidMethod(jcallback, onCompleteMid, jfull);
        env->DeleteLocalRef(jfull);
    }

    return env->NewStringUTF(full_response.c_str());
}

JNIEXPORT void JNICALL
Java_com_shadow_eyes_ShadowBrain_nativeRelease(
    JNIEnv * /* env */,
    jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    LOGI("Releasing native llama resources...");
    release_internal();
}

} // extern "C"
