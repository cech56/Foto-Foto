# FOTO FOTO — Payments, subscriptions and credit architecture

Status: proposed design, not yet implemented
Decision: Turkey-first launch, international-ready architecture; sell both one-time credit packs and recurring subscriptions.

## 1. Non-negotiable rules
- Runway is a provider used only by the FOTO FOTO backend. Customers never need Runway accounts.
- The Android client never decides a price, credit amount, subscription entitlement, payment status, or provider cost.
- The backend is the only writer to wallet balances, payment orders, subscription entitlements, and the credit ledger.
- Never grant credits from a client success screen or a redirect URL. Grant only after server-side verification with the payment provider.
- Every external event and purchase token/order ID must be idempotent. Duplicate callbacks must never grant twice.
- Keep payment-provider secrets, Google service-account credentials, Supabase service-role key, and Runway API key only in Render environment secrets.
- Keep a complete audit trail: payment amount/currency, channel, provider, product, granted credits, refund/chargeback state, subscription period, and ledger references.

## 2. Sales channels
### Android app distributed through Google Play
For digital credit packs and digital subscriptions sold inside the Play-distributed app, integrate Google Play Billing unless the app is eligible for a specific alternative-billing program in the user's region. Turkey is not assumed eligible for an exception. Do not add an Iyzico/PayTR external checkout button or direct users to an external checkout from the Play app without verifying current regional policy and enrollment.
- Credit packs: consumable one-time products.
- Subscription tiers: auto-renewing subscriptions.
- Send purchaseToken + productId + signed-in FOTO FOTO user identity to backend.
- Backend verifies purchase using Google Play Developer API before granting any benefit.
- Backend acknowledges/consumes verified purchases as required.
- Process Real-time Developer Notifications (RTDN), deduplicate message IDs, and re-query Google Play for the authoritative current purchase/subscription state.
- Handle refunds, voided purchases, cancellations, grace periods, account hold, expiry, and revocation.

### Web checkout / non-Play distribution
Use Iyzico or PayTR for checkout on a separately operated web checkout. Start with one provider, not both simultaneously. The provider's server callback/webhook is the authority:
- Iyzico: validate the current X-IYZ-SIGNATURE-V3 when enabled; process webhook idempotently.
- PayTR: verify callback hash, reconcile merchant_oid and amount, then return exactly OK after committing the result.
- Never grant based only on the browser return/redirect.
- Web sales and Play sales are separate channels and jurisdictions. Do not assume web-purchased credit can be promoted or sold inside the Play app; confirm Play policy and any eligible regional program before enabling cross-channel sales/marketing.

### International expansion
- Store prices in a product catalog per region and currency; do not hard-code TRY.
- Play local prices are read from Play product details. Web prices are server catalog entries by ISO currency and region.
- Keep provider adapters behind a common payment interface.
- Add Apple In-App Purchase before any iOS release.
- Add local taxes, receipts/invoicing, consumer cancellation/refund rights, privacy and subscription disclosures for each launch region.

## 3. Products and commercial model
Offer both:
1. One-time credit packs (consumable; e.g. Starter / Creator / Studio).
2. Subscription tiers (monthly and optionally annual) that grant a defined monthly credit allowance plus clearly specified subscription-only features.
Do not finalize package amounts or prices until provider unit costs, currency conversion, payment fees, taxes, refunds, and target gross margin are calculated.

Subscription rules:
- Grant subscription allowance once per verified billing period, keyed by provider + subscription reference + period start.
- Do not repeatedly re-grant credits for duplicate RTDN/webhook events.
- Define whether unused subscription credits expire at period end. Recommended: subscription allowance expires at period end; separately purchased credit-pack balance does not expire, subject to published terms and applicable law.
- Cancellation stops future renewals; access remains until the paid-through expiry. Payment failure/grace/hold/expiry must follow verified provider state.
- A plan change is applied from the provider-confirmed effective date. Avoid client-side plan toggles.

## 4. Canonical backend data model
Add migrations (names illustrative; preserve existing wallet/ledger tables):
### payment_orders
- id UUID primary key
- user_id UUID references auth.users
- channel: google_play | iyzico | paytr
- product_id and product_type: credit_pack | subscription
- amount_minor (integer), currency (ISO-4217), credits_to_grant (nullable for subscription)
- status: pending | paid | failed | refunded | partially_refunded | disputed | cancelled
- provider_order_id / provider_payment_id (unique where present)
- idempotency_key (unique)
- created_at, paid_at, updated_at

### payment_events
- id UUID primary key
- provider, provider_event_id (unique per provider), event_type
- payload_hash (do not store unnecessary card or personal data)
- received_at, processed_at, processing_status, failure_reason

### subscriptions
- id UUID primary key
- user_id, provider, provider_subscription_id (unique per provider)
- product_id, status, current_period_start, current_period_end
- cancel_at_period_end, latest_verified_at, created_at, updated_at

### subscription_grants
- id UUID primary key
- subscription_id, user_id, period_start, period_end, credits_granted
- unique(subscription_id, period_start)

### product_catalog
- product_id primary key, product_type, channel, region, currency
- price_minor, credits, subscription_period, active, effective_from
- Store server-side commercial values; do not trust values sent by Android.

### credit_ledger
Retain current ledger as the accounting source. Extend entry types for 'subscription_grant', 'purchase_reversal', and 'expiry' as needed. Add payment_order_id / subscription_grant_id references through a separate migration or reference fields. Never overwrite ledger history.

