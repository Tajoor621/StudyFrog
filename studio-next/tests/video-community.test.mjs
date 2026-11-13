import assert from "node:assert/strict";
import test from "node:test";
import { promptLink, studioPrompt } from "../lib/services/community-links.ts";
import { publicCreations, publishCreation } from "../lib/services/video-community.ts";

const env = {
  SUPABASE_URL: "https://db.example",
  SUPABASE_SECRET_KEY: "sb_secret_fixture",
  SUPABASE_PUBLISHABLE_KEY: "sb_publishable_fixture",
  VIDEO_COMMUNITY_MEDIA_ORIGINS: "https://media.example",
};
const video = { prompt: "A frog on a lily pad", videoUrl: "https://media.example/frog.mp4", ratio: "9:16" };

test("Try This Prompt round-trips Unicode and reserved characters safely", () => {
  const prompt = "Frog 🐸 & dragonfly + water? #1";
  const url = new URL(promptLink(prompt, "9:16"), "https://study.example");
  assert.equal(url.pathname, "/video-studio");
  assert.deepEqual(studioPrompt(Object.fromEntries(url.searchParams)), { prompt, ratio: "9:16" });
  assert.deepEqual(studioPrompt({ prompt: ["a", "b"], ratio: "2:1" }), { prompt: "", ratio: "16:9" });
  assert.equal(studioPrompt({ prompt: "x".repeat(2100) }).prompt.length, 2000);
});

test("feed requests only public records with a publishable key and filters invalid URLs", async () => {
  const row = { id: "first", prompt: video.prompt, video_url: video.videoUrl, aspect_ratio: "9:16" };
  const feed = await publicCreations(env, async (url, init) => {
    assert.equal(url.pathname, "/rest/v1/video_creations");
    assert.equal(url.searchParams.get("is_public"), "eq.true");
    assert.equal(url.searchParams.get("limit"), "24");
    assert.equal(url.searchParams.get("order"), "created_at.desc,id.desc");
    assert.equal(init.headers.apikey, env.SUPABASE_PUBLISHABLE_KEY);
    assert.equal(init.cache, "no-store");
    return Response.json([row, { ...row, video_url: "https://other.example/video.mp4" }]);
  });
  assert.deepEqual(feed, [row]);
});

test("publication rejects local, sample, unapproved and signed media before database access", async () => {
  for (const videoUrl of ["/fixtures/video-waterfall.mp4", "http://127.0.0.1/video.mp4",
    "https://media.example.evil.example/video.mp4", "https://media.example/video.mp4?token=secret"]) {
    assert.equal(await publishCreation(env, { ...video, videoUrl }, async () => assert.fail("Unexpected write")),
      "not-shareable");
  }
});

test("publication stores a nullable owner and sends the secret only to Supabase", async () => {
  assert.equal(await publishCreation(env, video, async (url, init) => {
    assert.equal(url.origin, env.SUPABASE_URL);
    assert.equal(init.method, "POST");
    assert.equal(init.redirect, "error");
    assert.equal(init.headers.apikey, env.SUPABASE_SECRET_KEY);
    assert.deepEqual(JSON.parse(init.body), { user_id: null, prompt: video.prompt,
      video_url: video.videoUrl, aspect_ratio: video.ratio, is_public: true });
    return new Response(null, { status: 201 });
  }), "published");
});
