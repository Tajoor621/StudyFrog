import type { AspectRatio } from "./community-links";
import type { VideoResult } from "./video-waterfall";
import { clipCount, concatArgs, concatManifest, normalizeArgs, probeArgs, MAX_CLIP_BYTES, MAX_STITCH_BYTES } from "./video-extension-plan";

type Sequence = {
  count: number;
  ratio: AspectRatio;
  sourceImage?: string;
  useLastFrame: boolean;
  signal: AbortSignal;
  generate: (image?: string) => Promise<VideoResult>;
  progress: (text: string) => void;
};

async function downloadClip(value: string, signal: AbortSignal): Promise<ArrayBuffer> {
  const url = new URL(value, location.href);
  if (url.username || url.password || (url.origin !== location.origin &&
      url.protocol !== "https:" && !(url.protocol === "http:" &&
      ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)))) {
    throw new Error("The provider returned an unsupported video URL.");
  }
  const deadline = AbortSignal.any([signal, AbortSignal.timeout(45_000)]);
  const response = await fetch(url, {
    signal: deadline, cache: "no-store", redirect: "error",
    credentials: url.origin === location.origin ? "same-origin" : "omit",
  }).catch(error => {
    signal.throwIfAborted();
    throw new Error("Could not download the clip. The video host must allow browser downloads (CORS) and return a direct media URL.", { cause: error });
  });
  const type = response.headers.get("content-type")?.split(";")[0].toLowerCase();
  if (!response.ok || !response.body || (type && !type.startsWith("video/") && type !== "application/octet-stream") ||
      Number(response.headers.get("content-length")) > MAX_CLIP_BYTES) {
    await response.body?.cancel();
    throw new Error("The clip could not be downloaded as video or exceeds 12 MiB.");
  }
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_CLIP_BYTES) {
        await reader.cancel();
        throw new Error("A clip exceeds the 12 MiB browser limit.");
      }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  if (!size) throw new Error("The clip download was empty.");
  signal.throwIfAborted();
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes.buffer;
}

function mediaEvent(video: HTMLVideoElement, event: string, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) return reject(signal.reason);
    const finish = (error?: unknown) => {
      video.removeEventListener(event, ready);
      video.removeEventListener("error", failed);
      signal.removeEventListener("abort", cancelled);
      if (error) reject(error);
      else resolve();
    };
    const ready = () => finish();
    const failed = () => finish(new Error("The browser could not decode the final frame."));
    const cancelled = () => finish(signal.reason);
    video.addEventListener(event, ready, { once: true });
    video.addEventListener("error", failed, { once: true });
    signal.addEventListener("abort", cancelled, { once: true });
  });
}

async function lastFrame(bytes: ArrayBuffer, signal: AbortSignal): Promise<string | undefined> {
  const url = URL.createObjectURL(new Blob([bytes], { type: "video/mp4" }));
  const video = document.createElement("video");
  const deadline = AbortSignal.any([signal, AbortSignal.timeout(6000)]);
  video.muted = true;
  video.playsInline = true;
  video.preload = "auto";
  try {
    const loaded = mediaEvent(video, "loadedmetadata", deadline);
    video.src = url;
    await loaded;
    if (!video.videoWidth || !video.videoHeight || !Number.isFinite(video.duration) || video.duration < 0.2) return;
    const seeked = mediaEvent(video, "seeked", deadline);
    video.currentTime = video.duration - 0.1;
    await seeked;
    const scale = Math.min(1, 768 / Math.max(video.videoWidth, video.videoHeight));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    const context = canvas.getContext("2d");
    if (!context) return;
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/jpeg", 0.8);
  } catch {
    signal.throwIfAborted();
  } finally {
    video.removeAttribute("src");
    video.load();
    URL.revokeObjectURL(url);
  }
}

