# StudyForge Video Extension

Video Extension is included in the local [Creative Studio](README.md).

## Browser encoder

`npm ci` installs the pinned FFmpeg.wasm wrapper and core. The Studio's `npm run dev` and `npm run build` scripts copy the core into `public/ffmpeg/` before running Next.js with Webpack. The browser loads the roughly 31 MiB single-threaded core only when needed. No third-party runtime CDN is required. Follow the core's GPL-2.0-or-later terms if redistributing it.

## Use

- Open **Video Studio → Video Extension**. Four clips is the default; select 2–6. The waterfall requests five seconds per clip, one at a time. Actual duration depends on the provider.
- Optionally select **Reuse the previous clip’s last frame** for providers that accept an image. If the frame cannot be decoded, the next request uses only the prompt. Turn this option off for text-only providers. Reusing the prompt does not guarantee motion or subject continuity.
- Each result needs a directly downloadable video URL. Cross-origin hosts must permit your dashboard origin via CORS; redirects are not followed. Use a short-clip provider: the existing StudyFrog LTX preset produces 30-second clips and remains available in ordinary Video Studio mode.
- Download the stitched MP4 from the preview. It stays on the device and is not submitted to Community. No additional encoding service is required.

The browser accepts up to 12 MiB per input clip, 8 seconds and 1080p per input, and 40 MiB of retained normalized clips. Output is H.264/AAC at 24 fps in the selected aspect ratio, with silence added for clips without audio. These limits bound file sizes; peak browser memory is higher due to codec buffers and the encoder itself. This version is designed for short sequences, not three-minute exports.

## Verify

Run `node --experimental-transform-types --test tests/*.test.mjs` using Node 24. The mixed-format stitch test uses local `ffmpeg` and `ffprobe` when present. For a no-provider UI check, set `VIDEO_ENABLE_MOCK=1` in development and generate four clips: the bundled sample is two seconds, so expect about eight seconds. Check progress, playback, download, and cancellation; then repeat with your chosen short-clip provider.
