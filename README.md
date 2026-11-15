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
- Per-user wallet balance display with manual refresh. Credit top-ups and mutations are not enabled yet.
- Video prompt, duration and aspect-ratio selection.
- Runway task polling and an external open-video action when a provider URL is returned.
- Per-user video history table and history screen.
- GitHub Actions builds a debug APK on pushes and pull requests.

## Supabase setup
Use the SQL Editor in the separate FOTO FOTO Supabase project and run migrations in this order:
1. `supabase/migrations/0001_profiles.sql`
2. `supabase/migrations/0002_wallet.sql`
3. `supabase/migrations/0003_video_history.sql`

The mobile app uses only the public publishable key. Never put a Supabase secret/service-role key or the Runway API key in the Android app or Git repository.

## Build and release
Open this repository in Android Studio, sync Gradle, and build the app module. GitHub Actions produces a debug APK artifact; that is not a signed Google Play release. A production backend URL, secure hosting secrets, billing/credit accounting, release signing, Play Console setup, and real-device end-to-end tests are still required before public launch.

## Backend
The separate Node.js API is in `backend/`. It verifies FOTO FOTO sessions and restricts task status lookup to task IDs recorded in the signed-in user's history. It still requires production hardening, rate limits, credit reservation/refunds, payment processing, and a deployed HTTPS URL before public exposure.
