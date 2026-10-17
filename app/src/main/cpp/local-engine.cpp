#include <jni.h>
#include <string>
#include <vector>
#include <stdexcept>
#include "sf_engine.h"
namespace {
std::string bytes(JNIEnv* env,jbyteArray data) {
 if(!data) return {};
 const auto count=env->GetArrayLength(data);std::string text(count,'\0');
 env->GetByteArrayRegion(data,0,count,reinterpret_cast<jbyte*>(text.data()));return text;
}
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_scenio_mth_inference_NativeEngine_available(JNIEnv*,jobject) { return sf_available()!=0; }
extern "C" JNIEXPORT jint JNICALL Java_com_scenio_mth_inference_NativeEngine_progress(JNIEnv*,jobject) { return sf_progress(); }
extern "C" JNIEXPORT void JNICALL Java_com_scenio_mth_inference_NativeEngine_cancel(JNIEnv*,jobject) { sf_cancel(); }
extern "C" JNIEXPORT jintArray JNICALL Java_com_scenio_mth_inference_NativeEngine_generate(JNIEnv* env,jobject,jbyteArray model,jbyteArray prompt,jbyteArray negative,jint size,jint steps,jlong seed,jfloat strength,jintArray initial) {
 if(size!=256 && size!=512) { env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Size must be 256 or 512");return nullptr; }
 std::vector<uint8_t> rgb;
 if(initial) {
   if(env->GetArrayLength(initial)!=size*size) { env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),"Invalid input image");return nullptr; }
   std::vector<jint> pixels(size*size);env->GetIntArrayRegion(initial,0,size*size,pixels.data());rgb.resize(size*size*3);
   for(int i=0;i<size*size;i++){rgb[i*3]=(pixels[i]>>16)&255;rgb[i*3+1]=(pixels[i]>>8)&255;rgb[i*3+2]=pixels[i]&255;}
 }
 const auto m=bytes(env,model),p=bytes(env,prompt),n=bytes(env,negative);uint8_t* result=nullptr;char* error=nullptr;
 const int ok=sf_generate(m.c_str(),p.c_str(),n.c_str(),size,size,steps,seed,strength,rgb.empty()?nullptr:rgb.data(),&result,&error);
 if(!ok) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),error?error:"Local inference failed");sf_free(error);return nullptr; }
 std::vector<jint> output(size*size);for(int i=0;i<size*size;i++) output[i]=static_cast<jint>(0xff000000u|(result[i*3]<<16)|(result[i*3+1]<<8)|result[i*3+2]);sf_free(result);
 auto array=env->NewIntArray(size*size);if(array) env->SetIntArrayRegion(array,0,size*size,output.data());return array;
}
