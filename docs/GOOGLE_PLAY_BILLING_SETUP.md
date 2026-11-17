# Google Play Billing Setup — FOTO FOTO

FOTO FOTO Android monetization uses Google Play Billing only. The Android app must not open an iyzico checkout for digital credits or subscriptions.

## Android dependency

The project uses Google Play Billing Library 9.1.0:
- Gradle catalog alias: `libs.play.billing`
- Artifact: `com.android.billingclient:billing-ktx:9.1.0`

## Product IDs to create in Play Console

Create these exact product IDs under the FOTO FOTO app. IDs are case-sensitive and must not be changed after release.

### One-time consumable products

| Product ID | Product name | Credit grant |
|---|---|---:|
| `ff_credits_59` | 59 Kredi | 59 |
| `ff_credits_129` | 129 Kredi | 129 |
| `ff_credits_299` | 299 Kredi | 299 |
| `ff_credits_699` | 699 Kredi | 699 |

These are consumable one-time products so the same package can be purchased again. Set localized prices in Play Console; do not hard-code a price in the app. The table lists credit quantities, not guaranteed final prices.

### Subscription

Create subscription product ID `ff_premium_monthly` with a monthly base plan. The subscription must provide ongoing benefits, not only a one-time credit grant. Proposed benefits:
- Ad-free use while the subscription is active.
- A recurring monthly credit allowance (final amount to be decided).
- Higher generation priority / monthly usage limits, where supported.

## Required secure purchase flow

1. Android queries ProductDetails from Google Play and launches the Play purchase UI.
2. Android sends the signed purchase token and product ID to the authenticated FOTO FOTO backend.
3. Backend verifies the token with the Google Play Developer API using a server-side service account.
4. Backend checks package name, product ID, purchase state, quantity, and token uniqueness.
5. Only a verified PURCHASED state can grant credits or subscription entitlement. PENDING purchases must not grant benefits.
6. Backend consumes a verified consumable credit product after granting it, and acknowledges an initial subscription purchase.
7. Android refreshes the wallet from the server. Never trust a client-side credit amount.

## Backend configuration still required

- Enable Google Play Developer API for the Cloud project.
- Create a service account and grant it access to the Play Console app.
- Store the service-account credential JSON securely as a Render secret (never commit it).
- Configure package name as `com.fotofoto.app`.
- Configure Real-time Developer Notifications (RTDN) with a Pub/Sub topic for subscription renewals, cancellations, expirations, and refunds.
- Add database tables/RPCs for unique purchase tokens, idempotent credit grants, and subscription entitlements.

## Testing and release

- Add license testers in Play Console.
- Upload a signed internal-testing build; Play Billing product queries generally require an app installed through a Play testing track.
- Activate products and base plans in Play Console.
- Test successful, canceled, pending, duplicate-token, refund, reinstall, and account-switch scenarios.
- Do not advertise purchases as available until product IDs are active and backend verification is configured.

## Important

The Google Play Billing dependency is added to the Android project, but the purchase UI, Developer API verification, database settlement, subscription lifecycle handling, and Play Console products are not yet complete. No real purchase should be accepted until all these pieces are implemented and tested.
