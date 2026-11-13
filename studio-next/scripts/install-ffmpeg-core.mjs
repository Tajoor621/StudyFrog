// Run from the Next.js project root after installing the pinned packages.
import { createRequire } from "node:module";
import { copyFile, mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
const require = createRequire(import.meta.url);
const core = require.resolve("@ffmpeg/core");
await mkdir("public/ffmpeg", { recursive: true });
await Promise.all([
  copyFile(new URL("../../branding/studyfrog-icon-192.png", import.meta.url), "public/studyfrog-icon.png"),
  copyFile(core, "public/ffmpeg/ffmpeg-core.js"),
  copyFile(join(dirname(core), "ffmpeg-core.wasm"), "public/ffmpeg/ffmpeg-core.wasm"),
]);
