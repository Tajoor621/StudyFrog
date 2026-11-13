export type AspectRatio = "16:9" | "9:16" | "1:1";
export const isAspectRatio = (value: unknown): value is AspectRatio =>
  value === "16:9" || value === "9:16" || value === "1:1";

export function studioPrompt(params: { prompt?: string | string[]; ratio?: string | string[] }) {
  return {
    prompt: typeof params.prompt === "string" ? params.prompt.slice(0, 2000) : "",
    ratio: isAspectRatio(params.ratio) ? params.ratio : "16:9" as const,
  };
}

export function promptLink(prompt: string, ratio: AspectRatio) {
  return "/video-studio?" + new URLSearchParams({ prompt, ratio }).toString();
}
