import assert from "node:assert/strict";
import { registerHooks } from "node:module";
import test from "node:test";
import { createImageGenerator, ImageGenerationError } from "../lib/services/image-generation.ts";

registerHooks({
  resolve(specifier, context, next) {
    return next(/\/services\/(?:image-generation|local-request)$/.test(specifier) ? specifier + ".ts" : specifier, context);
  },
});
const { POST } = await import("../app/api/image-studio/route.ts");

const origin = "http://127.0.0.1:8188";
const id = "d088a3c5-6ad6-4bc7-9d54-5035ea922a25";
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==", "base64");
const env = { IMAGE_COMFYUI_ORIGIN: origin };
function history(image) {
  return Response.json({ [id]: { status: { status_str: "success", completed: true }, outputs: {
    "9": { images: [image] },
  } } });
}

test("submits fixed FLUX workflow, polls the job and returns its PNG bytes", async () => {
  const paths = [];
  let polls = 0;
  const generator = createImageGenerator(env, async (url, init) => {
    paths.push(url.pathname);
    assert.equal(url.origin, origin);
    assert.equal(init.redirect, "error");
    if (url.pathname === "/prompt") {
      const { prompt } = JSON.parse(init.body);
      assert.equal(prompt["4"].inputs.text, "A forest frog");
      assert.equal(prompt["1"].inputs.unet_name, "flux1-schnell.safetensors");
      assert.equal(prompt["7"].inputs.steps, 4);
      return Response.json({ prompt_id: id });
    }
    if (url.pathname === "/history/" + id) {
      return ++polls === 1 ? Response.json({}) : history({ filename: "preview.png", subfolder: "", type: "temp" });
    }
    assert.equal(url.searchParams.get("filename"), "preview.png");
    assert.equal(url.searchParams.get("type"), "temp");
    return new Response(png);
  }, 0);
  const result = await generator.generateImage(" A forest frog ", "flux-schnell", new AbortController().signal);
  assert.deepEqual(Buffer.from(result), png);
  assert.deepEqual(paths, ["/prompt", "/history/" + id, "/history/" + id, "/view"]);
});

test("refuses remote backends and arbitrary output paths without fetching them", async () => {
  const signal = new AbortController().signal;
  await assert.rejects(createImageGenerator({ IMAGE_COMFYUI_ORIGIN: "https://cloud.example" },
    async () => assert.fail("unexpected request")).generateImage("frog", "flux-schnell", signal),
    e => e instanceof ImageGenerationError && e.kind === "unconfigured");
  const generator = createImageGenerator(env, async url => {
    if (url.pathname === "/prompt") return Response.json({ prompt_id: id });
    if (url.pathname === "/history/" + id) return history({ filename: "../secret.png", type: "temp" });
    assert.fail("unexpected image download");
  }, 0);
  await assert.rejects(generator.generateImage("frog", "flux-schnell", signal), ImageGenerationError);
});

test("cancellation stops polling before it fetches the next resource", async () => {
  const controller = new AbortController();
  const paths = [];
  const generator = createImageGenerator(env, async url => {
    paths.push(url.pathname);
    controller.abort();
    return Response.json({ prompt_id: id });
  }, 0);
  await assert.rejects(generator.generateImage("frog", "flux-schnell", controller.signal), { name: "AbortError" });
  assert.deepEqual(paths, ["/prompt"]);
});

function imageRequest(body, source = "http://localhost:3000") {
  return new Request("http://localhost:3000/api/image-studio", {
    method: "POST", headers: { Origin: source, "X-StudyForge-Image": "1", "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

test("image route keeps generation local and rejects unknown model names and large requests", async () => {
  assert.equal((await POST(imageRequest({ prompt: "frog", model: "flux-schnell" }, "https://other.example"))).status, 403);
  assert.equal((await POST(imageRequest({ prompt: "frog", model: "kling" }))).status, 400);
  assert.equal((await POST(imageRequest({ prompt: "x".repeat(17_000), model: "flux-schnell" }))).status, 413);
});
