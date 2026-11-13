# Image Studio

Image Studio is available in the local [Creative Studio](README.md). See [Video Extension](VIDEO-EXTENSION.md) for its encoder details.

## Run FLUX locally

Open Generative AI is MIT-licensed, but its upstream image generation requires a Muapi API key. This package uses [ComfyUI's local FLUX.1 Schnell workflow](https://comfyanonymous.github.io/ComfyUI_examples/flux/) instead. FLUX.1 Schnell's published model weights use Apache 2.0. No API key or paid image fallback is configured. Kling Image is not offered because there is no verified local runner for it in this package.

1. Install [ComfyUI](https://github.com/Comfy-Org/ComfyUI) and obtain the model files from the official FLUX example. Use the following filenames:
   - `models/diffusion_models/flux1-schnell.safetensors` (ComfyUI also accepts its legacy `models/unet/` directory)
   - `models/text_encoders/clip_l.safetensors`
   - `models/text_encoders/t5xxl_fp8_e4m3fn.safetensors`
   - `models/vae/ae.safetensors`
2. Start ComfyUI on `127.0.0.1:8188`, using compatible local hardware. For a standard Python checkout: `python main.py --listen 127.0.0.1 --port 8188 --disable-metadata`. The metadata option keeps the prompt out of generated PNG metadata. The model needs an initial download and enough memory to load. No runtime downloads are performed by the adapter.
3. In the Next.js server's `.env.local`, set `IMAGE_COMFYUI_ORIGIN=http://127.0.0.1:8188`. Optional: `IMAGE_COMFYUI_TIMEOUT_MS=600000`.
4. Open `/video-studio` and choose **Image Studio → Generate with FLUX.1 Schnell**. Click **Use for Image-to-Video** to switch tabs, enable image input, and select the generated PNG. Enter a separate motion prompt and submit when ready. The handoff does not send anything to the video provider until you press **Generate Video**.

The adapter submits only its built-in workflow, polls the job, and returns one bounded PNG. ComfyUI writes temporary previews on the computer. The **Stop waiting** button cancels browser/server polling; a job already submitted to ComfyUI can continue. Use the ComfyUI queue controls to stop it if needed. The included API only handles same-origin loopback dashboard requests. The ordinary hosted deployment integration still requires the actual app's session and permission checks. The existing video provider choices and opt-in remote consent remain unchanged.

## Verification

Run `node --experimental-transform-types --test tests/*.test.mjs` with Node 24. Tests exercise the request, job polling, binary PNG output, cancellation and validation with a simulated ComfyUI server. For a live check, start ComfyUI with the model weights, generate an image, and verify that the Video Studio tab shows the generated thumbnail and filename before submitting to a video runner.
