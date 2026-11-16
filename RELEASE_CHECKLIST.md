# FOTO FOTO — Release checklist

Last reviewed: 2026-11-16

## Confirmed in repository / CI
- [x] Native Android app builds with Kotlin and Jetpack Compose.
- [x] GitHub Actions debug build passes (run 43861973769).
- [x] Supabase email sign-up, login, signup-code verification, password reset request, encrypted session storage, refresh-token restoration, and sign-out are implemented.
- [x] Runway Gen-4.5 text-to-video and image-to-video request flow is implemented.
- [x] Per-user video history and task-status refresh are implemented.
- [x] Server-side atomic credit reservation, settlement, and failure refund functions are present.
- [x] Account deletion confirmation UI and authenticated backend deletion endpoint are present.

## Must verify with live services and a real device
- [ ] Confirm all six Supabase migrations are applied to the intended FOTO FOTO project, in order.
- [ ] Verify Supabase RLS, wallet reads, video-history reads/writes, reservation RPC permissions, and account deletion cascades.
- [ ] Set Render production secrets: SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, SUPABASE_SERVICE_ROLE_KEY, RUNWAY_API_KEY.
- [ ] Check Render /health and confirm all required checks report correctly.
- [ ] Confirm Runway account access, available provider credits, task polling, output URL lifetime, and failure/refund behavior.
- [ ] Test registration, email verification, login, password reset, session refresh, logout, insufficient balance, generation success, provider rejection, failure refund, app restart, and weak-network recovery on Android devices.
- [ ] Verify account deletion removes the Auth user and all linked profile, wallet, ledger, reservation, and video-history records.
- [ ] Add persistent abuse protection, request throttling, and background reconciliation for tasks/reservations that stop polling.
- [ ] Review data collection, retention, deletion, and third-party provider disclosures.

## Product / business items still not implemented
- [ ] Wallet top-up checkout and verified payment webhook (e.g. chosen payment provider).
- [ ] Refund/dispute and payment reconciliation procedures.
- [ ] Secure large-media upload pipeline and server-side metadata validation.
- [ ] Enable video editing, character animation, and upscaling only after secure uploads and exact server-side pricing are complete.
- [ ] Privacy policy, terms of service, support email/contact, and data-retention disclosures with the actual operator details.
- [ ] Final app icon, store screenshots, listing text, content rating, Data safety form, and Play Console declarations.
- [ ] Release signing key and protected release build workflow.
- [ ] Signed release APK/AAB install test and Play internal testing track.

## Release rule
A passing debug APK is not a production release. Do not collect payments or publish paid credit prices until payment processing, webhook verification, wallet reconciliation, and production tests pass.
