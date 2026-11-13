"use client";

import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

const button = "min-h-11 rounded-lg border border-border px-4 py-2 text-sm font-semibold hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-50";

export default function ImagePanel({ onUse, videoBusy }: {
  onUse: (image: File) => void;
  videoBusy: boolean;
}) {
  const [prompt, setPrompt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [image, setImage] = useState<{ file: File; url: string } | null>(null);
  const request = useRef<AbortController | null>(null);

  useEffect(() => () => request.current?.abort(), []);
  const imageUrl = image?.url;
  useEffect(() => {
    if (imageUrl) return () => URL.revokeObjectURL(imageUrl);
  }, [imageUrl]);

  async function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || videoBusy || !prompt.trim()) return;
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    setError("");
    try {
      const response = await fetch("/api/image-studio", {
        method: "POST", headers: { "Content-Type": "application/json", "X-StudyForge-Image": "1" },
        body: JSON.stringify({ prompt: prompt.trim(), model: "flux-schnell" }),
        signal: controller.signal,
      });
      if (!response.ok) {
        const detail = await response.json().catch(() => null);
        throw new Error(typeof detail?.error === "string" ? detail.error : "Image generation failed.");
      }
      if (response.headers.get("content-type") !== "image/png") throw new Error("The image runner returned an unexpected format.");
      const blob = await response.blob();
      controller.signal.throwIfAborted();
      if (!blob.size || blob.size > 8_000_000) throw new Error("The image cannot be used as a video source.");
      const file = new File([blob], "studyfrog-flux.png", { type: "image/png" });
      setImage({ file, url: URL.createObjectURL(file) });
    } catch (failure) {
      if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Image generation failed.");
    } finally {
      request.current = null;
      setBusy(false);
    }
  }

  return <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,25rem)_minmax(0,1fr)]">
    <form onSubmit={generate} className="space-y-4 rounded-xl border border-border bg-card p-5 text-card-foreground">
      <h2 className="text-lg font-semibold">Create a starting image</h2>
      <p className="text-sm text-muted-foreground">FLUX.1 Schnell generates a still image on your own computer. Use it as the first frame of a video.</p>
      <label htmlFor="image-studio-prompt" className="block text-sm font-medium">Image prompt</label>
      <textarea id="image-studio-prompt" required maxLength={2000} rows={6} value={prompt}
        disabled={busy} onChange={event => setPrompt(event.target.value)}
        placeholder="A tiny tree frog resting on a leaf after rain…"
        className="w-full resize-y rounded-lg border border-input bg-background p-3 text-sm focus-visible:outline-2 focus-visible:outline-ring" />
      <button className={button + " w-full bg-primary text-primary-foreground hover:bg-primary/90"}
        disabled={busy || videoBusy || !prompt.trim()} type="submit">
        {busy ? "Generating image…" : "Generate with FLUX.1 Schnell"}
      </button>
      {busy && <button className={button + " w-full"} type="button" onClick={() => request.current?.abort()}>Stop waiting</button>}
      {busy && <p role="status" className="text-sm text-muted-foreground">Waiting for the local image runner.</p>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </form>
    <section aria-label="Generated image preview" className="space-y-4 rounded-xl border border-border bg-card p-5">
      <h2 className="text-lg font-semibold">Image preview</h2>
      {image ? <>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={image.url} alt="Generated still image" className="max-h-[65vh] w-full rounded-lg object-contain" />
        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={videoBusy} className={button} onClick={() => onUse(image.file)}>Use for Image-to-Video</button>
          <a href={image.url} download={image.file.name} className={button}>Download image</a>
        </div>
      </> : <p className="grid aspect-video place-items-center rounded-lg border border-dashed border-border p-4 text-center text-sm text-muted-foreground">
        Your generated still will appear here.
      </p>}
    </section>
  </div>;
}
