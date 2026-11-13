import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import test from "node:test";

// Node's direct TypeScript runner needs the extension that Next resolves itself.
registerHooks({
  resolve(specifier, context, next) {
    return next(/\/services\/(?:video-waterfall|video-community)$/.test(specifier) ? specifier + ".ts" : specifier, context);
  },
});
const { POST } = await import("../app/api/video-studio/route.ts");
const origin = "http://localhost:3000";
function request(body, options = {}) {
  const { url = origin, source = origin, type = "application/json", headers = {} } = options;
  return new Request(url + "/api/video-studio", {
    method: "POST",
    headers: { Origin: source, "Content-Type": type, "X-StudyForge-Video": "1", ...headers },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

test("route rejects cross-origin and hosted calls with no authentication integration", async () => {
  assert.equal((await POST(request({}, { source: "https://other.example" }))).status, 403);
  assert.equal((await POST(request({}, { url: "https://study.example", source: "https://study.example" }))).status, 403);
});

test("route rejects malformed JSON, invalid fields, and oversized bodies", async () => {
  assert.equal((await POST(request({}, { type: "text/plain" }))).status, 415);
  assert.equal((await POST(request("{"))).status, 400);
  assert.equal((await POST(request({ prompt: "scene", aspectRatio: "auto" }))).status, 400);
  assert.equal((await POST(request("x".repeat(10_730_001)))).status, 413);
});

test("route calls the waterfall and returns a non-cacheable development result", async (t) => {
  t.mock.method(console, "warn", () => {});
  const keys = ["NODE_ENV", "VIDEO_ENABLE_MOCK", "VIDEO_FREE_PROXY_ORIGIN", "VIDEO_LOCAL_ORIGIN"];
  const saved = keys.map(key => [key, process.env[key]]);
  t.after(() => { for (const [key, value] of saved) value === undefined ? delete process.env[key] : process.env[key] = value; });
  process.env.NODE_ENV = "development";
  process.env.VIDEO_ENABLE_MOCK = "1";
  delete process.env.VIDEO_FREE_PROXY_ORIGIN;
  delete process.env.VIDEO_LOCAL_ORIGIN;
  const response = await POST(request({ prompt: "A frog jumps", aspectRatio: "1:1" }));
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.deepEqual(await response.json(), { videoUrl: "/fixtures/video-waterfall.mp4", provider: "colab-mock" });
  process.env.NODE_ENV = "production";
  assert.equal((await POST(request({ prompt: "A frog jumps", aspectRatio: "1:1" }))).status, 503);
});

test("sharing inserts only after generation and only with explicit opt-in", async (t) => {
  const values = {
    VIDEO_FREE_PROXY_ORIGIN: "https://proxy.example",
    VIDEO_FREE_PROXY_MODEL: "wan",
    VIDEO_COMMUNITY_MEDIA_ORIGINS: "https://media.example",
    SUPABASE_URL: "https://db.example",
    SUPABASE_SECRET_KEY: "sb_secret_fixture",
  };
  const saved = Object.keys(values).map(key => [key, process.env[key]]);
  t.after(() => { for (const [key, value] of saved) value === undefined ? delete process.env[key] : process.env[key] = value; });
  Object.assign(process.env, values);
  const calls = [];
  let acceptShare = true;
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push([url.hostname, JSON.parse(options.body)]);
    return url.hostname === "proxy.example" ? Response.json({ video_url: "https://media.example/frog.mp4" })
      : new Response(null, { status: acceptShare ? 201 : 503 });
  });
  t.mock.method(console, "warn", () => {});
  const prompt = "  Frog crossing a lily pond  ";
  const settings = { prompt, aspectRatio: "1:1", allowRemote: true, durationSeconds: 5 };
  assert.equal((await POST(request(settings))).status, 200);
  assert.equal(calls.length, 1);
  assert.equal(calls[0][1].duration, 5);
  const shared = await POST(request({ ...settings, shareToCommunity: true }));
  assert.equal((await shared.json()).shareStatus, "published");
  assert.deepEqual(calls.map(call => call[0]), ["proxy.example", "proxy.example", "db.example"]);
  assert.deepEqual(calls[2][1], {
    user_id: null, prompt: prompt.trim(), video_url: "https://media.example/frog.mp4",
    aspect_ratio: "1:1", is_public: true,
  });
  acceptShare = false;
  const failure = await POST(request({ ...settings, shareToCommunity: true }));
  assert.equal(failure.status, 200);
  assert.deepEqual(await failure.json(), {
    videoUrl: "https://media.example/frog.mp4", provider: "free-proxy", shareStatus: "unavailable",
  });
});
