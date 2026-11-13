import VideoStudio from "./studio";
import { studioPrompt } from "../../../lib/services/community-links";

type Query = { prompt?: string | string[]; ratio?: string | string[] };

export default async function Page({ searchParams }: { searchParams: Promise<Query> }) {
  const initial = studioPrompt(await searchParams);
  return <VideoStudio key={JSON.stringify(initial)} initial={initial} />;
}
