# Add Video Studio to StudyForge

The Studio is included in this repository. Follow the [local setup](README.md) to open `/video-studio`.

To preview without running any AI model, set `VIDEO_ENABLE_MOCK=1` in `.env.local`, start the app in development, and open `http://localhost:3000/video-studio`. Submit a prompt to see the labelled development sample. For actual generation, use the provider environment variables in [README.md](README.md). Turn on the online-provider checkbox only if you want that provider to receive this request. The existing StudyFrog LTX preset supports image-to-video in 16:9 only; other ratios require a compatible provider. The download link saves same-origin results directly; cross-origin results may open in another tab for saving.

The included API route accepts only same-origin requests on the local dashboard and limits concurrent work in its process. Keep the development server on loopback. Before enabling hosted use, integrate the application's real session and generation permission checks, use its configured canonical origin and per-user rate limits, and expose local video files via an authenticated media endpoint for remote clients. If the app uses Next.js `basePath`, prefix the API and bundled fixture URLs accordingly. Longer jobs on time-limited hosting should run in a worker.

Run `node --experimental-transform-types --test tests/*.test.mjs` with Node 24 for provider and route tests. Run the Next.js build and check both modes, all three aspect ratios, upload and removal, keyboard navigation, cancellation, video playback, and download. The standalone tests do not start a Next.js browser session or call a live provider.