export async function extendVideo(options: Sequence): Promise<VideoResult> {
  const count = clipCount(options.count);
  const { signal, progress } = options;
  signal.throwIfAborted();
  progress("Loading the browser video editor…");
  const { FFmpeg } = await import("@ffmpeg/ffmpeg");
  signal.throwIfAborted();
  const ffmpeg = new FFmpeg();
  const terminate = () => ffmpeg.terminate();
  const loadingDeadline = setTimeout(terminate, 45_000);
  signal.addEventListener("abort", terminate, { once: true });
  let retainedBytes = 0;
  let nextImage = options.sourceImage;
  const providers = new Set<string>();
  try {
    await ffmpeg.load({
      coreURL: new URL("/ffmpeg/ffmpeg-core.js", location.origin).href,
      wasmURL: new URL("/ffmpeg/ffmpeg-core.wasm", location.origin).href,
    });
    clearTimeout(loadingDeadline);
    for (let i = 0; i < count; i++) {
      signal.throwIfAborted();
      const label = `Clip ${i + 1}/${count}`;
      progress(`${label}: generating…`);
      const result = await options.generate(nextImage);
      providers.add(result.provider);
      progress(`${label} generated. Preparing it for stitching…`);
      const bytes = await downloadClip(result.videoUrl, signal);
      // The worker takes ownership of this buffer. Keep only normalized clips in its filesystem.
      if (!await ffmpeg.writeFile("source", new Uint8Array(bytes))) throw new Error("Could not stage a clip.");
      await ffmpeg.ffprobe(probeArgs(), 15_000);
      // The pinned core can return -1 even on success; the fresh JSON is the
      // source of truth. It is deleted after each clip so it cannot go stale.
      const raw = await ffmpeg.readFile("probe.json", "utf8").catch(() => {
        throw new Error(`${label} is not a readable video.`);
      });
      await ffmpeg.deleteFile("probe.json");
      const info = JSON.parse(String(raw)) as {
        format?: { duration?: string };
        streams?: { codec_type?: string; width?: number; height?: number; disposition?: { attached_pic?: number } }[];
      };
      const duration = Number(info.format?.duration);
      const stream = info.streams?.find(s => s.codec_type === "video" && !s.disposition?.attached_pic);
      const width = stream?.width ?? 0, height = stream?.height ?? 0;
      if (!(duration >= 0.25 && duration <= 8 && width >= 16 && height >= 16 &&
            width <= 1920 && height <= 1920 && width * height <= 1920 * 1080)) {
        throw new Error(`${label} exceeds the browser limits. Use clips up to 8 seconds and 1080p.`);
      }
      const normalized = `clip-${i}.mp4`;
      const code = await ffmpeg.exec(normalizeArgs(i, options.ratio, duration,
        info.streams?.some(s => s.codec_type === "audio") === true), 90_000);
      await ffmpeg.deleteFile("source");
      if (code !== 0) throw new Error(`Could not prepare clip ${i + 1} for stitching.`);
      const file = await ffmpeg.readFile(normalized);
      if (typeof file === "string" || !file.byteLength || file.byteLength >= MAX_CLIP_BYTES) {
        throw new Error("A normalized clip exceeded the browser memory budget.");
      }
      retainedBytes += file.byteLength;
      if (retainedBytes > MAX_STITCH_BYTES) throw new Error("The clips exceed the 40 MiB browser memory budget.");
      nextImage = undefined;
      if (i + 1 < count && options.useLastFrame) {
        const frameBytes = new Uint8Array(file).buffer;
        nextImage = await lastFrame(frameBytes, signal);
      }
    }
    progress(`All ${count} clips generated. Stitching MP4…`);
    await ffmpeg.writeFile("clips.txt", concatManifest(count));
    if (await ffmpeg.exec(concatArgs(), 90_000) !== 0) throw new Error("Could not stitch the clips into an MP4.");
    for (let i = 0; i < count; i++) await ffmpeg.deleteFile(`clip-${i}.mp4`);
    const output = await ffmpeg.readFile("stitched.mp4");
    if (typeof output === "string" || !output.byteLength || output.byteLength > MAX_STITCH_BYTES) {
      throw new Error("The stitched video exceeds the browser memory budget.");
    }
    signal.throwIfAborted();
    return {
      videoUrl: URL.createObjectURL(new Blob([new Uint8Array(output).buffer], { type: "video/mp4" })),
      provider: [...providers].every(provider => ["local-video", "local-studyfrog-ltx", "ollama"].includes(provider))
        ? "local-video" : providers.has("colab-mock") ? "colab-mock" : "browser-stitched",
    };
  } finally {
    clearTimeout(loadingDeadline);
    signal.removeEventListener("abort", terminate);
    terminate();
  }
}
