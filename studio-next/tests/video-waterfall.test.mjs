import assert from "node:assert/strict";
import test from "node:test";
import { createVideoWaterfall, VideoGenerationError } from "../lib/services/video-waterfall.ts";

const configured = {
  VIDEO_FREE_PROXY_ORIGIN: "http://127.0.0.1:8080",
  VIDEO_FREE_PROXY_MODEL: "wan-fast",
  VIDEO_LOCAL_ORIGIN: "http://127.0.0.1:7860",
  VIDEO_LOCAL_MODEL: "local-wan",
};
const image = "data:image/png;base64,YQ==";
const ok = (url) => Response.json({ data: [{ url }] });

test("proxy success stops the waterfall; request uses its documented plural route", async () => {
  const calls = [];
  const api = createVideoWaterfall({ env: configured, request: async (url, init) => {
    calls.push([url.pathname, init]);
    return ok("https://cdn.example/clip.mp4");
  } });
  assert.deepEqual(await api.generateVideo("make a frog jump", image,
    { allowRemote: true, durationSeconds: 5, aspectRatio: "9:16" }), {
    videoUrl: "https://cdn.example/clip.mp4", provider: "free-proxy",
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], "/v1/videos/generations");
  assert.deepEqual(JSON.parse(calls[0][1].body), {
    model: "wan-fast", prompt: "make a frog jump", image_url: image,
    duration: 5, aspect_ratio: "9:16",
  });
  assert.equal(calls[0][1].headers.Authorization, undefined);
  assert.equal(calls[0][1].redirect, "error");
});

test("429 falls through to local; diagnostics exclude request and response text", async () => {
  const logs = [];
  const api = createVideoWaterfall({ env: configured, logger: e => logs.push(e),
    request: async url => url.port === "8080" ? new Response("sensitive upstream body", {status: 429})
      : ok("/results/frog.mp4") });
  assert.deepEqual(await api.generateVideo("private prompt", image, { allowRemote: true }),
    { videoUrl: "http://127.0.0.1:7860/results/frog.mp4", provider: "local-video" });
  assert.equal(logs[0].status, 429);
  assert.doesNotMatch(JSON.stringify(logs), /private prompt|sensitive|base64/);
});

test("remote opt-in required and mock can never run in production", async () => {
  const calls = [];
  const request = async url => { calls.push(url.port); return Response.json({ status: "queued", id: "job" }); };
  const logger = () => {};
  const env = { ...configured, VIDEO_ENABLE_MOCK: "1" };
  const dev = createVideoWaterfall({ env: { ...env, NODE_ENV: "development" }, request, logger });
  assert.deepEqual(await dev.generateVideo("a frog"), {
    videoUrl: "/fixtures/video-waterfall.mp4", provider: "colab-mock",
  });
  assert.deepEqual(calls, ["7860"]);
  const prod = createVideoWaterfall({ env: { ...env, NODE_ENV: "production" }, request, logger });
  await assert.rejects(prod.generateVideo("a frog"), error => error instanceof VideoGenerationError &&
    error.attempts.map(a => a.reason).join(",") === "remote-disabled-for-request,generation-not-complete");
});

test("malformed or unsafe output and oversized JSON do not count as success", async () => {
  const api = createVideoWaterfall({ env: configured, logger: () => {}, request: async url =>
    url.port === "8080" ? Response.json({ videoUrl: "javascript:alert(1)" })
      : Response.json({ data: "x".repeat(140_000) }) });
  await assert.rejects(api.generateVideo("frog", undefined, { allowRemote: true }), e =>
    e.attempts[0].reason === "missing-video-url" && e.attempts[1].reason === "response-too-large");
});

test("bad input is rejected before provider calls, and local configuration must be loopback", async () => {
  let calls = 0;
  const api = createVideoWaterfall({ env: configured, request: async () => { calls++; return ok(""); } });
  await assert.rejects(api.generateVideo("  "), TypeError);
  await assert.rejects(api.generateVideo("frog", "https://example.org/private.png"), TypeError);
  await assert.rejects(api.generateVideo("frog", undefined, { durationSeconds: 31 }), TypeError);
  assert.equal(calls, 0);
  assert.throws(() => createVideoWaterfall({ env: { ...configured, VIDEO_LOCAL_ORIGIN: "https://example.org" } }));
});

test("timeout falls through but caller abort stops the entire waterfall", async () => {
  const controller = new AbortController();
  const keepAlive = setInterval(() => {}, 50);
  const calls = [];
  const api = createVideoWaterfall({ env: { ...configured, VIDEO_PROXY_TIMEOUT_MS: "10" },
    logger: () => {}, request: async (url, { signal }) => {
      calls.push(url.port);
      if (url.port === "7860") return ok("/result.mp4");
      return new Promise((_, reject) => signal.addEventListener("abort", () => reject(signal.reason), { once: true }));
    } });
  try {
    assert.equal((await api.generateVideo("frog", undefined, { allowRemote: true })).provider, "local-video");
    assert.deepEqual(calls, ["8080", "7860"]);
    calls.length = 0;
    const result = api.generateVideo("frog", undefined, { allowRemote: true, signal: controller.signal });
    controller.abort();
    await assert.rejects(result, { name: "AbortError" });
    assert.deepEqual(calls, ["8080"]);
  } finally { clearInterval(keepAlive); }
});

test("StudyFrog LTX uses the motion job rather than pretending Ollama generated a video", async () => {
  const job = "a".repeat(32);
  const calls = [];
  const api = createVideoWaterfall({
    env: { VIDEO_LOCAL_ORIGIN: "http://127.0.0.1:8766", VIDEO_LOCAL_KIND: "studyfrog-ltx" },
    request: async (url, init) => {
      calls.push([url.pathname, init.body && JSON.parse(init.body)]);
      if (url.pathname === "/engine/health") return Response.json({ motion_configured: true });
      if (url.pathname === "/engine/jobs") return Response.json({ id: job, status: "queued" }, {status: 202});
      return Response.json({ id: job, status: "done" });
    },
  });
  assert.deepEqual(await api.generateVideo("frog", image), {
    videoUrl: `http://127.0.0.1:8766/engine/results/${job}`, provider: "local-studyfrog-ltx",
  });
  assert.deepEqual(calls, [
    ["/engine/health", undefined], ["/engine/jobs", { kind: "motion", prompt: "frog", image: "YQ==" }],
    ["/engine/jobs/" + job, undefined],
  ]);
});
