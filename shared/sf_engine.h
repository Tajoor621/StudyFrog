#ifndef SF_ENGINE_H
#define SF_ENGINE_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
int sf_available(void);
int sf_progress(void);
void sf_cancel(void);
void sf_cancel_request(uint64_t token);
/* RGB input/output lengths are width*height*3. Caller frees output/error with sf_free. */
int sf_generate(const char* model, const char* prompt, const char* negative,
                int width, int height, int steps, int64_t seed, float strength,
                const uint8_t* initial_rgb, uint8_t** output_rgb, char** error);
int sf_generate_request(uint64_t token, const char* model, const char* prompt, const char* negative,
                int width, int height, int steps, int64_t seed, float strength,
                const uint8_t* initial_rgb, uint8_t** output_rgb, char** error);
void sf_free(void* memory);
#ifdef __cplusplus
}
#endif
#endif
