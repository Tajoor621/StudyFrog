#include "sf_engine.h"
#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <thread>
#ifdef SF_LOCAL_INFERENCE
#include "stable-diffusion.h"
#endif
namespace {
std::atomic<int> progress{0};
std::atomic<bool> cancelled{false};
std::mutex generation_mutex, context_mutex;
uint64_t active_token=0;
#ifdef SF_LOCAL_INFERENCE
sd_ctx_t* active_context=nullptr;
struct ContextDeleter { void operator()(sd_ctx_t* p) const { free_sd_ctx(p); } };
struct ImagesDeleter { int count; void operator()(sd_image_t* p) const { free_sd_images(p,count); } };
#endif
}
extern "C" int sf_available() {
#ifdef SF_LOCAL_INFERENCE
return 1;
#else
return 0;
#endif
}
extern "C" int sf_progress() { return progress.load(); }
extern "C" void sf_free(void* memory) { std::free(memory); }
extern "C" void sf_cancel_request(uint64_t token) {
    std::lock_guard<std::mutex> guard(context_mutex);
    if(token!=0 && token!=active_token) return;
    cancelled=true;
#ifdef SF_LOCAL_INFERENCE
    if(active_context) sd_cancel_generation(active_context,SD_CANCEL_ALL);
#endif
}
extern "C" void sf_cancel() { sf_cancel_request(0); }
extern "C" int sf_generate_request(uint64_t token,const char* model,const char* prompt,const char* negative,int width,int height,int steps,int64_t seed,float strength,const uint8_t* initial,uint8_t** output,char** error) {
    if(!output || !error) return 0;
    *output=nullptr;*error=nullptr;
    std::unique_lock<std::mutex> generation(generation_mutex);
    { std::lock_guard<std::mutex> guard(context_mutex);active_token=token;cancelled=false; }
    progress=0;
    try {
        if(!model || !*model || !prompt || !*prompt || !negative) throw std::runtime_error("A model and prompt are required");
        if(width<256 || width>512 || height<256 || height>512 || width%64 || height%64 || steps<1 || steps>40 || !std::isfinite(strength) || strength<.1f || strength>1.f) throw std::runtime_error("Invalid inference dimensions or parameters");
#ifndef SF_LOCAL_INFERENCE
        throw std::runtime_error("Local inference was disabled when this application was built");
#else
        // The app never loads RPC/network backends and never logs prompts or image data.
        sd_set_log_callback([](sd_log_level_t,const char*,void*){},nullptr);
        sd_set_progress_callback([](int step,int total,float,void*) { if(total>0) progress=std::min(99,step*100/total); },nullptr);
        sd_ctx_params_t options;sd_ctx_params_init(&options);
        options.model_path=model;options.enable_mmap=true;options.backend="CPU";options.params_backend="CPU";
        options.n_threads=static_cast<int>(std::max(1u,std::min(4u,std::thread::hardware_concurrency())));
        std::unique_ptr<sd_ctx_t,ContextDeleter> context(new_sd_ctx(&options));
        if(!context) throw std::runtime_error("Model could not be loaded. Import a complete compatible SD 1.x GGUF and check available memory");
        if(cancelled) throw std::runtime_error("Generation cancelled");
        { std::lock_guard<std::mutex> guard(context_mutex);active_context=context.get(); }
        sd_img_gen_params_t parameters;sd_img_gen_params_init(&parameters);
        parameters.prompt=prompt;parameters.negative_prompt=negative;parameters.width=width;parameters.height=height;
        parameters.seed=seed;parameters.batch_count=1;parameters.strength=strength;
        parameters.sample_params.sample_steps=steps;parameters.sample_params.guidance.txt_cfg=7.f;
        parameters.sample_params.sample_method=EULER_A_SAMPLE_METHOD;
        parameters.sample_params.scheduler=sd_get_default_scheduler(context.get(),EULER_A_SAMPLE_METHOD);
        parameters.vae_tiling_params.enabled=true;parameters.vae_tiling_params.tile_size_w=256;parameters.vae_tiling_params.tile_size_h=256;
        if(initial) parameters.init_image={static_cast<uint32_t>(width),static_cast<uint32_t>(height),3,const_cast<uint8_t*>(initial)};
        sd_image_t* raw=nullptr;int count=0;
        bool ok=false;
        try { ok=generate_image(context.get(),&parameters,&raw,&count); }
        catch(...) { std::lock_guard<std::mutex> guard(context_mutex);active_context=nullptr;active_token=0;throw; }
        { std::lock_guard<std::mutex> guard(context_mutex);active_context=nullptr;active_token=0; }
        std::unique_ptr<sd_image_t,ImagesDeleter> images(raw,ImagesDeleter{count});
        if(cancelled) throw std::runtime_error("Generation cancelled");
        if(!ok || !raw || count<1 || !raw[0].data || raw[0].channel!=3 || raw[0].width!=static_cast<uint32_t>(width) || raw[0].height!=static_cast<uint32_t>(height)) throw std::runtime_error("The local model returned no valid RGB image");
        const size_t bytes=static_cast<size_t>(width)*height*3;
        *output=static_cast<uint8_t*>(std::malloc(bytes));if(!*output) throw std::bad_alloc();
        std::memcpy(*output,raw[0].data,bytes);progress=100;return 1;
#endif
    } catch(const std::exception& e) {
        { std::lock_guard<std::mutex> guard(context_mutex);
#ifdef SF_LOCAL_INFERENCE
          active_context=nullptr;
#endif
        }
        { std::lock_guard<std::mutex> guard(context_mutex);active_token=0; }
        *error=static_cast<char*>(std::malloc(std::strlen(e.what())+1));if(*error) std::strcpy(*error,e.what());return 0;
    } catch(...) { return 0; }
}

extern "C" int sf_generate(const char* model,const char* prompt,const char* negative,int width,int height,int steps,int64_t seed,float strength,const uint8_t* initial,uint8_t** output,char** error) {
    return sf_generate_request(0,model,prompt,negative,width,height,steps,seed,strength,initial,output,error);
}
