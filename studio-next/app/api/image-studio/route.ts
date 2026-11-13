import { isLocalStudioRequest } from "../../../lib/services/local-request";
import { createImageGenerator, ImageGenerationError } from "../../../lib/services/image-generation";

export const runtime = "nodejs";
let busy = false;
const headers = { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" };
const reply = (error: string, status: number) => Response.json({ error }, { status, headers });

async function parse(request: Request) {
  const reader = request.body?.getReader();
  if (!reader) throw new TypeError();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let size = 0, raw = "";
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > 16_384) {
        await reader.cancel();
        throw new RangeError();
      }
      raw += decoder.decode(value, { stream: true });
    }
    return JSON.parse(raw + decoder.decode());
  } finally { reader.releaseLock(); }
}

export async function POST(request: Request) {
  if (!isLocalStudioRequest(request, "x-studyforge-image")) {
    return reply("Image Studio requires a same-origin request on the local dashboard.", 403);
  }
  if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
    return reply("Expected a JSON request.", 415);
  }
  if (busy) return reply("An image is already being generated. Try again when it finishes.", 429);
  busy = true;
  try {
    let body;
    try { body = await parse(request); }
    catch (error) { return error instanceof RangeError ? reply("Image request is too large.", 413) : reply("Invalid JSON request.", 400); }
    if (typeof body?.prompt !== "string" || !body.prompt.trim() || body.prompt.length > 2000 || body.model !== "flux-schnell") {
      return reply("Enter a prompt up to 2,000 characters and select FLUX.1 Schnell.", 400);
    }
    const image = await createImageGenerator(process.env).generateImage(body.prompt, body.model, request.signal);
    return new Response(image, { headers: { ...headers, "Content-Type": "image/png" } });
  } catch (error) {
    if (request.signal.aborted) return reply("Image request cancelled.", 499);
    if (error instanceof ImageGenerationError && error.kind === "unconfigured") {
      return reply("Configure the local ComfyUI origin in Image Studio setup.", 503);
    }
    if (error instanceof Error && error.name === "TimeoutError") return reply("Local image generation timed out. Check the ComfyUI queue.", 504);
    console.warn("[image-studio] Local image backend did not return an image");
    return reply("Local image generation failed. Check that ComfyUI and the FLUX model files are ready.", 502);
  } finally { busy = false; }
}
