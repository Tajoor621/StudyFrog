# Study Frog Creative Studio

This optional Next.js app brings Video Studio, Image Studio, Video Extension and Community into the same repository as the native apps and static companion. Run it on your own computer. GitHub Pages continues to serve the static companion; it cannot run the Studio server.

From the repository root, with Node.js 24:

```sh
cd studio-next
npm ci
```

Copy `.env.example` to `.env.local` and uncomment `VIDEO_ENABLE_MOCK=1` to try the bundled development sample. Then run `npm run dev` and open **http://127.0.0.1:3000/video-studio**. For a production build use `npm run build` and `npm start`; the development sample is disabled there. The dev and build scripts prepare the local FFmpeg files automatically.

- [Video Studio](VIDEO-STUDIO.md): prompt and image input, playback and export.
- [Image Studio](IMAGE-STUDIO.md): local FLUX generation and direct video handoff.
- [Video Extension](VIDEO-EXTENSION.md): browser stitching and provider requirements.
- [Community](COMMUNITY.md): optional Supabase and public video links.

No paid service is required for local generation and stitching. You need compatible hardware and installed models for real inference. The generation APIs accept same-origin requests on loopback only. Do not expose this local server directly to the internet.

## Provider order and configuration

1. **Free proxy** (optional): Set `VIDEO_FREE_PROXY_ORIGIN` to the origin of a trusted compatible proxy whose terms and costs you have checked, and `VIDEO_FREE_PROXY_MODEL` to one of its supported IDs. The [free-multimodal-proxy source](https://github.com/b3b41020/free-multimodal-proxy) declares the synchronous path `/v1/videos/generations` (plural). Set `VIDEO_FREE_PROXY_PATH=/v1/video/generations` for a compatible singular endpoint. No key is sent. A request reaches this provider only when `allowRemote: true`, including when the proxy itself is on loopback. Confirm how your chosen proxy accepts images; this adapter uses `image_url` with a data URL. The endpoint must return a finished URL (e.g. `{"data":[{"url":"https://…"}]}`). Queued job IDs and base64 video are not supported by this synchronous adapter. Set `VIDEO_PROXY_TIMEOUT_MS` if needed (default 120000). A public proxy's availability, pricing, and downstream data handling can change. Leave this origin unset for local-only operation.
2. **Local**: Set `VIDEO_LOCAL_ORIGIN` to a loopback HTTP origin. With `VIDEO_LOCAL_KIND=compatible`, also set `VIDEO_LOCAL_MODEL` and expose a real video runner with the same JSON request/finished-URL response on `/v1/video/generations` (override with `VIDEO_LOCAL_PATH`). The local result URL must use the runner's own origin. Native Ollama's documented `/api/generate` text endpoint is not a video renderer and is not called.

   Alternatively, use the existing StudyFrog LTX runner by setting `VIDEO_LOCAL_KIND=studyfrog-ltx` and `VIDEO_LOCAL_ORIGIN=http://127.0.0.1:8766`. Follow the [StudyFrog local engine setup](https://github.com/Tajoor621/StudyFrog/blob/main/docs/ENGINE.md), including the engine token shared with its bridge and the local LTX weights. This preset needs an input image and supports the engine's 30-second landscape format. Omit duration and aspect ratio or pass `30` and `16:9`. It polls the local job and attempts cancellation on a timeout or disconnect. Local timeout defaults to 300000 ms via `VIDEO_LOCAL_TIMEOUT_MS`. Running the Next.js server and runner on the same computer allows local playback. For remote clients, serve completed files through a separately authenticated, same-origin media route; a loopback URL points at the browser's computer.
3. **Colab mock**: Set `VIDEO_ENABLE_MOCK=1` and run Next.js in development. The provider returns the bundled two-second H.264 fixture; it does not contact Google Colab or claim to generate a video. It is excluded from production.

Provider timeouts bound how long the service waits; aborting an external POST does not guarantee that the upstream job was cancelled. There are no paid fallback credentials, implicit retries, or model downloads in this service. Actual local video generation requires installed weights and compatible hardware. For remote deployments with short request limits, call the service from a job worker and return progress from your application instead of waiting on the HTTP route.

## Verification

Run `npm test` with Node 24. Tests cover provider order, payloads, privacy controls, validation, development-only mock gating, cancellation, and local LTX job polling using simulated HTTP responses. They do not call hosted services or verify model output quality.
