/** Server-only service. Call from a Next.js Node.js route after authentication. */

export type VideoOptions = {
  durationSeconds?: number;
  aspectRatio?: "16:9" | "9:16" | "1:1";
  signal?: AbortSignal;
  /** Explicit permission to send this request to the configured remote service. */
  allowRemote?: boolean;
};

export type VideoResult = { videoUrl: string; provider: string };

export interface VideoProvider {
  generateVideo(prompt: string, image?: string, options?: VideoOptions): Promise<VideoResult>;
}

type Failure = { provider: string; reason: string; status?: number };
type Logger = (event: Failure & { elapsedMs: number }) => void;
type Backend = VideoProvider & { readonly name: string };

export class VideoGenerationError extends Error {
  constructor(public readonly attempts: readonly Failure[]) {
    super("No configured video provider generated a video.");
    this.name = "VideoGenerationError";
  }
}

class ProviderError extends Error {
  constructor(public readonly reason: string, public readonly status?: number) {
    super(reason);
  }
}

type Environment = Readonly<Record<string, string | undefined>>;
type Config = { env: Environment; request?: typeof fetch; logger?: Logger };
type Json = Record<string, unknown>;
const object = (value: unknown): Json =>
  value !== null && typeof value === "object" && !Array.isArray(value) ? value as Json : {};

const loopback = (url: URL) => ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
const imagePattern = /^data:image\/(?:png|jpeg|webp);base64,([A-Za-z0-9+/]+={0,2})$/;
const fixture = "/fixtures/video-waterfall.mp4";

function origin(value: string, local = false): URL {
  const url = new URL(value);
  if (url.username || url.password || url.search || url.hash || url.pathname !== "/" ||
      !["http:", "https:"].includes(url.protocol) ||
      (local ? !loopback(url) : url.protocol !== "https:" && !loopback(url))) {
    throw new Error("Invalid video provider origin.");
  }
  return url;
}

function endpointPath(path: string): string {
  if (!/^\/(?:[a-z0-9_-]+\/)*[a-z0-9_-]+$/i.test(path)) {
    throw new Error("Invalid video generation endpoint path.");
  }
  return path;
}

function playableUrl(value: unknown, base: URL, local: boolean): string | undefined {
  if (typeof value !== "string" || !value || value.length > 4096 || value !== value.trim()) return;
  try {
    const url = new URL(value, base);
    if (url.username || url.password || (local ? url.origin !== base.origin :
        url.protocol !== "https:" && !(url.protocol === "http:" && url.origin === base.origin && loopback(url)))) return;
    return url.href;
  } catch { return; }
}

function abort(signal?: AbortSignal) { signal?.throwIfAborted(); }

async function json(request: typeof fetch, url: URL, signal: AbortSignal, body?: Json): Promise<Json> {
  const response = await request(url, {
    method: body ? "POST" : "GET",
    ...(body && { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
    signal, redirect: "error", cache: "no-store",
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new ProviderError("http-error", response.status);
  }
  if (!response.headers.get("content-type")?.toLowerCase().includes("json") || !response.body) {
    await response.body?.cancel();
    throw new ProviderError("invalid-response");
  }
  const reader = response.body.getReader();
  let size = 0, text = "";
  const decoder = new TextDecoder();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > 128 * 1024) {
        await reader.cancel();
        throw new ProviderError("response-too-large");
      }
      text += decoder.decode(value, { stream: true });
    }
  } finally {
    reader.releaseLock();
  }
  try { return object(JSON.parse(text + decoder.decode())); }
  catch { throw new ProviderError("invalid-response"); }
}

function signalFor(timeout: number, signal?: AbortSignal): AbortSignal {
  const deadline = AbortSignal.timeout(timeout);
  return signal ? AbortSignal.any([signal, deadline]) : deadline;
}

function pause(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) return reject(signal.reason);
    const onAbort = () => { clearTimeout(timer); reject(signal.reason); };
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, ms);
    signal.addEventListener("abort", onAbort, { once: true });
  });
}

function timeout(raw: string | undefined, fallback: number, max: number): number {
  const n = raw === undefined ? fallback : Number(raw);
  if (!Number.isInteger(n) || n < 1 || n > max) throw new Error("Invalid video timeout.");
  return n;
}

function httpProvider(
  name: string, base: URL, path: string, model: string, request: typeof fetch,
  budget: number, remote: boolean,
): Backend {
  return {
    name,
    async generateVideo(prompt, image, options) {
      if (remote && options?.allowRemote !== true) throw new ProviderError("remote-disabled-for-request");
      const signal = signalFor(budget, options?.signal);
      const result = await json(request, new URL(path, base), signal, {
        model, prompt, ...(image && { image_url: image }),
        ...(options?.durationSeconds && { duration: options.durationSeconds }),
        ...(options?.aspectRatio && { aspect_ratio: options.aspectRatio }),
      });
      const item = object(Array.isArray(result.data) ? result.data[0] : result.output);
      const status = result.status ?? item.status;
      if (result.error || item.error || (status !== undefined &&
          !["completed", "succeeded", "success", "done"].includes(String(status).toLowerCase()))) {
        throw new ProviderError("generation-not-complete");
      }
      const videoUrl = playableUrl(
        result.videoUrl ?? result.video_url ?? result.url ?? item.video_url ?? item.url, base, !remote);
      if (!videoUrl) throw new ProviderError("missing-video-url");
      return { videoUrl, provider: name };
    },
  };
}

