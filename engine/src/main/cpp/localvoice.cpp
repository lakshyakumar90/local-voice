#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <memory>
#include <string>
#include <algorithm>
#include <vector>
#include <limits>
#include "script_policy.h"

struct Context { whisper_context *model; std::atomic<bool> cancelled{false}; std::vector<int> arabic_tokens; };
static void quiet_log(ggml_log_level, const char *, void *) {}
static bool aborted(void *data) { return static_cast<Context *>(data)->cancelled.load(); }
static void hindi_script(whisper_context *, whisper_state *, const whisper_token_data *, int, float *logits, void *data) {
    // Every regular Arabic/Urdu UTF-8 character has a D8..DF lead byte, even in split BPE pieces.
    // Keep Latin tokens available for English words in mixed speech. Final script validation remains.
    for (int id : static_cast<Context *>(data)->arabic_tokens) logits[id] = -std::numeric_limits<float>::infinity();
}
static void fail(JNIEnv *env, const char *message) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message); }
static jstring utf8(JNIEnv *env, const std::string &text) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    jclass klass = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(klass, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(klass, constructor, bytes, encoding));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(encoding); env->DeleteLocalRef(klass);
    return result;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_localvoice_engine_WhisperNative_load(JNIEnv *env, jobject, jstring path) {
    whisper_log_set(quiet_log, nullptr);
    const char *name = env->GetStringUTFChars(path, nullptr);
    auto params = whisper_context_default_params(); params.use_gpu = false;
    whisper_context *model = whisper_init_from_file_with_params(name, params);
    env->ReleaseStringUTFChars(path, name);
    if (!model) { fail(env, "The local model could not be loaded. Check its files or choose a smaller model."); return 0; }
    auto *ctx = new Context(); ctx->model = model;
    for (int id = 0; id < whisper_n_vocab(model); ++id) {
        if (uses_arabic_utf8_lead(whisper_token_to_str(model, id))) ctx->arabic_tokens.push_back(id);
    }
    return reinterpret_cast<jlong>(ctx);
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_localvoice_engine_WhisperNative_transcribe(JNIEnv *env, jobject, jlong handle, jfloatArray input, jstring language, jint threads) {
    auto *ctx = reinterpret_cast<Context *>(handle);
    if (!ctx || !ctx->model) { fail(env, "No model is loaded."); return nullptr; }
    const auto count = env->GetArrayLength(input);
    if (count <= 0 || count > 16000 * 600) { fail(env, "Audio is empty or exceeds ten minutes."); return nullptr; }
    std::vector<float> pcm(count); env->GetFloatArrayRegion(input, 0, count, pcm.data());
    const char *code = env->GetStringUTFChars(language, nullptr);
    const bool hindi = std::string(code) == "hi";
    if (hindi && !whisper_is_multilingual(ctx->model)) {
        env->ReleaseStringUTFChars(language, code);
        std::fill(pcm.begin(), pcm.end(), 0.f);
        fail(env, "Hindi requires a multilingual Whisper model."); return nullptr;
    }
    auto params = whisper_full_default_params(hindi ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::clamp(static_cast<int>(threads), 1, 4);
    params.language = code; params.translate = false;
    params.detect_language = false;
    // Do not seed made-up Hindi sentences: small models can echo them as invented speech.
    params.initial_prompt = nullptr;
    if (hindi) {params.logits_filter_callback = hindi_script; params.logits_filter_callback_user_data = ctx;}
    params.beam_search.beam_size = 5;
    // Retain entropy/log-probability fallback to escape degenerate repetitions.
    // The language/task prefix remains fixed to Hindi transcription at every temperature.
    params.no_context = true; params.single_segment = false;
    params.print_realtime = false; params.print_progress = false; params.print_timestamps = false; params.print_special = false;
    params.suppress_blank = true; params.suppress_nst = true;
    params.abort_callback = aborted; params.abort_callback_user_data = ctx;
    const int result = whisper_full(ctx->model, params, pcm.data(), count);
    env->ReleaseStringUTFChars(language, code);
    std::fill(pcm.begin(), pcm.end(), 0.f);
    if (ctx->cancelled.load()) { fail(env, "Transcription cancelled."); return nullptr; }
    if (result != 0) { fail(env, "Local transcription failed. Try a shorter recording or smaller model."); return nullptr; }
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(ctx->model); ++i) text += whisper_full_get_segment_text(ctx->model, i);
    return utf8(env, text);
}
extern "C" JNIEXPORT void JNICALL Java_dev_localvoice_engine_WhisperNative_resetCancel(JNIEnv *, jobject, jlong handle) { auto *ctx = reinterpret_cast<Context *>(handle); if(ctx) ctx->cancelled.store(false); }
extern "C" JNIEXPORT void JNICALL Java_dev_localvoice_engine_WhisperNative_cancel(JNIEnv *, jobject, jlong handle) { auto *ctx = reinterpret_cast<Context *>(handle); if(ctx) ctx->cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL Java_dev_localvoice_engine_WhisperNative_free(JNIEnv *, jobject, jlong handle) { auto *ctx = reinterpret_cast<Context *>(handle); if(ctx) {whisper_free(ctx->model); delete ctx;} }
extern "C" JNIEXPORT jintArray JNICALL Java_dev_localvoice_engine_WhisperNative_speechRange(JNIEnv *env, jobject, jstring path, jfloatArray input) {
    const char *name = env->GetStringUTFChars(path, nullptr);
    auto params = whisper_vad_default_context_params(); params.n_threads = 1; params.use_gpu = false;
    auto *vad = whisper_vad_init_from_file_with_params(name, params);
    env->ReleaseStringUTFChars(path, name);
    if(!vad) return nullptr;
    const auto count = env->GetArrayLength(input);
    std::vector<float> pcm(count); env->GetFloatArrayRegion(input, 0, count, pcm.data());
    int start=-1, end=-1;
    if(whisper_vad_detect_speech(vad, pcm.data(), count)) {
        const auto *prob = whisper_vad_probs(vad);
        for(int i=0; i<whisper_vad_n_probs(vad); ++i) if(prob[i] >= 0.3f) {if(start<0) start=i*512; end=(i+1)*512;}
    }
    whisper_vad_free(vad); std::fill(pcm.begin(), pcm.end(), 0.f);
    jint values[2] = {start<0 ? -1 : std::max(0,start-7200), end<0 ? -1 : std::min(count,end+7200)};
    auto result = env->NewIntArray(2); env->SetIntArrayRegion(result,0,2,values); return result;
}
