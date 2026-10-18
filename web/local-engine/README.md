# Study Frog — free local image generation

The web Studio supports optional **Ollama image generation** on your own computer. No paid API, account or credits are required.

## Setup

1. Install [Ollama](https://ollama.com/) and a compatible **image-generation** model supported by your hardware. Text-only models cannot generate images.
2. Start Ollama and confirm `http://127.0.0.1:11434/api/tags` responds.
3. Download/clone this repository to the computer.
4. Run `python3 web/local-engine/bridge.py` from the repository root.
5. On **that same computer**, open `http://127.0.0.1:8766/`, select Studio, click **Check local engine**, choose an image model, write a prompt, then click **Generate image**.
6. Export the result using Studio's **Export PNG**.

The public GitHub Pages URL is a static PWA and **cannot generate AI images without your engine**. The local bridge serves the same web UI and API from one origin. Never expose this unauthenticated loopback-only bridge to the internet. A phone's `127.0.0.1` is not your computer.

## Limitations

This adapter currently supports **text-to-image only**, not video, img2img, or remote-phone-to-PC generation. Model licenses, compatible hardware and electricity costs still apply. Prompts and model choices are controlled by the user, subject to applicable law and model capabilities. The bridge does not store prompts or outputs; browser-local projects are not a durable backup. Real model inference has not yet been device-tested.
