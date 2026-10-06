# AUDIT REPORT — PART 2C-1/7: Centralized Audit Log System (Foundation)

## Scope
Implemented ONLY the centralized audit-logging foundation. No controllers,
no UI, no event integrations into existing features, no changes to
authentication, payments, or any existing endpoint behavior.

## 1. New files

- `backend/src/main/java/com/vitc/entity/AuditLog.java`
  New JPA entity, table `audit_logs`. Extends the existing `BaseEntity`
  (same pattern as `EmailDeliveryLog`) for `id` / `createdAt` / `updatedAt`.
  Fields: `userId`, `actorIdentifier`, `role`, `action`, `entityType`,
  `entityId`, `description`, `ipAddress`, `userAgent`.

- `backend/src/main/java/com/vitc/repository/AuditLogRepository.java`
  Spring Data JPA repository. Read methods (`findTop200By...`) are included
  now as foundation for a future admin-facing audit log viewer — no
  controller exposes them yet.

- `backend/src/main/java/com/vitc/service/AuditLogService.java`
  Public contract: `log(action, entityType, entityId, description)` and
  `log(action, description)`. This is the ONLY sanctioned way to write an
  audit row — future parts must call this instead of touching
  `AuditLogRepository` directly.

- `backend/src/main/java/com/vitc/service/impl/AuditLogServiceImpl.java`
  Implementation. Resolves who is acting from `CurrentUserContext` (never
  from request input), pulls IP/User-Agent from the current request, applies
  secret-redaction + length truncation to `description`, and persists.
  Any failure while saving an audit row is caught and logged — it can never
  break the operation being audited (e.g. it will never fail a login).

- `backend/src/main/java/com/vitc/security/RequestMetadataUtil.java`
  Small static helper: current `HttpServletRequest` (via
  `RequestContextHolder`), client IP (X-Forwarded-For, else remote addr),
  and User-Agent (capped at 255 chars). Deliberately kept separate from
  `RateLimitingFilter`'s own IP logic so existing rate-limiting is untouched.

- `database/audit_logs_migration.sql`
  Documentation-only migration matching the project's existing convention
  (`email_delivery_logs_migration.sql`, etc). `spring.jpa.hibernate.ddl-auto=update`
  will also create this table automatically on first startup — this file is
  for anyone who prefers to apply schema manually. `IF NOT EXISTS`, additive
  only, safe to re-run.

## 2. Files changed
None. No existing file was modified.

## 3. Database changes
New table `audit_logs` only (see SQL above). No existing table, column, or
row touched. Auto-created by Hibernate on next startup, or apply the SQL
file manually first if you prefer.

## 4. Audit entity structure
`id, user_id, actor_identifier, role, action, entity_type, entity_id,
description, ip_address, user_agent, created_at, updated_at` — matches the
requested field list plus `actor_identifier` (login id, useful for audit
review since usernames are stable while numeric ids are not memorable).

## 5. AuditLogService
Two overloads: one for events tied to a specific entity, one for events that
aren't (e.g. a login attempt). Identity, IP, and User-Agent are always
derived server-side — never accepted as parameters — so nothing calling this
service can spoof who an event is attributed to.

## 6. Request/security-context handling
Reuses the existing `CurrentUserContext` (thread-local, populated by the
existing auth interceptors, cleared at end of every request by the existing
`CurrentUserContextFilter`). When no authenticated user is present, the
event is recorded with `user_id`/`role`/`actor_identifier` all null —
i.e. as an anonymous security event — rather than being rejected.

## 7. Secret-sanitization approach
Two layers:
1. **Contract-level**: `AuditLogService`'s javadoc is explicit that
   `description` must never contain a secret — this is the primary control,
   same as the existing `EmailDeliveryLog.failureReason` convention.
2. **Defense-in-depth**: `AuditLogServiceImpl.sanitize()` regex-redacts any
   `token=`, `password=`, `secret=`, `api_key=`, `authorization=`, `jwt=`
   style fragment (case-insensitive) before the row is ever persisted, and
   truncates to 500 chars.

No password, reset token, JWT, API key, SMTP password, Razorpay secret,
encryption key, video-token secret, or payment secret is ever a field on
`AuditLog` — the entity has no column that could hold one.

## 8. Build/startup result
**Could not run `mvn compile` / `mvn spring-boot:run` in this sandbox** —
this environment has a JRE but no JDK compiler, no Maven, and no network
access to Maven Central, so the build could not be executed here. The code
was written and manually reviewed against the existing, already-compiling
codebase for API compatibility:
- `BaseEntity`, `EmailDeliveryLog` (entity pattern), `SmtpSettingsServiceImpl`
  (service pattern), `CurrentUser`/`CurrentUserContext`/`Role`
  (all pre-existing, unmodified) were read and matched exactly (record
  accessor names, Lombok annotations, package layout).
- No new Maven dependency is required — only classes already used elsewhere
  in the project (`jakarta.persistence.*`, `lombok.*`,
  `org.springframework.data.jpa.repository.JpaRepository`,
  `org.springframework.web.context.request.RequestContextHolder`, which
  ships with `spring-boot-starter-web`, already a dependency).
- No existing file was edited, so no existing compiled behavior can have
  regressed.

**Please run `mvn clean spring-boot:run` (or a full IDE rebuild) after
extracting this zip**, per the recurring stale-`target/` issue noted in
`TROUBLESHOOTING.md` from earlier parts, and confirm startup is clean before
starting Part 2C-2.

## 9. Remaining audit-log foundation issues / what's intentionally NOT done yet
- No controller/endpoint exposes audit logs yet (no UI).
- No existing service/controller (login, password reset, admin CRUD,
  payments, etc.) calls `AuditLogService` yet — that wiring is the
  "event integrations" work explicitly deferred to a later part.
- No pagination/filtering DTOs — `AuditLogRepository`'s query methods are a
  minimal foundation only.
- No retention/cleanup policy for `audit_logs` yet.
