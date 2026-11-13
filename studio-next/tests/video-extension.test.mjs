import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { clipCount, normalizeArgs, concatManifest, concatArgs } from "../lib/services/video-extension-plan.ts";

test("the stitch list is ordered and limited to the supported clip counts", () => {
  assert.throws(() => clipCount(7), RangeError);
  assert.throws(() => clipCount(2.5), RangeError);
  assert.equal(concatManifest(2), "file 'clip-0.mp4'\nfile 'clip-1.mp4'\n");
});

const hasTools = ["ffmpeg", "ffprobe"].every(tool => spawnSync(tool, ["-version"]).status === 0);
test("the stitch commands join a silent landscape clip and an audio portrait clip", { skip: !hasTools }, () => {
  const cwd = mkdtempSync(join(tmpdir(), "studyforge-stitch-"));
  const ff = (...args) => execFileSync("ffmpeg", ["-hide_banner", "-loglevel", "error", "-y", ...args], { cwd });
  try {
    ff("-f", "lavfi", "-i", "color=red:s=320x180:r=30:d=1", "-c:v", "libx264", "-f", "mp4", "source");
    ff(...normalizeArgs(0, "9:16", 1, false));
    ff("-f", "lavfi", "-i", "color=blue:s=180x320:r=25:d=1", "-f", "lavfi", "-i",
      "sine=frequency=440:sample_rate=44100:duration=1", "-c:v", "libx264", "-c:a", "aac", "-f", "mp4", "source");
    ff(...normalizeArgs(1, "9:16", 1, true));
    writeFileSync(join(cwd, "clips.txt"), concatManifest(2));
    ff(...concatArgs());
    const info = JSON.parse(execFileSync("ffprobe", ["-v", "error", "-show_streams",
      "-show_format", "-of", "json", "stitched.mp4"], { cwd, encoding: "utf8" }));
    assert.ok(Math.abs(Number(info.format.duration) - 2) < 0.15);
    assert.deepEqual(info.streams.map(s => s.codec_name), ["h264", "aac"]);
    assert.equal(info.streams[0].width * 16, info.streams[0].height * 9);
    assert.equal(info.streams[1].channels, 2);
  } finally {
    rmSync(cwd, { recursive: true, force: true });
  }
});