### wallets
Keep the current balance as a fast materialized balance, but only change it in the same database transaction that writes a ledger entry and records a unique grant/reversal. Add a reconciliation job that compares wallet balance to the ledger.

## 5. Payment lifecycle
### One-time credit pack
1. Client asks backend for product catalog.
2. Client starts Google Play Billing purchase (Play app) or web checkout (web channel).
3. Client sends purchase token / checkout reference to backend.
4. Backend verifies it with the provider and confirms user binding, product, amount, currency, status, and non-refunded state.
5. In one database transaction: insert/mark payment order paid, insert a unique ledger purchase entry, increment wallet balance.
6. Acknowledge/consume Play purchase after successful verification and grant.
7. Return updated wallet balance to the app.
8. On refund/void/chargeback, record a reversal and apply the documented policy; never silently delete history.

### Subscription
1. Client starts provider-native subscription purchase.
2. Backend verifies the initial purchase and creates/updates subscription record.
3. Grant the period's allowance once using unique(subscription_id, period_start).
4. Process RTDN/webhooks and query provider's authoritative subscription state.
5. Renew period allowance only after confirmed successful renewal.
6. On cancellation, keep entitlement through paid-through period; on hold/expiry/revocation, remove subscription-only access and apply the published unused-credit policy.

## 6. Wallet accounting and safety
- All wallet writes occur in database transactions/RPCs under service role only.
- Use idempotency keys for payment grants, renewals, refunds, and provider task settlement.
- Prevent race conditions with row locks and non-negative balance constraints.
- Keep credit reservations for generation; reserve before calling Runway, settle on success, refund on provider failure.
- Add stale reservation/task reconciliation for server restarts and lost polling.
- Distinguish purchased credits from subscription allowance if their expiry/refund rules differ. A single balance can be displayed, but ledger buckets or grant lots must preserve source and expiry.
- Never let a negative balance or client-supplied amount create credits.
- Add rate limits, purchase velocity limits, fraud/voided-purchase monitoring, admin audit logs, and alerts for unusual Runway spend.

## 7. Runway cost and margin controls
- One FOTO FOTO-owned Runway API account/project can serve all users; Runway API spend is charged to that provider account, not to each customer's personal Runway account.
- Attribute every generation to FOTO FOTO user_id, provider task ID, selected model, duration, ratio, estimated provider cost, credits reserved, and final status.
- Maintain a server-side pricing table for each generation mode. Never derive customer credit cost from a client parameter.
- Before accepting a generation, check the user's credit balance and provider account budget/availability.
- Set a daily/monthly Runway spend ceiling and alert before the ceiling. Disable generation safely if the provider key/budget is unavailable.
- Recalculate sell prices when provider costs, FX, taxes, or payment fees change.

## 8. API surface to implement
- GET /api/catalog — return products available to this app/channel/region.
- POST /api/payments/google-play/verify — verify product purchase token and grant once.
- POST /api/payments/iyzico/create — create server-side checkout (web channel only).
- POST /api/webhooks/iyzico — verify signature and process event.
- POST /api/payments/paytr/create — create server-side iframe token (web channel only).
- POST /api/webhooks/paytr — verify callback hash, process once, reply OK.
- POST /api/webhooks/google-play — receive authenticated Pub/Sub RTDN and reconcile with Play Developer API.
- GET /api/billing/status — return wallet, active subscription, purchase history summary.
- POST /api/subscriptions/google-play/restore — re-check current user purchases; never trust a client claim.
- Admin-only reconciliation/report endpoints, protected separately from user routes.

## 9. Rollout plan
### Phase A — billing foundation
- Confirm Google Play Console developer account and app package name.
- Create product IDs for consumable credit packs and monthly subscription tiers.
- Enable Google Play Billing and create service-account/API access.
- Add database migrations and server-side product catalog.
- Implement purchase verification, idempotent grants, RTDN, refunds/voided purchases.
- Add billing screen and restore/manage subscription actions.

### Phase B — Turkey web checkout
- Choose either Iyzico or PayTR after merchant onboarding, fees, settlement and refund terms are compared.
- Implement web checkout and verified webhook/callback.
- Keep web and Play channels labelled separately.
- Validate invoices, tax, consumer rights, privacy and refund policies.

### Phase C — international
- Add regional prices/currencies and eligible payment methods.
- Add Apple IAP before iOS launch.
- Review regional alternative billing rules before enabling external payment flows in Play-distributed builds.
- Add multilingual legal pages, tax handling, support and fraud controls.

### Phase D — launch gates
- Sandbox/test purchases for all products.
- Test duplicate webhook, delayed webhook, payment success but app closed, failed payment, refund, chargeback, subscription renewal, cancellation, grace period, account hold, restore on second device, and network loss.
- Reconcile provider transactions, payment_orders, credit_ledger and wallets.
- Test Runway failure, timeout, insufficient provider budget, duplicate task poll, stale reservation and credit refund.
- Complete signed release AAB, Play Console declarations, Data safety, content rating, privacy policy and customer support.

## 10. Decisions already made
- Market strategy: Turkey first, then international.
- Monetization: one-time credit packs + recurring subscriptions.
- Generation provider: centrally managed FOTO FOTO Runway API account.
- User wallet: FOTO FOTO-owned balance, not Runway credits.
- Android Play-distributed digital purchases: Google Play Billing unless a documented regional exception applies.
- Web checkout: Iyzico or PayTR, to be selected; no provider chosen yet.