const assert = require("node:assert/strict");
const { spawn } = require("node:child_process");
const { chromium } = require(process.env.STUDYFORGE_PLAYWRIGHT || "playwright");

const origin = "http://127.0.0.1:3000";
const server = spawn(process.execPath, [
  require.resolve("next/dist/bin/next"), "dev", "--webpack", "--hostname", "127.0.0.1",
], {
  env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1", VIDEO_ENABLE_MOCK: "1",
    VIDEO_FREE_PROXY_ORIGIN: "", VIDEO_LOCAL_ORIGIN: "" },
  stdio: ["ignore", "inherit", "inherit"],
});

async function run() {
  let browser;
  try {
    for (let i = 0; i < 45; i++) {
      try { if ((await fetch(origin + "/video-studio")).ok) break; } catch {}
      if (i === 44) throw Error("Studio did not start");
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    browser = await chromium.launch(process.env.STUDYFORGE_CHROME ? { executablePath: process.env.STUDYFORGE_CHROME } : {});
    const page = await browser.newPage({ viewport: { width: 412, height: 860 } });
    page.setDefaultTimeout(25_000);
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    await page.route("**/api/image-studio", route => route.fulfill({
      contentType: "image/png",
      body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jSxoAAAAASUVORK5CYII=", "base64"),
    }));
    await page.goto(origin + "/video-studio?prompt=A%20forest%20frog&ratio=16%3A9");
    assert.equal(await page.getByLabel("Prompt", { exact: true }).inputValue(), "A forest frog");
    await page.getByRole("tab", { name: "Image Studio" }).click();
    await page.getByLabel("Image prompt").fill("A frog on a leaf");
    await page.getByRole("button", { name: /Generate with FLUX/ }).click();
    await page.getByRole("button", { name: "Use for Image-to-Video" }).click();
    assert.equal(await page.getByRole("switch", { name: "Image to Video" }).isChecked(), true);
    await page.getByText("studyfrog-flux.png").waitFor();
    await page.getByRole("button", { name: "Generate Video" }).click();
    await page.getByText("Development sample").waitFor();
    await page.getByRole("switch", { name: "Video Extension" }).check();
    await page.getByLabel("Number of clips").selectOption("2");
    await page.getByRole("button", { name: "Generate Video" }).click();
    const video = page.getByLabel("Generated video preview");
    await page.waitForFunction(() => document.querySelector("video")?.src.startsWith("blob:"), null, { timeout: 120_000 });
    await page.waitForFunction(() => document.querySelector("video")?.readyState >= 1);
    const duration = await video.evaluate(node => node.duration);
    assert.ok(Math.abs(duration - 4) < 0.25, `Unexpected stitched duration: ${duration}`);
    const download = page.getByRole("link", { name: "Download Video" });
    assert.equal(await download.getAttribute("download"), "studyfrog-video.mp4");
    assert.deepEqual(errors, []);
    console.log("Studio browser verified: prompt prefill, image handoff, sample video and real browser WASM stitch.");
  } finally {
    try { await browser?.close(); } finally { server.kill(); }
  }
}
run().catch(error => { console.error(error); process.exitCode = 1; });
