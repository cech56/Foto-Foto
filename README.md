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
7. `supabase/migrations/0007_paytr_web_billing.sql` (legacy schema foundation; retained for migration history)
8. `supabase/migrations/0008_iyzico_checkout.sql`

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
- Wallet top-ups: iyzico Checkout Form backend foundation is implemented; merchant credentials, sandbox testing, and approved credit-pack setup are still required. The Android app does not expose iyzico in-app checkout while targeting Google Play; use Play Billing unless enrolled in an eligible alternative-billing programme.
- Production runtime: Render is configured on the free plan; cold starts and production reliability must be evaluated before launch.

## Pre-payment / pre-release completion checklist
- [ ] Run all eight Supabase migrations in order in the FOTO FOTO project.
- [ ] Configure `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` as private backend environment variables.
- [ ] Confirm the Render health endpoint reports the expected safe configuration checks.
- [ ] Configure Runway API credentials only when provider access/credits are available.
- [ ] Verify the existing account-deletion UI/API end to end, including cascading deletion of profile, wallet, ledger, reservations, and video-history data.
- [ ] Publish a legally reviewed privacy policy, terms of service, support contact, and data-retention disclosures before public release.
- [ ] Add a production-grade upload flow for large video files and validate duration/frames server-side before enabling advanced tools.
- [ ] Add background reconciliation for stale reservations and provider tasks, plus server-side abuse controls and request limits.
- [ ] Complete signed release configuration, Play Console declarations, content-rating/privacy forms, and real-device tests.
- [ ] Verify signup/email confirmation, password recovery, session expiry, logout, insufficient balance, provider rejection, success, failure/refund, app restart, and flaky-network recovery end to end.

Payment gateway integration and wallet top-ups are tracked in the iyzico section below.

## iyzico checkout (backend foundation)
- Migration 0008 switches the billing catalog to iyzico Checkout Form and adds the iyzico token/payment references and an atomic wallet-settlement RPC. Migration 0007 is retained only as a historical schema prerequisite; the active backend no longer calls PayTR.
- Backend endpoints: `GET /api/catalog`, `POST /api/payments/iyzico/create`, `POST /api/webhooks/iyzico`, and authenticated `GET /api/payments/iyzico/status?conversationId=...`.
- Required private Render environment variables: `IYZICO_API_KEY`, `IYZICO_SECRET_KEY`, and `IYZICO_CALLBACK_URL`. Set `IYZICO_BASE_URL=https://sandbox-api.iyzipay.com` for sandbox; use `https://api.iyzipay.com` only after iyzico approval and testing.
- Checkout uses iyzico's hosted Checkout Form. The server retrieves the payment result by token, validates iyzico's response signature, checks payment status, fraud status, currency, basket, amount and order reference, then grants credits once.
- The Android wallet screen currently shows balance and ledger only; the iyzico purchase dialog and return/polling UI still need to be implemented and tested. Do not expose an external iyzico checkout link in a Google Play-distributed app unless the app is eligible for and enrolled in an applicable alternative-billing programme.
- Product prices and credit quantities remain unset and products are inactive until commercially approved.
- This integration covers one-time credit packs only. Subscription billing requires a separate iyzico subscription flow and product/plan configuration.

## Release readiness and legal drafts
- [Release readiness checklist](docs/RELEASE_READINESS.md)
- [Privacy policy draft — must be reviewed and completed before publication](docs/PRIVACY_POLICY_DRAFT.md)
- [Terms of service draft — must be reviewed and completed before publication](docs/TERMS_OF_SERVICE_DRAFT.md)
