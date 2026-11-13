import type { AspectRatio } from "./community-links";

export const CLIP_SECONDS = 5;
export const MIN_CLIPS = 2;
export const MAX_CLIPS = 6;
export const MAX_CLIP_BYTES = 12 * 1024 * 1024;
export const MAX_STITCH_BYTES = 40 * 1024 * 1024;

export function clipCount(value: number) {
  if (!Number.isInteger(value) || value < MIN_CLIPS || value > MAX_CLIPS) {
    throw new RangeError("Choose 2–6 clips.");
  }
  return value;
}

const videoFormats = "mov,mp4,m4a,3gp,3g2,mj2,matroska,webm";

export function probeArgs() {
  return [
    "-v", "error", "-format_whitelist", videoFormats, "-show_entries",
    "stream=codec_type,width,height:stream_disposition=attached_pic:format=duration",
    "-of", "json", "-o", "probe.json", "source",
  ];
}

export function normalizeArgs(index: number, ratio: AspectRatio, duration: number, hasAudio: boolean) {
  const [w, h] = ratio === "9:16" ? [486, 864] : ratio === "1:1" ? [480, 480] : [864, 486];
  return [
    "-format_whitelist", videoFormats, "-i", "source",
    ...(!hasAudio ? ["-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"] : []),
    "-map", "0:V:0", "-map", hasAudio ? "0:a:0" : "1:a:0",
    "-vf", `scale=${w}:${h}:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=${w}:${h}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=24`,
    "-af", "aresample=48000:async=1:first_pts=0,apad", "-t", String(duration),
    "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-crf", "24",
    "-c:a", "aac", "-b:a", "96k", "-ar", "48000", "-ac", "2",
    "-map_metadata", "-1", "-fs", String(MAX_CLIP_BYTES), `clip-${index}.mp4`,
  ];
}

export function concatManifest(count: number) {
  return Array.from({ length: clipCount(count) }, (_, i) => `file 'clip-${i}.mp4'`).join("\n") + "\n";
}

export function concatArgs() {
  return ["-f", "concat", "-safe", "1", "-i", "clips.txt", "-map", "0:v:0", "-map", "0:a:0",
    "-c", "copy", "-movflags", "+faststart", "stitched.mp4"];
}
