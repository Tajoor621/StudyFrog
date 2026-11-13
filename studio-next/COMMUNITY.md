# StudyForge Community

Community is available in the local [Creative Studio](README.md). Video Studio and Community link to each other.

1. Apply `supabase/migrations/20261113000100_video_creations.sql` to your Supabase project using your migration workflow. If this is a new database, you can run the file in the Supabase SQL Editor. No existing project was modified by this package.
2. Configure the following in the Next.js server's `.env.local` (never commit secrets):

   ```dotenv
   SUPABASE_URL=https://YOUR_PROJECT.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   SUPABASE_SECRET_KEY=sb_secret_...
   VIDEO_COMMUNITY_MEDIA_ORIGINS=https://your-public-media-host.example
   ```

   A legacy anon key in `NEXT_PUBLIC_SUPABASE_ANON_KEY` and legacy service role key in `SUPABASE_SERVICE_ROLE_KEY` are also accepted. Only the server uses the secret. The origin list is comma separated and should contain only HTTPS hosts whose videos you intend to publish. The database keeps references; it does not upload or persist the video bytes.

3. Configure a real video provider using [README.md](README.md). In Video Studio, enable **Share to Community** and generate. A successful video with a stable, publicly playable URL from an approved origin creates a public record. Local, mock, and signed URLs are not published. To share locally generated videos, first integrate a deliberate public media publishing step and return its stable URL. A database or sharing failure still leaves the generated video available in the studio.
4. Open `/community` to see the latest 24 public entries. Cards request video metadata for the preview and provide inline playback. **Try This Prompt** copies the prompt and aspect ratio into the studio. It does not copy the source image or start generation.

## Access and privacy

The migration enables RLS, permits anonymous and authenticated clients to read public rows (without author IDs), and reserves writes for the server service role. The share toggle defaults to off. If you want to attach an account to a post, derive its UUID from the application's verified server session and pass it into `publishCreation`; never accept an author ID from the request body. The supplied Video Studio API retains its local-only gate and records shares anonymously. For hosted publishing, integrate your application's real session or guest authorization and per-user rate limiting before opening the endpoint. Community readers can see the prompt and video you choose to share; the source image is not written to Supabase.

## Checks

Run `node --experimental-transform-types --test tests/*.test.mjs` with Node 24. Then apply the migration in a test project, verify that the publishable key cannot insert or read private rows, and check the masonry layout and prompt reuse in your Next.js build. The automated tests use simulated Supabase and provider responses.
