# FOTO FOTO — Android

FOTO FOTO is an Android-only AI video creation app, independent from Zubih. There is no public website in this project. The initial distribution target is Google Play.

## Stack
- Native Android with Kotlin and Jetpack Compose
- Minimum Android version: API 26
- Video generation first; image generation may follow later
- Runway is the first planned provider. Kling and Veo remain disabled until their API integrations are verified.
- Provider API secrets stay on the separate `backend/` service, never inside the Android app.

## Current app foundation
- Supabase email registration, login, password-reset email request, encrypted Android Keystore session storage, refresh-token restoration, and sign-out.
- Per-user wallet balance and recent ledger display with manual refresh. Server-side credit reservation, settlement, and refund RPCs are implemented; paid credit top-ups are not enabled yet.
- In-app permanent account deletion UI and authenticated backend deletion endpoint are implemented; production cascade behavior still requires end-to-end verification.
- Video prompt, duration and aspect-ratio selection.
- Runway task polling and an external open-video action when a provider URL is returned.
- Per-user video history table and history screen.
- GitHub Actions builds a debug APK on pushes and pull requests.

## Supabase setup
Use the SQL Editor in the separate FOTO FOTO Supabase project and run each migration once, in numerical order:
1. `supabase/migrations/0001_profiles.sql`
2. `supabase/migrations/0002_wallet.sql`
3. `supabase/migrations/0003_video_history.sql`
4. `supabase/migrations/0004_gen45_aspect_ratios.sql`
5. `supabase/migrations/0005_credit_reservations.sql`
6. `supabase/migrations/0006_wallet_read_grants.sql`
7. `supabase/migrations/0007_paytr_web_billing.sql`

The mobile app uses only the public publishable key. Never put a Supabase secret/service-role key or the Runway API key in the Android app or Git repository. The service-role key belongs only in the private backend hosting environment.

## Build and release
Open this repository in Android Studio, sync Gradle, and build the app module. GitHub Actions produces a debug APK artifact; that is not a signed Google Play release. A production backend URL, secure hosting secrets, billing/credit accounting, release signing, Play Console setup, and real-device end-to-end tests are still required before public launch.

## Backend
The separate Node.js API is in `backend/`. It verifies FOTO FOTO sessions, reserves credits atomically before Gen-4.5 requests, refunds failed/cancelled or rejected requests, and restricts task status lookup to reservations belonging to the signed-in user. The app also lets users manually re-check pending history items so a task can still be reconciled after the initial foreground polling window ends.

Runway Gen-4.5 is the only enabled generation mode. Aleph 2 editing, Act-Two character animation, and video upscaling remain deliberately disabled until secure file uploads, server-verified media metadata, and exact credit calculations are implemented. Kling and Veo are not connected.

## Current release status

- Android debug build: passing in GitHub Actions; latest run `43861973769` uploaded `foto-foto-debug-apk` (debug-only, not Play Store signed).
- Account deletion: client confirmation UI and authenticated backend endpoint exist; verify against the live Supabase project before release.
- Advanced modes: video editing, character animation, and upscaling are deliberately gated with HTTP 503 until secure upload and exact server-side pricing are implemented.
- Wallet top-ups/payment checkout: not implemented. Do not sell or advertise credit packages until a verified payment and webhook flow is deployed.
- Production runtime: Render is configured on the free plan; cold starts and production reliability must be evaluated before launch.

## Pre-payment / pre-release completion checklist
- [ ] Run all six Supabase migrations in order in the FOTO FOTO project.
- [ ] Configure `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` as private backend environment variables.
- [ ] Confirm the Render health endpoint reports the expected safe configuration checks.
- [ ] Configure Runway API credentials only when provider access/credits are available.
- [ ] Verify the existing account-deletion UI/API end to end, including cascading deletion of profile, wallet, ledger, reservations, and video-history data.
- [ ] Publish a legally reviewed privacy policy, terms of service, support contact, and data-retention disclosures before public release.
- [ ] Add a production-grade upload flow for large video files and validate duration/frames server-side before enabling advanced tools.
- [ ] Add background reconciliation for stale reservations and provider tasks, plus server-side abuse controls and request limits.
- [ ] Complete signed release configuration, Play Console declarations, content-rating/privacy forms, and real-device tests.
- [ ] Verify signup/email confirmation, password recovery, session expiry, logout, insufficient balance, provider rejection, success, failure/refund, app restart, and flaky-network recovery end to end.

Payment gateway integration and wallet top-ups are intentionally tracked separately from the checklist above.

## PayTR web checkout (foundation added)
- Migration 0007 adds the server-managed product catalog, PayTR payment orders, and an atomic callback settlement RPC. No live products are seeded or activated.
- Backend endpoints: `GET /api/catalog`, `POST /api/payments/paytr/create`, and public `POST /api/webhooks/paytr`.
- Required private Render environment variables: `PAYTR_MERCHANT_ID`, `PAYTR_MERCHANT_KEY`, `PAYTR_MERCHANT_SALT`, `PAYTR_OK_URL`, `PAYTR_FAIL_URL`; optional `PAYTR_TEST_MODE=1` for approved testing.
- Configure the PayTR notification URL as `https://YOUR-RENDER-SERVICE.onrender.com/api/webhooks/paytr` using the actual deployed API hostname.
- PayTR merchant credentials must never be committed to GitHub, placed in Android, or sent in chat.
- A PayTR merchant account and credentials are not available yet, so checkout cannot be activated or tested against PayTR. Product prices and credit quantities also remain intentionally unset.
- This first PayTR phase is one-time credit packs only. Subscription billing still requires its own verified billing-provider flow; for Google Play distributed Android digital subscriptions, use Play Billing unless enrolled in an eligible regional alternative-billing program.