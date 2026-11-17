# FOTO FOTO iOS

Native SwiftUI starter for iPhone and iPad. It reuses the existing FOTO FOTO Supabase project and Render API; provider secrets remain on the server.

## Open and build
Open `FOTO FOTO.xcodeproj` in Xcode on a Mac. Select an iOS Simulator and build. For a physical iPhone, select a signing Team in Xcode. App Store distribution requires Apple Developer Program membership.

## Included in this first native iOS foundation
- SwiftUI login and account creation using Supabase Auth
- Keychain-backed session storage
- Runway Gen-4.5 text-to-video request through the existing authenticated backend
- Video history and wallet balance/ledger read-only views
- Turkish interface foundation and native iOS navigation

## Deliberately not enabled
- Payments or credit purchases
- Image picker/image-to-video (next implementation step)
- Email OTP verification UI and password reset
- Background task reconciliation and push notifications

The iOS client does not contain the Runway API key or Supabase service-role key. The 59-credit trial bonus remains available only through the existing Android account UI for the designated test account; no trial grant button is exposed on iOS.
