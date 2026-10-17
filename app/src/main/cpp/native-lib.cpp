#include <jni.h>
extern "C" JNIEXPORT jstring JNICALL
Java_com_scenio_mth_MainActivity_nativeStatus(JNIEnv* env, jobject) {
    return env->NewStringUTF("StudyForge • native library loaded • offline canvas ready");
}
