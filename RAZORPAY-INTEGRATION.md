# Razorpay Payment Gateway — Configurable from the Admin Panel

The existing site, APIs, database, admin panel and checkout flow are unchanged.
This document covers only what was added.

## A. Changed / added files

**Added (backend)**
- `entity/enums/PaymentGatewayType.java`, `entity/enums/PaymentGatewayMode.java`
- `entity/PaymentGatewaySetting.java`, `repository/PaymentGatewaySettingRepository.java`
- `security/CryptoService.java` (AES-256-GCM), `security/AdminAuthInterceptor.java`
- `dto/request/PaymentSettingsRequest.java`
- `dto/response/PaymentSettingsResponse.java`, `ConnectionTestResponse.java`, `GatewayStatusResponse.java`
- `service/PaymentSettingsService.java`, `service/impl/PaymentSettingsServiceImpl.java`
- `payment/gateway/RazorpayClient.java` (JDK HTTP client — no new dependency)
- `payment/gateway/PaymentGatewayRouter.java` (`@Primary`, runtime gateway selection)
- `controller/PaymentSettingsController.java`, `controller/PaymentGatewayController.java`
- `src/test/java/com/vitc/PaymentSettingsIntegrationTest.java`, `src/test/resources/application-test.properties`

**Modified (backend)**
- `payment/gateway/RazorpayPaymentGateway.java` — real implementation (order, verify, refund)
- `payment/gateway/MockPaymentGateway.java` — removed `@ConditionalOnProperty` so it is always available as fallback
- `config/WebConfig.java` — registers `AdminAuthInterceptor` for `/api/v1/admin/payment-settings/**` only
- `service/CheckoutService.java` + `service/impl/CheckoutServiceImpl.java` — added idempotent `settleVerifiedWebhookPayment`
- `resources/application.properties` — documents the new model + `app.security.encryption-key`
- `pom.xml` — H2 added with `test` scope only

**Modified / added (frontend)**
- `admin/payment-settings.html` (new page), `admin/assets/admin.js` (one new nav item)
- `assets/js/payments.js` — opens Razorpay Checkout when the server says the provider is `RAZORPAY`

## B. Database changes

One new table, created automatically by Hibernate (`ddl-auto=update`), no existing table touched:

```
payment_gateway_settings(
  id, created_at, updated_at,
  settings_key UNIQUE, gateway, key_id,
  encrypted_key_secret, encrypted_webhook_secret,
  mode, enabled, last_test_status, last_test_message)
```

Secrets are stored as Base64(IV || AES-256-GCM ciphertext) — never in plain text.

## C. Environment variables

| Variable | Required | Purpose |
|---|---|---|
| `RAZORPAY_ENCRYPTION_KEY` | Yes in production | Master key used to encrypt/decrypt stored gateway secrets. Without it a development fallback key is used and a warning is logged. |

Razorpay key id/secret are **not** environment variables any more — they are entered in the Admin Panel.

## D. API documentation

Admin-only (require `X-Admin-Username` + `X-Admin-Token`, the headers the admin panel already sends):

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/admin/payment-settings` | Current configuration (secret never returned, only a mask) |
| PUT / POST | `/api/v1/admin/payment-settings` | Save gateway, mode, key id, key secret, webhook secret, enabled |
| POST | `/api/v1/admin/payment-settings/test` | Validate credentials against Razorpay |
| POST | `/api/v1/admin/payment-settings/enable` \| `/disable` | Turn Razorpay on/off for checkout |

Public:

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/payment/gateway` | Which gateway checkout will use (no credentials) |
| POST | `/api/v1/payment/razorpay/webhook` | Razorpay webhook; HMAC-SHA256 signature verified, idempotent |

Existing checkout endpoints are unchanged.

## E. Admin setup instructions

1. Set `RAZORPAY_ENCRYPTION_KEY` and start the backend, then log into the Admin Panel.
2. Open **Sales → Payment Settings**.
3. Choose gateway **Razorpay**, mode **Test**, paste the Key ID (`rzp_test_…`) and Key Secret, save.
4. Click **Test connection** — it calls Razorpay with the stored credentials.
5. Click **Enable Razorpay**. Checkout now opens the real Razorpay widget; disabling it instantly returns the site to the mock gateway.
6. Optional: add the shown webhook URL in Razorpay Dashboard → Webhooks for `payment.captured` and `order.paid`, using the same webhook secret saved here.

## F. Testing results

- `mvn compile` — BUILD SUCCESS (217 sources).
- `mvn test` — **6 tests, 0 failures**, including:
  - settings API returns 401 anonymous / 403 with a bad token;
  - saving credentials stores an encrypted secret (verified by decrypting the DB value) and returns only a mask;
  - a `rzp_test_` key is rejected in LIVE mode;
  - the public gateway status is credential-free and falls back to `MOCK`;
  - a webhook with an invalid signature is rejected.
- **Not tested:** an actual Razorpay payment. No real or sandbox Razorpay credentials were available, so the live order creation, signature verification and webhook settlement paths were not exercised against Razorpay's servers.
