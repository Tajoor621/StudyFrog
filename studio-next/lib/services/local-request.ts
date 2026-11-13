// Next may build request.url from its configured bind hostname rather than
// the browser's Host header. Compare the browser Origin to the real Host.
export function isLocalStudioRequest(request: Request, marker: string): boolean {
  const host = request.headers.get("host") ?? new URL(request.url).host;
  return /^(?:localhost|127\.0\.0\.1|\[::1\])(?::[0-9]{1,5})?$/.test(host) &&
    request.headers.get("origin") === "http://" + host &&
    request.headers.get(marker) === "1";
}
