# FOTO FOTO — Android

FOTO FOTO is an Android-only AI video creation app, independent from Zubih. There is no public website in this project. The initial distribution target is Google Play.

## Stack
- Native Android with Kotlin and Jetpack Compose
- Minimum Android version: API 26
- Video generation first; image generation may follow later
- Runway is the first planned provider. Kling and Veo remain disabled until their API integrations are verified.
- Provider API secrets stay on the separate `backend/` service, never inside the Android app.
- The backend currently exposes a Runway generation endpoint and a task-status proxy. It is an initial scaffold, not production-ready.

## Open and build
Open this repository in Android Studio, sync Gradle, and build the app module. The backend URL must be configured in `app/build.gradle.kts` before video generation can work. Backend setup instructions are in `backend/README.md`.

## Not yet complete
The current commit is an initial Android foundation. Authentication, credit wallet, payments, production hardening (authentication, credit controls, rate limiting and persistent task history), in-app task polling, generated video playback/download/history, app icon, release signing, Play Console setup, and end-to-end testing remain to be completed. No APK/AAB has been built or published yet.

## Supabase setup
- Project URL and the public publishable key are configured in the Android Gradle BuildConfig. The publishable key is intended for mobile clients; never add a Supabase secret/service-role key to the app.
- Run `supabase/migrations/0001_profiles.sql` in the new FOTO FOTO project's SQL Editor.
- Email confirmation settings are controlled in Supabase Authentication. If confirmation is enabled, users must verify their email before signing in.
