# PayTR setup checklist — FOTO FOTO

## Current implementation
The backend has a PayTR iFrame token endpoint and a public callback endpoint. The callback verifies PayTR's HMAC-SHA256 signature and settles a pending order through a database RPC that locks the order and grants credits once. Duplicate callbacks do not grant twice.

## Before testing
1. Register FOTO FOTO as a merchant with PayTR and complete merchant onboarding/approval.
2. In the PayTR merchant panel, retrieve Merchant ID, Merchant Key, and Merchant Salt.
3. Add these as private Render environment variables (never commit them):
   - PAYTR_MERCHANT_ID
   - PAYTR_MERCHANT_KEY
   - PAYTR_MERCHANT_SALT
   - PAYTR_OK_URL
   - PAYTR_FAIL_URL
   - PAYTR_TEST_MODE=1 for approved test-mode transactions
4. Use the actual deployed API hostname for the callback URL: `https://YOUR-RENDER-SERVICE.onrender.com/api/webhooks/paytr`
5. In PayTR Merchant Panel > Destek & Kurulum > Ayarlar, set the notification/callback URL to the endpoint above and HTTPS.
6. Run Supabase migration 0007 after migrations 0001–0006.
7. Create the approved credit pack rows in public.billing_products. Keep active=false until amount, credits, legal terms, and PayTR test have been approved. No prices or credit amounts are assumed in this repository.
8. Test successful payment, failed payment, repeated callback, mismatched amount, and user balance/ledger consistency before activating products.

## Important
- The browser return URLs only display the result to the user. They do not grant credits.
- Only the PayTR server callback grants credits, after signature and amount checks.
- The callback must return the exact plain text `OK` after successful processing.
- Do not enable installment payments in this integration. The order amount check expects the exact one-time amount.
- Do not send merchant credentials through chat, GitHub, screenshots, or the Android app. Configure them only as encrypted/private Render environment variables.
- PayTR credit packs do not implement recurring subscription billing. Subscriptions require a separate approved recurring billing integration. For Play-distributed Android digital subscriptions, Google Play Billing remains the default unless an eligible alternative-billing program applies.