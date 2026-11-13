import Link from "next/link";
import { promptLink } from "../../../lib/services/community-links";
import { publicCreations } from "../../../lib/services/video-community";

export const dynamic = "force-dynamic";

export default async function CommunityPage() {
  const creations = await publicCreations(process.env).catch(() => null);
  return (
    <main className="mx-auto max-w-7xl space-y-6 px-4 py-8 text-foreground sm:px-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">Study Frog · Create</p>
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Community</h1>
        <p className="text-sm text-muted-foreground">Browse shared videos and reuse a prompt in your own studio.</p>
        <Link className="inline-flex min-h-11 items-center text-sm font-medium underline" href="/video-studio">Open Video Studio</Link>
      </header>
      {creations === null ? <p role="status">The community feed is unavailable right now.</p>
        : creations.length === 0 ? <p>No public videos have been shared yet.</p>
        : <ul aria-label="Public video creations" className="columns-1 gap-5 sm:columns-2 lg:columns-3 xl:columns-4">
          {creations.map(item => (
            <li key={item.id} className="mb-5 break-inside-avoid overflow-hidden rounded-xl border border-border bg-card text-card-foreground">
              <video src={item.video_url + "#t=0.1"} controls playsInline preload="metadata"
                aria-label="Community video preview" className="w-full bg-black object-contain"
                style={{ aspectRatio: item.aspect_ratio.replace(":", " / ") }} />
              <div className="space-y-3 p-4">
                <p className="line-clamp-3 break-words text-sm">{item.prompt}</p>
                <Link href={promptLink(item.prompt, item.aspect_ratio)}
                  className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 text-sm font-semibold hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring">
                  Try This Prompt
                </Link>
              </div>
            </li>
          ))}
        </ul>}
    </main>
  );
}
