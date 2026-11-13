import { isLocalStudioRequest } from "../../../lib/services/local-request";
import { createVideoWaterfall, VideoGenerationError } from "../../../lib/services/video-waterfall";

import { publishCreation } from "../../../lib/services/video-community";

export const runtime = "nodejs";
const MAX_BODY = 10_730_000;
let busy = false;

function reply(value: object, status = 200) {
  return Response.json(value, { status, headers: { "Cache-Control": "no-store" } });
}

async function readBody(request: Request): Promise<Record<string, unknown>> {
  const announced = request.headers.get("content-length");
  if (announced && Number(announced) > MAX_BODY) throw new RangeError();
  const reader = request.body?.getReader();
  if (!reader) throw new TypeError();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let size = 0;
  let body = "";
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY) {
        await reader.cancel();
        throw new RangeError();
      }
      body += decoder.decode(value, { stream: true });
    }
  } finally {
    reader.releaseLock();
  }
  const parsed: unknown = JSON.parse(body + decoder.decode());
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new TypeError();
  return parsed as Record<string, unknown>;
}

export async function POST(request: Request) {
  // Safe standalone default. On a hosted dashboard, replace this local gate with
  // the app's real session/permission check plus its canonical same-origin check.
  if (!isLocalStudioRequest(request, "x-studyforge-video")) {
    return reply({ error: "Video Studio requires a same-origin request on the local dashboard." }, 403);
  }
  if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
    return reply({ error: "Expected a JSON request." }, 415);
  }
  if (busy) return reply({ error: "A video is already being generated. Try again when it finishes." }, 429);
  busy = true;
  try {
    let body: Record<string, unknown>;
    try { body = await readBody(request); }
    catch (error) {
      return error instanceof RangeError
        ? reply({ error: "The image exceeds the upload limit." }, 413)
        : reply({ error: "The video request is not valid JSON." }, 400);
    }
    const { prompt, image, aspectRatio, allowRemote, shareToCommunity, durationSeconds } = body;
    if (typeof prompt !== "string" || (image !== undefined && typeof image !== "string") ||
        (durationSeconds !== undefined && (typeof durationSeconds !== "number" ||
          !Number.isInteger(durationSeconds) || durationSeconds < 1 || durationSeconds > 30)) ||
        (aspectRatio !== "16:9" && aspectRatio !== "9:16" && aspectRatio !== "1:1")) {
      return reply({ error: "Enter a prompt and choose a supported aspect ratio." }, 400);
    }
    let service;
    try { service = createVideoWaterfall({ env: process.env }); }
    catch {
      console.warn("[video-studio] Invalid provider configuration");
      return reply({ error: "The video providers are not configured correctly." }, 503);
    }
    const result = await service.generateVideo(prompt, image, {
      aspectRatio, durationSeconds: durationSeconds as number | undefined, signal: request.signal, allowRemote: allowRemote === true,
    });
    const shareStatus = shareToCommunity === true && !request.signal.aborted
      ? await publishCreation(process.env, { prompt: prompt.trim(), videoUrl: result.videoUrl,
        ratio: aspectRatio, userId: null })
      : undefined;
    return reply({ ...result, shareStatus });
  } catch (error) {
    if (request.signal.aborted) return reply({ error: "Generation cancelled." }, 499);
    if (error instanceof TypeError) return reply({ error: error.message }, 400);
    if (error instanceof VideoGenerationError) {
      const localAspect = error.attempts.some(item => item.reason === "unsupported-local-options");
      const localImage = error.attempts.some(item => item.reason === "local-image-required");
      return reply({ error: localAspect ? "The local StudyFrog runner supports 16:9 only."
        : error.attempts.some(item => item.reason === "unsupported-local-duration")
          ? "The local LTX preset creates 30-second videos. Use a short-clip provider for Video Extension."
        : localImage ? "The local StudyFrog runner needs a source image. Switch to Image to Video."
        : "No video provider was available. Check the local runner or enable your configured proxy." }, 503);
    }
    console.warn("[video-studio] Unexpected generation failure");
    return reply({ error: "Video generation failed. Please try again." }, 500);
  } finally {
    busy = false;
  }
}
