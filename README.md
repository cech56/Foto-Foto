# FOTO FOTO — Android

FOTO FOTO is an Android-only AI video creation app, independent from Zubih. There is no public website in this project. The initial distribution target is Google Play.

## Stack
- Native Android with Kotlin and Jetpack Compose
- Minimum Android version: API 26
- Video generation first; image generation may follow later
- Runway is the first planned provider. Kling and Veo remain disabled until their API integrations are verified.
- Provider API secrets must stay on a secure backend, never inside the Android app.

## Open and build
Open this repository in Android Studio, sync Gradle, and build the app module. The backend URL must be configured in app/build.gradle.kts before video generation can work.

## Not yet complete
The current commit is an initial Android foundation. Authentication, credit wallet, payments, production API backend, task polling, generated video playback/download/history, app icon, release signing, Play Console setup, and end-to-end testing remain to be completed. No APK/AAB has been built or published yet.
