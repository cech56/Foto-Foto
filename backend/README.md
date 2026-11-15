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
- `POST /api/generate` — starts a Runway Gen-4.5 text-to-video task.
- `GET /api/tasks/:taskId` — retrieves task status/output.

The provider key is never sent to the Android app. This initial backend does not yet implement user authentication, credit reservation/refunds, abuse protection, persistent task history, or payment processing. Do not expose it publicly until those controls are added.

## Required server environment
- `SUPABASE_URL`: FOTO FOTO Supabase project URL.
- `SUPABASE_PUBLISHABLE_KEY`: FOTO FOTO public publishable key; used only to verify user access tokens against Supabase Auth.
- `RUNWAY_API_KEY`: Runway API secret; keep it in the hosting provider's secret manager and never commit it.

Video generation and task status routes now require a valid FOTO FOTO Supabase access token in the Authorization Bearer header.
