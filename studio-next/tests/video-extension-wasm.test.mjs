import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import test from "node:test";
import { concatArgs, concatManifest, normalizeArgs, probeArgs } from "../lib/services/video-extension-plan.ts";

const require = createRequire(import.meta.url);
let factory;
try { factory = require.resolve("@ffmpeg/core"); } catch { /* optional core during source-only testing */ }

test("the pinned WebAssembly core probes and stitches four fixture clips", { skip: !factory }, async () => {
  globalThis.self = globalThis;
  globalThis.location = new URL("http://localhost/");
  const core = await require(factory)({ wasmBinary: readFileSync(require.resolve("@ffmpeg/core/wasm")) });
  const fixture = readFileSync(new URL("../public/fixtures/video-waterfall.mp4", import.meta.url));
  function metadata(args) {
    core.ffprobe(...args);
    core.reset();
    const info = JSON.parse(core.FS.readFile("probe.json", { encoding: "utf8" }));
    core.FS.unlink("probe.json");
    return info;
  }
  function encode(args) {
    core.exec(...args);
    assert.equal(core.ret, 0);
    core.reset();
  }
  for (let i = 0; i < 4; i++) {
    core.FS.writeFile("source", fixture);
    const info = metadata(probeArgs());
    assert.equal(Number(info.format.duration), 2);
    encode(normalizeArgs(i, "16:9", Number(info.format.duration), false));
    core.FS.unlink("source");
  }
  core.FS.writeFile("clips.txt", concatManifest(4));
  encode(concatArgs());
  const result = metadata([...probeArgs().slice(0, -1), "stitched.mp4"]);
  assert.ok(Math.abs(Number(result.format.duration) - 8) < 0.15);
  assert.deepEqual(result.streams.map(s => s.codec_type), ["video", "audio"]);
  assert.equal(result.streams[0].width * 9, result.streams[0].height * 16);
});
