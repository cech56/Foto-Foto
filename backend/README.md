# FOTO FOTO API (server only)

This folder is a private server/API component for the Android app. It is not a public website.

## Run
- Use Node.js 20 or newer.
- Copy `.env.example` to `.env` on your hosting provider.
- Set `RUNWAY_API_KEY` as a server-side secret.
- Run `npm start`.
- Set the Android app's `API_BASE_URL` to this server's HTTPS base URL.

## Routes
- `GET /health` — health check.
- `POST /api/generate` — starts a Runway Gen-4.5 text-to-video or image-to-video task.
- `POST /api/edit-video` — starts an Aleph 2 video-to-video edit task.
- `POST /api/upscale-video` — starts a Magnific Video Upscaler task.
- `GET /api/tasks/:taskId` — retrieves task status/output.

The provider key is never sent to the Android app. The backend verifies FOTO FOTO Supabase access tokens and checks task ownership. Credit reservation/refunds, payment processing, and robust abuse protection are still pending. Do not expose paid generation publicly until those controls are implemented.

## Required server environment
- `SUPABASE_URL`: FOTO FOTO Supabase project URL.
- `SUPABASE_PUBLISHABLE_KEY`: FOTO FOTO public publishable key; used only to verify user access tokens against Supabase Auth.
- `RUNWAY_API_KEY`: Runway API secret; keep it in the hosting provider's secret manager and never commit it.

Video generation and task status routes require a valid FOTO FOTO Supabase access token in the Authorization Bearer header. Task status lookup additionally checks that the provider task ID exists in the signed-in user's `video_generations` rows. Run `supabase/migrations/0003_video_history.sql` before using task polling. The Android app creates and updates its own history rows using the user JWT.
