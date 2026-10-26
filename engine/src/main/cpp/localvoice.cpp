#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <memory>
#include <string>
#include <algorithm>
#include <vector>

struct Context { whisper_context *model; std::atomic<bool> cancelled{false}; };
static void quiet_log(ggml_log_level, const char *, void *) {}
static bool aborted(void *data) { return static_cast<Context *>(data)->cancelled.load(); }
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
    return reinterpret_cast<jlong>(ctx);
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_localvoice_engine_WhisperNative_transcribe(JNIEnv *env, jobject, jlong handle, jfloatArray input, jstring language, jint threads) {
    auto *ctx = reinterpret_cast<Context *>(handle);
    if (!ctx || !ctx->model) { fail(env, "No model is loaded."); return nullptr; }
    const auto count = env->GetArrayLength(input);
    if (count <= 0 || count > 16000 * 120) { fail(env, "Audio is empty or exceeds two minutes."); return nullptr; }
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
    // Context is sample transcription, not an instruction (Whisper is not an instruction model).
    // Both Hindi modes decode to Devanagari first; Romanization happens only after recognition.
    params.initial_prompt = hindi ? "नमस्ते। आज मुझे काम करना है। मेरी meeting कल है।" : nullptr;
    params.beam_search.beam_size = 5;
    // Hotter fallback decoding can drift into English paraphrases on short Hindi clips.
    if (hindi) { params.temperature = 0.0f; params.temperature_inc = 0.0f; }
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
