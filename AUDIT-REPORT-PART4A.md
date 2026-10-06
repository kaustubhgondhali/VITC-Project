# AUDIT REPORT — PART 4A/12: Main Admin SMTP Settings UI

## Scope

Added SMTP / outgoing-mail configuration to the **existing** Main Admin
Panel. No second admin panel was created; no payment, student, or existing
admin feature was modified.

## New menu item

`Main Admin → Email / SMTP Settings` (in the existing "System" nav group,
alongside Users / Admins), added to the single shared nav definition in
`admin/assets/admin.js` so it appears in the sidebar on every admin page.

## New files

Backend (mirrors the existing `PaymentGatewaySetting` / `PaymentSettings*`
pattern used for Razorpay credentials):

- `backend/.../entity/SmtpSetting.java` — single-row config; password is
  stored **encrypted only** (`encryptedPassword`), never in plain text.
- `backend/.../repository/SmtpSettingRepository.java`
- `backend/.../dto/request/SmtpSettingsRequest.java` — bean-validated
  request DTO (host, port, username, from email/name required; password
  optional on update).
- `backend/.../dto/response/SmtpSettingsResponse.java` — **never** includes
  the password; only a `passwordConfigured` flag and a masked placeholder.
- `backend/.../service/SmtpSettingsService.java` +
  `service/impl/SmtpSettingsServiceImpl.java` — encrypts the password with
  the existing `CryptoService` (AES-256-GCM, same master key mechanism as
  Payment Settings) before persisting; blank password on save keeps the
  previously stored one.
- `backend/.../controller/SmtpSettingsController.java` — exposes
  `GET/PUT/POST /api/v1/admin/smtp-settings`. Sits under `/api/v1/admin/**`,
  which `WebConfig` already routes through `AdminAuthInterceptor`, so it
  uses the exact same admin session auth as every other admin endpoint —
  no new security code was added or needed.

Frontend:

- `admin/smtp-settings.html` — new admin page, built with the same
  `Admin.renderShell(...)` shell, CSS variables, and card layout as
  `admin/payment-settings.html`. Includes:
  - Fields: SMTP Host, SMTP Port, SMTP Username, SMTP Password / App
    Password, From Email, From Name.
  - Frontend (client-side) validation for all required fields, port range,
    and email format, with inline error messages, before any request is
    sent.
  - The password field is **always rendered empty** when the page loads or
    after a save — the API response never contains a password to populate
    it with in the first place.
  - A read-only "Status" card shows whether SMTP is configured, a masked
    password indicator ("Saved" / "Not set"), and last-updated time — no
    secret values are ever displayed.

## What was intentionally NOT touched

- `EmailServiceImpl` / actual mail-sending code still reads
  `spring.mail.*` from environment variables as before. Wiring the newly
  saved DB-backed SMTP settings into the actual JavaMailSender used for
  sending is deliberately left for a later part so this part stays scoped
  to "the admin UI/config storage," per the instructions.
- `admin/emails.html` (Credential Emails log + "Send test email") is
  unchanged.
- No payment, student, or other existing admin feature/file was modified
  beyond the one-line nav array addition in `admin/assets/admin.js`.

## Security notes

- Password is encrypted at rest with the existing `CryptoService`
  (AES-256-GCM), same mechanism already used for the Razorpay key secret.
- No endpoint returns the stored password, in any form (HTML, JS, or JSON
  API responses) — confirmed by inspecting `SmtpSettingsResponse`, which
  has no password field at all, only `passwordConfigured` (boolean) and a
  fixed bullet-mask string.
- The admin API path (`/api/v1/admin/smtp-settings`) is covered by the
  existing `AdminAuthInterceptor` via the `/api/v1/admin/**` pattern
  already registered in `WebConfig` — no separate auth logic was written.
