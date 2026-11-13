/** Server-only adapter for a local ComfyUI instance. No hosted inference. */
import { randomInt } from "node:crypto";

export const MAX_IMAGE_BYTES = 8_000_000;
type Env = Readonly<Record<string, string | undefined>>;
type Fetch = typeof fetch;
type Node = { class_type: string; inputs: Record<string, unknown> };

export class ImageGenerationError extends Error {
  constructor(public readonly kind: "unconfigured" | "backend") {
    super(kind);
  }
}

function fluxWorkflow(prompt: string): Record<string, Node> {
  return {
    "1": { class_type: "UNETLoader", inputs: { unet_name: "flux1-schnell.safetensors", weight_dtype: "default" } },
    "2": { class_type: "DualCLIPLoader", inputs: { clip_name1: "clip_l.safetensors", clip_name2: "t5xxl_fp8_e4m3fn.safetensors", type: "flux", device: "default" } },
    "3": { class_type: "VAELoader", inputs: { vae_name: "ae.safetensors" } },
    "4": { class_type: "CLIPTextEncode", inputs: { text: prompt, clip: ["2", 0] } },
    "5": { class_type: "ConditioningZeroOut", inputs: { conditioning: ["4", 0] } },
    "6": { class_type: "EmptySD3LatentImage", inputs: { width: 768, height: 768, batch_size: 1 } },
    "7": { class_type: "KSampler", inputs: { model: ["1", 0], positive: ["4", 0], negative: ["5", 0],
      latent_image: ["6", 0], seed: randomInt(2 ** 32), steps: 4, cfg: 1, sampler_name: "euler", scheduler: "simple", denoise: 1 } },
    "8": { class_type: "VAEDecode", inputs: { samples: ["7", 0], vae: ["3", 0] } },
    "9": { class_type: "PreviewImage", inputs: { images: ["8", 0] } },
  };
}

async function readBounded(response: Response, limit: number): Promise<Uint8Array<ArrayBuffer>> {
  if (!response.ok || !response.body || Number(response.headers.get("content-length")) > limit) {
    await response.body?.cancel();
    throw new ImageGenerationError("backend");
  }
  const reader = response.body.getReader();
  const parts: Uint8Array[] = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) {
        await reader.cancel();
        throw new ImageGenerationError("backend");
      }
      parts.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
  return bytes;
}

export function createImageGenerator(env: Env, request: Fetch = fetch, pollMs = 1500) {
  return {
    async generateImage(prompt: string, model: "flux-schnell", signal: AbortSignal) {
      if (!prompt.trim() || prompt.length > 2000 || model !== "flux-schnell") throw new TypeError("Invalid image request");
      let base: URL;
      try {
        base = new URL(env.IMAGE_COMFYUI_ORIGIN ?? "");
        if (!["http:", "https:"].includes(base.protocol) || !["127.0.0.1", "[::1]"].includes(base.hostname) ||
          base.username || base.password || base.pathname !== "/" || base.search || base.hash) throw new Error();
      } catch { throw new ImageGenerationError("unconfigured"); }
      const timeoutMs = Number(env.IMAGE_COMFYUI_TIMEOUT_MS ?? 600_000);
      if (!Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 1_800_000) {
        throw new ImageGenerationError("unconfigured");
      }
      const deadline = AbortSignal.any([signal, AbortSignal.timeout(timeoutMs)]);
      deadline.throwIfAborted();
      const call = (path: string, init?: RequestInit) => request(new URL(path, base), {
        ...init, signal: deadline, redirect: "error", cache: "no-store",
      });
      const json = async (path: string, init?: RequestInit) =>
        JSON.parse(new TextDecoder().decode(await readBounded(await call(path, init), 512_000)));
      const queued = await json("/prompt", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt: fluxWorkflow(prompt.trim()) }),
      });
      const id: unknown = queued?.prompt_id;
      if (typeof id !== "string" || !/^[a-f0-9-]{36}$/i.test(id)) throw new ImageGenerationError("backend");

      for (;;) {
        await new Promise<void>((resolve, reject) => {
          if (deadline.aborted) return reject(deadline.reason);
          const abort = () => { clearTimeout(timer); reject(deadline.reason); };
          const timer = setTimeout(() => { deadline.removeEventListener("abort", abort); resolve(); }, pollMs);
          deadline.addEventListener("abort", abort, { once: true });
        });
        const history = await json("/history/" + id);
        const entry = history?.[id];
        if (!entry) continue;
        if (entry.status?.status_str !== "success" || entry.status?.completed !== true) throw new ImageGenerationError("backend");
        const image = entry.outputs?.["9"]?.images?.[0];
        if (image?.type !== "temp" || typeof image.filename !== "string" ||
            !/^[a-zA-Z0-9_.-]{1,180}\.png$/.test(image.filename) ||
            (image.subfolder !== undefined && image.subfolder !== "")) throw new ImageGenerationError("backend");
        const result = await call("/view?" + new URLSearchParams({ filename: image.filename, type: "temp" }));
        const bytes = await readBounded(result, MAX_IMAGE_BYTES);
        if (bytes.length < 24 || ![137,80,78,71,13,10,26,10].every((byte, i) => bytes[i] === byte)) {
          throw new ImageGenerationError("backend");
        }
        return bytes;
      }
    },
  };
}
