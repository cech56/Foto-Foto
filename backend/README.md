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

The provider key is never sent to the Android app. The backend verifies FOTO FOTO Supabase access tokens and checks task ownership. Gen-4.5 reserves 12 credits per second before contacting Runway and settles the reservation when task polling reports success or failure. Run migration `0005_credit_reservations.sql` and configure `SUPABASE_SERVICE_ROLE_KEY` before enabling paid generation. Payment processing and robust abuse protection are still pending. Aleph 2, Act-Two, and video upscaling routes remain temporarily gated until exact usage-based pricing can be enforced.

## Required server environment
- `SUPABASE_URL`: FOTO FOTO Supabase project URL.
- `SUPABASE_PUBLISHABLE_KEY`: FOTO FOTO public publishable key; used only to verify user access tokens against Supabase Auth.
- `SUPABASE_SERVICE_ROLE_KEY`: private service-role secret used only by the backend to call the wallet reservation/refund RPCs. Never place it in Android or commit its real value.
- `RUNWAY_API_KEY`: Runway API secret; keep it in the hosting provider's secret manager and never commit it.

Video generation and task status routes require a valid FOTO FOTO Supabase access token in the Authorization Bearer header. Task status lookup additionally checks that the provider task ID exists in the signed-in user's `video_generations` rows. Run `supabase/migrations/0003_video_history.sql` before using task polling. The Android app creates and updates its own history rows using the user JWT.


## FOTO FOTO retail pricing (Gen-4.5)
- 5-second generation: 59 FOTO FOTO credits (59 TL).
- 10-second generation: 119 FOTO FOTO credits (119 TL).
- The backend reserves these retail credits before calling Runway and refunds the full reservation if the provider rejects the request or the task fails/cancels.
- Runway provider usage remains separate: Gen-4.5 uses 12 Runway credits per second. Retail prices are not provider-credit amounts.
- These are initial gross-margin prices, not guaranteed net profit. Payment processing fees, taxes, hosting, currency movement and other operating costs must be included in final accounting.
- Wallet top-ups and payment checkout are not implemented yet; do not advertise the listed credit packages as purchasable until a verified payment/webhook flow is deployed.
