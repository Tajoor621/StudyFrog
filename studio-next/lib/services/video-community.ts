/** Server-only Supabase adapter. Browser clients never receive the write key. */
import type { AspectRatio } from "./community-links";

type Env = Readonly<Record<string, string | undefined>>;
export type Creation = { id: string; prompt: string; video_url: string; aspect_ratio: AspectRatio };
type Publication = { prompt: string; videoUrl: string; ratio: AspectRatio; userId?: string | null };
export type ShareStatus = "published" | "not-shareable" | "unavailable";
const ratio = (value: unknown): value is AspectRatio =>
  value === "16:9" || value === "9:16" || value === "1:1";

function table(env: Env): URL {
  const base = new URL(env.SUPABASE_URL ?? env.NEXT_PUBLIC_SUPABASE_URL ?? "");
  if (base.protocol !== "https:" || base.username || base.password || base.pathname !== "/" ||
      base.search || base.hash) throw new Error("Invalid Supabase project origin.");
  return new URL("/rest/v1/video_creations", base);
}

function headers(key: string): Record<string, string> {
  // New keys use apikey; legacy JWT keys can also be used as Bearer tokens.
  return { apikey: key, ...(key.startsWith("eyJ") ? { Authorization: "Bearer " + key } : {}) };
}

function shareableUrl(value: string, env: Env): string | undefined {
  try {
    if (value.length > 4096) return;
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash) return;
    const trusted = (env.VIDEO_COMMUNITY_MEDIA_ORIGINS ?? "").split(",").some(item => {
      try {
        const origin = new URL(item.trim());
        return origin.protocol === "https:" && !origin.username && !origin.password &&
          origin.pathname === "/" && !origin.search && !origin.hash && url.origin === origin.origin;
      } catch { return false; }
    });
    if (trusted) return url.href;
  } catch { return; }
}

export async function publicCreations(env: Env, request: typeof fetch = fetch): Promise<Creation[]> {
  if (typeof window !== "undefined") throw new Error("Community queries must run on the server.");
  const key = env.SUPABASE_PUBLISHABLE_KEY ?? env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ??
    env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!key) throw new Error("Community is not configured.");
  const url = table(env);
  url.search = new URLSearchParams({
    select: "id,prompt,video_url,aspect_ratio", is_public: "eq.true",
    order: "created_at.desc,id.desc", limit: "24",
  }).toString();
  const response = await request(url, {
    headers: headers(key), cache: "no-store", redirect: "error", signal: AbortSignal.timeout(8000),
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new Error("Community feed is unavailable.");
  }
  const rows: unknown = await response.json();
  if (!Array.isArray(rows)) throw new Error("Invalid community feed.");
  return rows.slice(0, 24).flatMap((row: unknown) => {
    if (!row || typeof row !== "object") return [];
    const item = row as Record<string, unknown>;
    if (typeof item.id !== "string" || typeof item.prompt !== "string" ||
        typeof item.video_url !== "string" || !ratio(item.aspect_ratio)) return [];
    const videoUrl = shareableUrl(item.video_url, env);
    return videoUrl ? [{ id: item.id, prompt: item.prompt, video_url: videoUrl,
      aspect_ratio: item.aspect_ratio }] : [];
  });
}

export async function publishCreation(
  env: Env, creation: Publication, request: typeof fetch = fetch,
): Promise<ShareStatus> {
  if (typeof window !== "undefined") throw new Error("Community publication must run on the server.");
  const videoUrl = shareableUrl(creation.videoUrl, env);
  if (!videoUrl) return "not-shareable";
  const key = env.SUPABASE_SECRET_KEY ?? env.SUPABASE_SERVICE_ROLE_KEY;
  if (!key) return "unavailable";
  try {
    const response = await request(table(env), {
      method: "POST",
      headers: { ...headers(key), "Content-Type": "application/json", Prefer: "return=minimal" },
      body: JSON.stringify({ user_id: creation.userId ?? null, prompt: creation.prompt,
        video_url: videoUrl, aspect_ratio: creation.ratio, is_public: true }),
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(8000),
    });
    await response.body?.cancel();
    if (response.status === 201) return "published";
    console.warn("[video-community] insert rejected", { status: response.status });
  } catch {
    console.warn("[video-community] insert unavailable");
  }
  return "unavailable";
}
