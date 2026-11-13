"use client";

import Link from "next/link";
import ImagePanel from "./image-panel";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import type { VideoOptions, VideoResult } from "../../../lib/services/video-waterfall";

import { CLIP_SECONDS, MAX_CLIPS, MIN_CLIPS } from "../../../lib/services/video-extension-plan";

type Ratio = NonNullable<VideoOptions["aspectRatio"]>;
const allowedImages = new Set(["image/png", "image/jpeg", "image/webp"]);
const maxImageBytes = 8_000_000;
const button = "inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-border px-4 py-2 text-sm font-medium transition-colors hover:bg-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:opacity-50";
const field = "w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-50";

function imageData(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => typeof reader.result === "string"
      ? resolve(reader.result) : reject(new Error("Could not read the source image."));
    reader.onerror = reader.onabort = () => reject(new Error("Could not read the source image."));
    reader.readAsDataURL(file);
  });
}

export default function VideoStudio({ initial }: { initial: { prompt: string; ratio: Ratio } }) {
  const [tab, setTab] = useState<"video" | "image">("video");
  const [prompt, setPrompt] = useState(initial.prompt);
  const [ratio, setRatio] = useState<Ratio>(initial.ratio);
  const [extension, setExtension] = useState(false);
  const [count, setCount] = useState(4);
  const [useLastFrame, setUseLastFrame] = useState(false);
  const [progress, setProgress] = useState("");
  const [shareToCommunity, setShareToCommunity] = useState(false);
  const [shareMessage, setShareMessage] = useState("");
  const [imageMode, setImageMode] = useState(false);
  const [allowRemote, setAllowRemote] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [thumbnail, setThumbnail] = useState("");
  const [result, setResult] = useState<VideoResult | null>(null);
  const [error, setError] = useState("");
  const [playbackError, setPlaybackError] = useState(false);
  const [busy, setBusy] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const active = useRef<AbortController | null>(null);
  const videoTab = useRef<HTMLButtonElement>(null);
  const imageTab = useRef<HTMLButtonElement>(null);
  useEffect(() => () => active.current?.abort(), []);
  const resultUrl = result?.videoUrl;
  useEffect(() => {
    if (resultUrl?.startsWith("blob:")) return () => URL.revokeObjectURL(resultUrl);
  }, [resultUrl]);
  useEffect(() => {
    if (thumbnail) return () => URL.revokeObjectURL(thumbnail);
  }, [thumbnail]);

  function clearFile() {
    setFile(null);
    setThumbnail("");
    if (input.current) input.current.value = "";
  }

  async function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (active.current) return;
    if (!prompt.trim() || (imageMode && !file)) {
      setError(imageMode && !file ? "Upload a source image first." : "Enter a video prompt.");
      return;
    }
    const controller = new AbortController();
    active.current = controller;
    setBusy(true);
    setError("");
    setPlaybackError(false);
    setShareMessage("");
    setProgress("");
    try {
      const image = imageMode && file ? await imageData(file) : undefined;
      controller.signal.throwIfAborted();
      async function generateClip(frame?: string, durationSeconds?: number) {
        const response = await fetch("/api/video-studio", {
          method: "POST",
          headers: { "Content-Type": "application/json", "X-StudyForge-Video": "1" },
          body: JSON.stringify({ prompt: prompt.trim(), image: frame, aspectRatio: ratio, allowRemote,
            shareToCommunity: !extension && shareToCommunity, durationSeconds }),
          signal: controller.signal,
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(typeof data.error === "string" ? data.error : "Video generation failed.");
        if (typeof data.videoUrl !== "string" || typeof data.provider !== "string") throw new Error("The server did not return a video.");
        return { videoUrl: data.videoUrl as string, provider: data.provider as string, shareStatus: data.shareStatus as unknown };
      }
      if (extension) {
        const { extendVideo } = await import("../../../lib/services/video-extension");
        const stitched = await extendVideo({
          count, ratio, sourceImage: image, useLastFrame, signal: controller.signal,
          generate: frame => generateClip(frame, CLIP_SECONDS), progress: setProgress,
        });
        if (controller.signal.aborted) URL.revokeObjectURL(stitched.videoUrl);
        else setResult(stitched);
        return;
      }
      const data = await generateClip(image);
      if (controller.signal.aborted) return;
      setResult({ videoUrl: data.videoUrl, provider: data.provider });
      if (data.shareStatus === "published") setShareMessage("Shared to Community.");
      if (data.shareStatus === "not-shareable") setShareMessage("Video generated. Sharing needs a public video URL on an approved host.");
      if (data.shareStatus === "unavailable") setShareMessage("Video generated, but Community sharing is unavailable.");
    } catch (failure) {
      if (!controller.signal.aborted) {
        setError(failure instanceof Error ? failure.message : "Video generation failed.");
      }
    } finally {
      active.current = null;
      setBusy(false);
    }
  }

  const local = result && ["local-studyfrog-ltx", "local-video", "ollama"].includes(result.provider);
  return (
    <main className="mx-auto max-w-7xl space-y-6 px-4 py-8 text-foreground sm:px-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">Study Frog · Create</p>
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">AI Video Studio</h1>
        <p className="text-sm text-muted-foreground">Describe a scene, or add an image to use as its starting point.</p>
        <Link href="/community" className="inline-flex min-h-11 items-center text-sm font-medium underline">Browse Community</Link>
      </header>
      <div role="tablist" aria-label="Creative studio" className="flex flex-wrap gap-2"
        onKeyDown={event => {
          if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
          event.preventDefault();
          const next = event.key === "Home" ? "video" : event.key === "End" ? "image" : tab === "video" ? "image" : "video";
          setTab(next);
          (next === "video" ? videoTab : imageTab).current?.focus();
        }}>
        <button ref={videoTab} id="video-studio-tab" type="button" role="tab" aria-controls="video-studio-panel"
          aria-selected={tab === "video"} tabIndex={tab === "video" ? 0 : -1} onClick={() => setTab("video")}
          className={button + (tab === "video" ? " bg-muted" : "")}>Video Studio</button>
        <button ref={imageTab} id="image-studio-tab" type="button" role="tab" aria-controls="image-studio-panel"
          aria-selected={tab === "image"} tabIndex={tab === "image" ? 0 : -1} onClick={() => setTab("image")}
          className={button + (tab === "image" ? " bg-muted" : "")}>Image Studio</button>
      </div>
      <div id="image-studio-panel" role="tabpanel" aria-labelledby="image-studio-tab" hidden={tab !== "image"}>
        <ImagePanel videoBusy={busy} onUse={image => {
          if (active.current) return;
          if (input.current) input.current.value = "";
          setFile(image);
          setThumbnail(URL.createObjectURL(image));
          setImageMode(true);
          setError("");
          setTab("video");
          videoTab.current?.focus();
        }} />
      </div>
      <div id="video-studio-panel" role="tabpanel" aria-labelledby="video-studio-tab" hidden={tab !== "video"}>
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,25rem)_minmax(0,1fr)]">
        <form onSubmit={generate} className="space-y-5 rounded-xl border border-border bg-card p-5 text-card-foreground">
          <fieldset disabled={busy} className="space-y-5">
            <legend className="sr-only">Video generation settings</legend>
            <label className="flex min-h-11 items-center gap-3 text-sm font-medium">
              <input type="checkbox" role="switch" checked={imageMode} onChange={e => {
                setImageMode(e.target.checked);
                setError("");
                if (!e.target.checked) clearFile();
              }} />
              Image to Video
            </label>

            <label className="flex min-h-11 items-center gap-3 text-sm font-medium">
              <input type="checkbox" role="switch" checked={extension} onChange={e => {
                setExtension(e.target.checked);
                if (e.target.checked) setShareToCommunity(false);
              }} />
              Video Extension
            </label>
            {extension && <div className="space-y-2 rounded-lg border border-border p-3">
              <label htmlFor="video-count" className="text-sm font-medium">Number of clips</label>
              <select id="video-count" value={count} onChange={e => setCount(Number(e.target.value))} className={field}>
                {Array.from({ length: MAX_CLIPS - MIN_CLIPS + 1 }, (_, i) => i + MIN_CLIPS).map(n =>
                  <option key={n} value={n}>{n} clips · about {n * CLIP_SECONDS} seconds</option>)}
              </select>
              <label className="flex min-h-11 items-center gap-3 text-sm">
                <input type="checkbox" checked={useLastFrame} onChange={e => setUseLastFrame(e.target.checked)} />
                Reuse the previous clip’s last frame
              </label>
              <p className="text-xs text-muted-foreground">Enable frame reuse only if your provider accepts image input. Otherwise the same prompt is used for each new clip. The finished MP4 is stitched on this device.</p>
            </div>}

            <div className="space-y-2">
              <label htmlFor="video-prompt" className="text-sm font-medium">Prompt</label>
              <textarea id="video-prompt" required rows={7} maxLength={2000} value={prompt}
                onChange={e => setPrompt(e.target.value)}
                placeholder="A slow camera push toward a glass terrarium as sunlight moves across the leaves…"
                className={field + " min-h-36 resize-y"} aria-describedby="video-prompt-count" />
              <p id="video-prompt-count" className="text-right text-xs text-muted-foreground">{prompt.length} / 2,000</p>
            </div>

            <div className="space-y-2">
              <label htmlFor="video-ratio" className="text-sm font-medium">Aspect ratio</label>
              <select id="video-ratio" value={ratio} onChange={e => setRatio(e.target.value as Ratio)} className={field}>
                <option value="16:9">16:9 · Landscape</option>
                <option value="9:16">9:16 · Portrait</option>
                <option value="1:1">1:1 · Square</option>
              </select>
            </div>

            {imageMode && <div className="space-y-3">
              <input ref={input} type="file" hidden accept="image/png,image/jpeg,image/webp"
                aria-label="Source image" onChange={e => {
                  const selected = e.target.files?.[0];
                  if (!selected) return;
                  if (!allowedImages.has(selected.type) || selected.size === 0 || selected.size > maxImageBytes) {
                    clearFile();
                    setError("Choose a PNG, JPEG or WebP image up to 8 MB.");
                    return;
                  }
                  setError("");
                  setFile(selected);
                  setThumbnail(URL.createObjectURL(selected));
                }} />
              <button type="button" className={button} onClick={() => input.current?.click()}>
                {file ? "Replace Image" : "Upload Image"}
              </button>
              {file && <div className="flex flex-wrap items-center gap-3 rounded-lg border border-border p-3">
                {/* A temporary object URL previews the file without uploading it. */}
                {/* eslint-disable-next-line @next/next/no-img-element */}
                {thumbnail && <img src={thumbnail} alt="Source image preview" className="size-14 rounded-md object-cover" />}
                <span className="min-w-0 flex-1 break-all text-sm">{file.name}</span>
                <button type="button" onClick={clearFile} className={button}>Remove</button>
              </div>}
            </div>}

            <label className="flex min-h-11 items-center gap-3 text-sm text-muted-foreground">
              <input type="checkbox" checked={allowRemote} onChange={e => setAllowRemote(e.target.checked)} />
              Allow the configured online provider to receive this prompt and image
            </label>
            <label className="flex min-h-11 items-center gap-3 text-sm font-medium">
              <input type="checkbox" role="switch" disabled={extension} checked={shareToCommunity}
                onChange={e => setShareToCommunity(e.target.checked)} />
              Share to Community
            </label>
            {extension && <p className="text-xs text-muted-foreground">Download the stitched MP4 to keep it. Community sharing is unavailable for browser-only files.</p>}
            {shareToCommunity && <p className="text-xs text-muted-foreground">
              Your prompt and generated video will be public. Your source image is not uploaded to Community.
            </p>}
          </fieldset>

          <button type="submit" disabled={busy} className={button + " w-full border-transparent bg-primary font-semibold text-primary-foreground hover:bg-primary/90"}>
            {busy ? "Generating video…" : "Generate Video"}
          </button>
          {busy && <button type="button" onClick={() => active.current?.abort()} className={button + " w-full"}>Cancel</button>}
          {shareMessage && <p role="status" className="text-sm">{shareMessage}</p>}
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        </form>

        <section aria-labelledby="video-preview-heading" className="space-y-4 rounded-xl border border-border bg-card p-5 text-card-foreground">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="video-preview-heading" className="text-lg font-semibold">Preview</h2>
            {local && <span className="rounded-full border border-border bg-muted px-3 py-1 text-xs font-semibold">Generated Locally</span>}
            {result?.provider === "colab-mock" && <span className="rounded-full border border-border px-3 py-1 text-xs">Development sample</span>}
          </div>
          {busy && <div role="status" className="flex items-center gap-3 rounded-lg border border-border bg-muted p-4 text-sm">
            <span aria-hidden="true" className="size-4 rounded-full border-2 border-current border-t-transparent motion-safe:animate-spin" />
            {progress || "Generating your video. This can take several minutes."}
          </div>}
          {result ? <>
            <video key={result.videoUrl} src={result.videoUrl} controls playsInline preload="metadata"
              aria-label="Generated video preview" onError={() => setPlaybackError(true)}
              className="max-h-[65vh] w-full rounded-lg bg-black" />
            {playbackError && <p role="alert" className="text-sm text-destructive">The video could not be played. Check that the runner is reachable or open the file directly.</p>}
            <a className={button} href={result.videoUrl} download="studyfrog-video.mp4"
              target="_blank" rel="noopener noreferrer">Download Video</a>
            {!result.videoUrl.startsWith("blob:") && <p className="text-xs text-muted-foreground">Some providers open the video in a new tab, where you can save it.</p>}
          </> : <div aria-hidden={busy} className="grid aspect-video place-items-center rounded-lg border border-dashed border-border bg-muted/40 px-4 text-center text-sm text-muted-foreground">
            {busy ? "Preparing preview…" : "Your video will appear here after generation."}
          </div>}
        </section>
      </div>
      </div>
    </main>
  );
}
