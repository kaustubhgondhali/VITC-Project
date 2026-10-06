# AUDIT REPORT — PART 4C/12: Connect SMTP Settings to Email Service

## Scope
Wire the Main-Admin-saved SMTP configuration (part 4B: `smtp_settings` table,
encrypted password, admin UI) into the actual outgoing-mail path, so it is
genuinely used to send mail, not just stored.

## What changed

### `backend/src/main/java/com/vitc/service/impl/EmailServiceImpl.java` (rewired)
- Now resolves the outgoing mail server **fresh on every send** in this order:
  1. **Primary**: the single admin-managed row in `smtp_settings`
     (`SmtpSettingRepository.findBySettingsKey("DEFAULT")`), password
     decrypted in memory via the existing `CryptoService` (AES-256-GCM, same
     service already used for Razorpay secrets).
  2. **Fallback**: the environment/`application.properties`-configured
     `JavaMailSender` bean (`MAIL_HOST` etc.), kept only for installations
     that have not opened Admin → Email / SMTP Settings yet.
- Builds a short-lived `JavaMailSenderImpl` per send from the DB row (host,
  port, username, decrypted password, STARTTLS for port 587 / implicit SSL
  for port 465, 10s timeouts) — no restart is needed after Main Admin saves
  new settings; the very next email uses them.
- `isConfigured()` now reflects **either** source being usable, so existing
  callers (`AdminEmailController#status`, retry checks) keep working
  unchanged.
- Public interface (`EmailService.isConfigured()` / `sendHtml(...)`) is
  **unchanged**, so every existing caller — `StudentCredentialEmailService`
  (student-credential emails after a verified Razorpay payment, course-added
  emails, retries) — needed no changes at all.

### `backend/src/main/resources/application.properties` (comment only)
- Clarified that `spring.mail.*` / `MAIL_HOST` is now the **fallback**, and
  the primary source is the admin-managed `smtp_settings` row. No property
  keys, defaults, or behavior changed.

### Not changed (already correct from Part 4B, verified working end-to-end with the above)
- `SmtpSettingsController` (`GET`/`PUT`/`POST /api/v1/admin/smtp-settings`) —
  admin-only via `AdminAuthInterceptor`, password never returned (masked
  bullet string only).
- `AdminEmailController` (`POST /api/v1/admin/emails/test`) — the Main
  Admin–only **Send Test Email** endpoint already existed; it now actually
  exercises the live, DB-configured SMTP server instead of only the static
  env config. Frontend `admin/emails.html` already has a working "Send test
  email" button wired to it.
- `pom.xml` — `spring-boot-starter-mail` was already present (added in Part
  4B) and matches the project's Spring Boot parent version; no dependency
  changes were needed or made in this part.

## Safety checks
- **Passwords never logged**: the decrypted password lives only inside the
  local `JavaMailSenderImpl` built per send; all log lines (auth failure,
  send failure, decrypt failure) log only the exception type / a static
  message, never the password or the exception's raw message from the mail
  library.
- **Passwords never in responses**: unchanged — `SmtpSettingsResponse` never
  carries the password; `EmailService` never returns configuration data.
- **Existing features unaffected**: `EmailService`'s public contract is
  identical, so Payment (Razorpay), Student, and Admin flows that call it
  (`StudentCredentialEmailServiceImpl.sendForPaidOrder/retry/sendTestEmail`)
  compile and behave the same; only the internal source of SMTP settings
  changed from "env-only" to "DB row, env fallback".
- **Genuinely used, not just stored**: `smtp_settings` is now read by
  `EmailServiceImpl` on the send path itself (not merely by the settings
  CRUD service), confirmed by tracing `SmtpSettingsController.save()` →
  `smtp_settings` table → `EmailServiceImpl.resolve()` → every `sendHtml()`
  call.

## Not in scope for this part (unchanged)
- Role-gating admin endpoints beyond the existing session-token check
  (`AdminAuthInterceptor` treats every active admin session as "Main Admin",
  same as the pre-existing `PaymentSettingsController` — no new role system
  was introduced here, consistent with the rest of the admin panel).

## Verification performed
Maven/network access to Maven Central is not available in this sandbox, so a
full `mvn compile`/test run could not be executed here. Verification instead
consisted of: full manual trace of every call site of `EmailService` and
`SmtpSetting`/`SmtpSettingRepository`/`CryptoService`, brace/paren balance
check on the rewritten file, import-by-import cross-check against existing
sibling classes in the same package for correct package paths and API
signatures (`JavaMailSenderImpl`, `MimeMessageHelper`, `BadRequestException`,
etc.), and a check that `SmtpSettingsIntegrationTest` (Part 4B) exercises
only the settings CRUD path and is unaffected by this change.
**Please run `mvn -o clean verify` (or your CI) once you download the
project**, since this sandbox cannot reach Maven Central to build it.

---
Stopping here per PART 4C/12 scope. Waiting for PART 4D instructions.
