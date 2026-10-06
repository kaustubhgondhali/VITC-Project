# AUDIT REPORT — PART 4B/12: SMTP Settings Backend Storage & Security

## Finding: the persistence layer was already implemented in Part 4A

Part 4A ("Main Admin SMTP Settings UI") already delivered the full backend
stack this part asked for, following the project's existing
`PaymentGatewaySetting` / `PaymentSettings*` pattern:

| Requirement | File | Status |
|---|---|---|
| Entity | `backend/.../entity/SmtpSetting.java` | already present |
| Repository | `backend/.../repository/SmtpSettingRepository.java` | already present |
| Request DTO (validated) | `backend/.../dto/request/SmtpSettingsRequest.java` | already present |
| Response DTO (no password) | `backend/.../dto/response/SmtpSettingsResponse.java` | already present |
| Service + impl | `backend/.../service/SmtpSettingsService.java` (+ impl) | already present |
| Controller (`/api/v1/admin/smtp-settings`) | `backend/.../controller/SmtpSettingsController.java` | already present |
| Bean validation | `@NotBlank`/`@Email`/`@Min`/`@Max`/`@Size` on the request DTO | already present |

This part re-verified every one of those files line by line against the
security requirements below, and closed two real gaps that were missing:
a database migration for the new table, and a generic frontend URL
setting. No file listed above needed a behavioural change.

## Security requirements — verified

- **Password never returned by any API** — `SmtpSettingsResponse` has no
  password field at all; only `passwordConfigured` (boolean) and a fixed
  bullet-mask string. Confirmed by a new integration test
  (`SmtpSettingsIntegrationTest`) asserting `$.data.password` does not
  exist in the JSON response.
- **Password never logged** — grepped every log statement in the codebase;
  none reference the SMTP password. The one `log.info(...password...)` hit
  in the whole project is `AdminSeeder` printing the *default admin login*
  on first boot, unrelated to SMTP and out of scope for this part.
- **Never exposed to frontend JavaScript** — `admin/smtp-settings.html`
  never populates the password `<input>` from the API response (the field
  always starts empty; the API has nothing to send it in the first place).
- **Preserve existing password on partial update** — `SmtpSettingsServiceImpl.save()`
  only re-encrypts and overwrites `encryptedPassword` when the incoming
  `password` is non-blank; otherwise the previously stored ciphertext is
  left untouched. Covered by a new test
  (`updatingOtherFieldsWithoutAPasswordKeepsThePreviousOne`).
- **Never commit real credentials** — `backend/.env.example` only contains
  placeholder values (`your-address@gmail.com`, etc.); no real SMTP
  credentials exist anywhere in the repository.
- **Encryption at rest** — the password is AES-256-GCM encrypted with the
  existing `CryptoService` (same master key mechanism, `RAZORPAY_ENCRYPTION_KEY`,
  already used for Razorpay secrets) before it is written to the database.
- **Admin-only access** — `/api/v1/admin/smtp-settings` sits under
  `/api/v1/admin/**`, already routed through `AdminAuthInterceptor` in
  `WebConfig`. No new auth code was needed or added.

## Gaps found and fixed in this part

1. **Missing database migration.** `spring.jpa.hibernate.ddl-auto` is
   `update` in dev (auto-creates the table) but `validate` in prod — so a
   fresh production database would fail to start once this feature is
   deployed, because no table existed for `SmtpSetting` to validate
   against. Added `database/smtp_settings_migration.sql`, an additive,
   re-runnable migration in the same style as the existing
   `database/student_portal_upgrade.sql`, creating the `smtp_settings`
   table with the exact columns `SmtpSetting.java` maps to.
2. **`APP_FRONTEND_URL` not wired up.** The task listed it alongside the
   `MAIL_*` variables as an example of the project's configuration
   mechanism. Added `app.frontend-url=${APP_FRONTEND_URL:http://localhost:5500}`
   to `application.properties` and documented it in `.env.example`, so it
   is available to later parts (e.g. links in outgoing emails) without
   another round of wiring.
3. **No automated test coverage for this endpoint.** Added
   `backend/src/test/java/com/vitc/SmtpSettingsIntegrationTest.java`
   (same shape as the existing `PaymentSettingsIntegrationTest`), covering:
   anonymous/forbidden access, first-save-requires-a-password, the
   response never containing a password, password preserved across an
   update that omits it, and port-range validation.

## What was intentionally NOT touched (unchanged from Part 4A's note)

- `EmailServiceImpl` (the code that actually sends mail) still reads
  `spring.mail.*` from environment variables only. Wiring the DB-backed
  settings saved here into the live `JavaMailSender` is left for a later
  part, exactly as Part 4A already scoped it — this part is storage only.
- No payment, student, or other existing admin feature/file was modified.

## Build verification note

This sandboxed environment has no outbound network access to Maven
Central (`repo.maven.apache.org` is not on the allowed domain list), so a
full `mvn compile`/`mvn test` could not be executed here. Every changed
and pre-existing file was instead reviewed statically: imports, method
signatures, and DTO/entity field names were cross-checked by hand across
the entity, repository, DTOs, service, controller, `ApiResponse`,
`WebConfig`, and `CryptoService`. Please run `mvn clean verify` (or
`./mvnw clean verify` if you add a wrapper) once, on a machine with normal
internet access, before deploying — the new
`SmtpSettingsIntegrationTest` will confirm all of the above end to end.