function studyFrogProvider(base: URL, request: typeof fetch, budget: number): Backend {
  const name = "local-studyfrog-ltx";
  return {
    name,
    async generateVideo(prompt, image, options) {
      const encoded = image?.match(imagePattern)?.[1];
      if (!encoded) throw new ProviderError("local-image-required");
      if (options?.durationSeconds && options.durationSeconds !== 30) {
        throw new ProviderError("unsupported-local-duration");
      }
      if (options?.aspectRatio && options.aspectRatio !== "16:9") {
        throw new ProviderError("unsupported-local-options");
      }
      const signal = signalFor(budget, options?.signal);
      const health = await json(request, new URL("/engine/health", base), signal);
      if (health.motion_configured !== true) throw new ProviderError("local-model-unavailable");
      const job = await json(request, new URL("/engine/jobs", base), signal,
        { kind: "motion", prompt, image: encoded });
      if (typeof job.id !== "string" || !/^[a-f0-9]{32}$/.test(job.id)) {
        throw new ProviderError("invalid-job-id");
      }
      try {
        for (;;) {
          await pause(2000, signal);
          const state = await json(request, new URL("/engine/jobs/" + job.id, base), signal);
          if (state.status === "done") {
            return { videoUrl: new URL("/engine/results/" + job.id, base).href, provider: name };
          }
          if (state.status !== "queued" && state.status !== "running") {
            throw new ProviderError("local-job-failed");
          }
        }
      } catch (error) {
        if (!(error instanceof ProviderError && error.reason === "local-job-failed")) {
          // The bridge keeps jobs running after a client disconnects. Stop ours if possible.
          try {
            await json(request, new URL("/engine/jobs/" + job.id + "/cancel", base), AbortSignal.timeout(2000), {});
          } catch { /* preserve the original failure */ }
        }
        throw error;
      }
    },
  };
}

export function createVideoWaterfall({ env, request = fetch, logger = ({ provider, reason, status, elapsedMs }) =>
  console.warn("[video-waterfall]", { provider, reason, status, elapsedMs }),
}: Config): VideoProvider {
  if (typeof window !== "undefined") throw new Error("Video waterfall must run on the server.");
  const providers: Backend[] = [];
  if (env.VIDEO_FREE_PROXY_ORIGIN) {
    if (!env.VIDEO_FREE_PROXY_MODEL) throw new Error("Set VIDEO_FREE_PROXY_MODEL.");
    providers.push(httpProvider("free-proxy", origin(env.VIDEO_FREE_PROXY_ORIGIN),
      endpointPath(env.VIDEO_FREE_PROXY_PATH ?? "/v1/videos/generations"), env.VIDEO_FREE_PROXY_MODEL,
      request, timeout(env.VIDEO_PROXY_TIMEOUT_MS, 120_000, 600_000), true));
  }
  if (env.VIDEO_LOCAL_ORIGIN) {
    const base = origin(env.VIDEO_LOCAL_ORIGIN, true);
    const budget = timeout(env.VIDEO_LOCAL_TIMEOUT_MS, 300_000, 1_800_000);
    if (env.VIDEO_LOCAL_KIND === "studyfrog-ltx") {
      providers.push(studyFrogProvider(base, request, budget));
    } else {
      if (env.VIDEO_LOCAL_KIND && env.VIDEO_LOCAL_KIND !== "compatible") {
        throw new Error("Invalid VIDEO_LOCAL_KIND.");
      }
      if (!env.VIDEO_LOCAL_MODEL) throw new Error("Set VIDEO_LOCAL_MODEL.");
      providers.push(httpProvider("local-video", base,
        endpointPath(env.VIDEO_LOCAL_PATH ?? "/v1/video/generations"),
        env.VIDEO_LOCAL_MODEL, request, budget, false));
    }
  }
  if (env.NODE_ENV === "development" && env.VIDEO_ENABLE_MOCK === "1") {
    const name = "colab-mock";
    providers.push({ name, async generateVideo() { return { videoUrl: fixture, provider: name }; } });
  }
  return {
    async generateVideo(prompt, image, options) {
      abort(options?.signal);
      if (typeof prompt !== "string" || !prompt.trim() || prompt.trim().length > 2000) {
        throw new TypeError("Video prompt must contain 1–2000 characters.");
      }
      if (image !== undefined && (typeof image !== "string" || image.length > 10_700_000 || !imagePattern.test(image))) {
        throw new TypeError("Use a base64 PNG, JPEG or WebP image data URL (up to 8 MB).");
      }
      const duration = options?.durationSeconds;
      if (duration !== undefined && (!Number.isInteger(duration) || duration < 1 || duration > 30)) {
        throw new TypeError("Video duration must be a whole number from 1 to 30 seconds.");
      }
      if (options?.aspectRatio !== undefined && !["16:9", "9:16", "1:1"].includes(options.aspectRatio)) {
        throw new TypeError("Unsupported aspect ratio.");
      }
      const attempts: Failure[] = [];
      for (const provider of providers) {
        abort(options?.signal);
        const start = Date.now();
        try {
          const result = await provider.generateVideo(prompt.trim(), image, options);
          abort(options?.signal);
          return result;
        } catch (error) {
          abort(options?.signal);
          const failure: Failure = {
            provider: provider.name,
            reason: error instanceof ProviderError ? error.reason
              : error instanceof Error && error.name === "TimeoutError" ? "timeout" : "request-failed",
            ...(error instanceof ProviderError && error.status !== undefined ? { status: error.status } : {}),
          };
          attempts.push(failure);
          try { logger({ ...failure, elapsedMs: Date.now() - start }); } catch { /* logging cannot prevent fallback */ }
        }
      }
      throw new VideoGenerationError(attempts);
    },
  };
}
