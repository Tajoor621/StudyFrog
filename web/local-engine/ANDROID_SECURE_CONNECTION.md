# Secure Android 10+ connection to Study Frog's free local image engine

This uses **Tailscale Serve**, which provides HTTPS access to a service bound to your computer's loopback address through your private Tailscale network. **Do not use Tailscale Funnel**: Funnel publishes a service to the public internet. The Android browser and computer must belong to the same authorized tailnet; Tailscale access controls determine which devices can reach the computer. No public port forwarding or paid generation API is required for personal use (subject to Tailscale plan terms).

## Computer

1. Install and sign in to [Tailscale](https://tailscale.com/download) on your Windows/macOS/Linux computer. Enable Tailscale HTTPS certificates / MagicDNS if prompted.
2. Install [Ollama](https://ollama.com/download) and pull a compatible **image** model (not a text-only model).
3. Clone/download StudyFrog. From its root, run `python3 web/local-engine/bridge.py`. It binds **only** to `127.0.0.1:8766`.
4. In another terminal run `tailscale serve --bg 8766` (on supported recent Tailscale versions), then `tailscale serve status` to see the **private HTTPS URL**, typically `https://your-computer.your-tailnet.ts.net/`. Depending on your Tailscale version, you may instead need `tailscale serve --bg --https=443 http://127.0.0.1:8766`; consult `tailscale serve --help` for your installed version.
5. Keep Ollama, the Python bridge and Tailscale running when generating. Verify the URL opens on the computer.

## Samsung / Android 10 or later

1. Install Tailscale for Android, sign in to **the same tailnet**, and enable its VPN connection.
2. In Chrome or Samsung Internet, open the **HTTPS URL from `tailscale serve status`** (not the public GitHub Pages URL, and not `127.0.0.1`).
3. Go to **Studio → Check local engine**, select your image model, enter your prompt and press **Generate image**. Export the PNG when complete.
4. You can use Wi-Fi or mobile data. Your computer must remain online and reachable in your private tailnet.

## Security

- The Python bridge stays loopback-only; only Tailscale Serve terminates remote HTTPS and forwards requests locally.
- Do **not** enable Funnel, expose port 8766 on your router, or set up public reverse proxies without adding authentication.
- The bridge permits loopback clients with local or `.ts.net` hostnames and checks the Origin header. **It does not implement user accounts or per-user authorization**; Tailscale tailnet membership and access controls are the security boundary. Limit tailnet access to trusted devices/users.
- If access fails, check `tailscale serve status`, tailnet access controls, phone VPN connectivity, and whether the local bridge is running.
- This is a **web/PWA integration**, not a modification to the native Android APK. Android 10+ browsers are the intended mobile clients. No real phone-to-PC inference test has yet been performed.

## Limits

Compatible model weights, local GPU/RAM and storage are still required. Video generation is not yet connected. The web editor may have browser storage limits; export generated PNGs. The local AI engine uses no paid generation API, but equipment and internet/electricity costs remain.
