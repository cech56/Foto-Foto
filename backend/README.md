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
