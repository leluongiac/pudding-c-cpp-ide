#include <jni.h>
#include "cpp_highlighter.hpp"

static CppHighlighter g_highlighter;

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_mytexteditor_syntax_NativeSyntaxEngine_parseCpp(
    JNIEnv* env,
    jobject /* thiz */,
    jstring text) {
    if (!text) {
        return env->NewIntArray(0);
    }

    const char* utf8_chars = env->GetStringUTFChars(text, nullptr);
    jsize utf8_len = env->GetStringUTFLength(text);

    std::vector<int32_t> tokens = g_highlighter.highlight(utf8_chars, utf8_len);

    env->ReleaseStringUTFChars(text, utf8_chars);

    jsize count = static_cast<jsize>(tokens.size());
    jintArray result = env->NewIntArray(count);
    if (result && count > 0) {
        env->SetIntArrayRegion(result, 0, count, reinterpret_cast<const jint*>(tokens.data()));
    }
    return result;
}
