# iyzico Checkout Form setup — FOTO FOTO

## Backend configuration
Set these as private Render environment variables. Never commit them or place them in the Android app:
- `IYZICO_API_KEY` — iyzico API key
- `IYZICO_SECRET_KEY` — iyzico secret key
- `IYZICO_CALLBACK_URL` — `https://YOUR-RENDER-SERVICE.onrender.com/api/webhooks/iyzico`
- `IYZICO_BASE_URL` — sandbox: `https://sandbox-api.iyzipay.com`; live: `https://api.iyzipay.com`

The backend defaults to the sandbox host unless `IYZICO_BASE_URL` is exactly the live host.

## Merchant onboarding
1. Apply for an iyzico merchant account and complete the required business/identity and bank-account verification.
2. Obtain the sandbox API key and secret key from the iyzico merchant/developer panel for testing.
3. Configure the HTTPS callback URL in the iyzico panel if required for your Checkout Form account.
4. Run Supabase migrations 0001–0008 in order. Migration 0007 is retained as a historical database prerequisite; migration 0008 switches the active billing catalog to iyzico.
5. Add approved credit packs to `public.billing_products` with `channel='iyzico_checkout'`, `currency='TRY'`, price in kuruş (`price_minor`), and credit quantity. Keep `active=false` until tests pass.

## Checkout flow
1. The signed-in app requests `GET /api/catalog`.
2. The app starts a payment with `POST /api/payments/iyzico/create`, sending `productId` and a `buyer` object containing `name`, `surname`, `identityNumber`, `gsmNumber`, `address`, `city`, and optional `zipCode`.
3. The backend reads the product price and credit quantity from the database, creates an iyzico Checkout Form session, verifies the initialize response signature, and returns the hosted `paymentPageUrl` plus token.
4. The customer completes payment on the iyzico-hosted page. iyzico posts the token to the callback URL.
5. The backend retrieves the transaction from iyzico, verifies its response signature, validates token/order/basket/currency/amount/payment status and fraud status, then calls the atomic database settlement function.
6. The app checks `GET /api/payments/iyzico/status?conversationId=...` using the signed-in user's session. Only a settled `paid` order reports granted credits.

## Security and test checklist
- [ ] Use sandbox keys and sandbox host first.
- [ ] Verify initialize signature and retrieve signature validation.
- [ ] Test successful payment and verify the wallet and credit ledger increase exactly once.
- [ ] Test failed payment; no credits must be granted.
- [ ] Replay the same callback; no duplicate credit grant must occur.
- [ ] Test wrong amount, token, basket ID, currency, and conversation ID; settlement must fail.
- [ ] Confirm fraud-review status does not grant credits until approved.
- [ ] Verify the callback endpoint returns a 2xx response only after the callback has been processed safely.
- [ ] Test Android app returning from the hosted page and polling payment status.
- [ ] Activate products only after end-to-end testing and commercial/legal review.

## Important
- Never trust the browser redirect alone as proof of payment.
- Never grant credits from client-side state or the hosted-page return URL.
- Keep API and secret keys only in private backend environment variables.
- The current implementation is for one-time credit packs. Recurring subscriptions need the iyzico subscription API and separate plan setup.
- The iyzico backend is prepared for a website or distribution channel where iyzico is permitted. The Android app is currently targeted at Google Play, so the iyzico redirect button is intentionally not exposed in-app pending confirmation of an eligible Google Play alternative-billing programme. For Play distribution, use Google Play Billing unless the app is formally enrolled in an applicable alternative-billing programme.