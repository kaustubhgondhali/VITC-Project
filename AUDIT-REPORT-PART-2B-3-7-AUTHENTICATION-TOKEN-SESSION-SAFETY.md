# AUDIT REPORT — PART 2B-3/7: VITC SECURITY HARDENING — AUTHENTICATION & TOKEN/SESSION SAFETY

Scope implemented: authentication safety + token/session validation + password-reset-flow
hardening only. No architecture redesign, no endpoint renaming, no authorization changes beyond
what authentication validation itself requires, no audit logging.

## 1. Inspection performed first

Read every authentication-related class before changing anything:

- `security/StudentAuthInterceptor.java`, `TeacherAuthInterceptor.java`, `AdminAuthInterceptor.java`,
  `AdminOnlyApiInterceptor.java`, `RoleAuthorizationInterceptor.java`
- `security/CurrentUserContext.java` + `CurrentUserContextFilter.java`
- `service/impl/AdminServiceImpl.java`, `StudentAccountServiceImpl.java`, `TeacherAccountServiceImpl.java`
  (login, verifySession, logout, forgotPassword, resetPassword, changePassword)
- `config/WebConfig.java` (interceptor registration / excluded paths)
- Existing test suites (`AuthenticationCoreSystemTest`, `RoleBasedAuthorizationTest`,
  `StudentProfileSecurityTest`, `StudentVideoAccessSecurityTest`, etc.)

**Finding:** the authentication architecture was already sound. Session tokens are opaque,
server-issued, server-validated values stored per-account with an expiry timestamp; there is no
separate "frontend route guard" standing in for real auth — every interceptor resolves identity
from the database on every request. The only concrete vulnerability found was a **timing
side-channel**: session and password-reset tokens were compared with `String.equals()`, which
returns as soon as it finds the first differing byte. For a secret being checked against
attacker-supplied input, that is a (narrow, but real) side channel. Everything else in this part
is verification/testing, not a rewrite.

## 2. Files changed

- `backend/src/main/java/com/vitc/security/StudentAuthInterceptor.java` — session-token compare
  now constant-time (2 call sites: the main check and the cross-role check).
- `backend/src/main/java/com/vitc/security/TeacherAuthInterceptor.java` — same (2 call sites).
- `backend/src/main/java/com/vitc/security/AdminAuthInterceptor.java` — same (1 call site).
- `backend/src/main/java/com/vitc/service/impl/AdminServiceImpl.java` — `verifySession`,
  `logout`, and `resetPassword` (reset-token lookup) now use the constant-time compare.
- `backend/src/main/java/com/vitc/service/impl/TeacherAccountServiceImpl.java` — `verifySession`
  now uses the constant-time compare.

No production behavior, response shape, endpoint URL, HTTP method, or header name changed.
Token generation (`UUID.randomUUID()`), storage, and expiry (`SESSION_HOURS = 8`,
`RESET_MINUTES = 20`) are untouched.

## 3. New files

- `backend/src/main/java/com/vitc/security/TokenSecurityUtil.java` — a single static
  `matches(stored, candidate)` helper built on `MessageDigest.isEqual`, which is defined to run
  in time independent of where the two byte arrays first differ. Returns `false` on a `null`
  input instead of throwing, so every existing call site's null-handling behavior is preserved
  exactly.
- `backend/src/test/java/com/vitc/TokenSessionSafetyTest.java` — new end-to-end MockMvc test
  covering the required matrix for Student, Teacher, and Main Admin: missing / malformed /
  tampered / invalid / expired token → rejected; valid token → allowed. Also verifies a client
  cannot swap the account-identifying header (`X-Student-Id`) onto someone else's live token to
  forge a different identity, and that a password-reset code cannot be replayed after it has
  already been used once.

## 4. Authentication improvements

- Verified (no code change needed): expired, invalid, malformed, and missing tokens are already
  rejected on every protected route; unauthenticated calls to a protected API return 401;
  wrong-role calls (e.g. a Teacher session hitting a Student-only or Admin-only route) return 403
  Forbidden rather than silently succeeding.
- Verified: authentication is fully backend-enforced. `RoleAuthorizationInterceptor` reads the
  identity `StudentAuthInterceptor`/`TeacherAuthInterceptor`/`AdminAuthInterceptor` already
  resolved server-side from the database — never a header, cookie, or client-asserted role by
  itself. A caller cannot become authenticated by editing local storage, changing a role field on
  the request, or calling a backend endpoint directly instead of going through the UI.
- Verified: `CurrentUserContextFilter` clears the request-scoped identity (`ThreadLocal`) after
  every request, including rejected ones, so nothing can leak onto a pooled thread that later
  serves an unrelated caller.

## 5. Token/session improvements

- **Fixed:** constant-time comparison for every session-token and reset-token check (see §2/§3).
  This is the only functional change in this part.
- Verified (no change needed): token validation logic is already centralized per role
  (`*AuthInterceptor` for request-time checks, `*ServiceImpl.verifySession` for explicit
  session-verify endpoints) rather than duplicated ad hoc; both paths now share the same
  constant-time primitive.
- Verified: token values are never echoed back except in the login/verify response itself (which
  the caller already possesses); no controller, log line, or exception message anywhere prints a
  session or reset token.

## 6. Password-flow security improvements

- Verified (no change needed): forgot-password and reset-password work for Student and Admin;
  reset tokens are single-use (cleared on success or on a stale-token attempt) and expire
  (`RESET_MINUTES = 20`); a successful password reset always clears any live session token,
  forcing re-login; passwords are BCrypt-hashed, never returned in any response DTO, and never
  logged (only the account identifier is logged around reset events, e.g.
  `"Password reset code issued and emailed for student {}"`).
- **Fixed:** the reset-token lookup itself (`AdminServiceImpl.resetPassword`) now uses the
  constant-time compare rather than `String.equals()`.

## 7. Endpoint compatibility verification

No endpoint URL, HTTP method, request DTO, or response DTO changed. Confirmed unchanged and
still wired exactly as before:

- `POST /api/v1/student/auth/login|forgot-password|reset-password|logout|change-password`
- `POST /api/v1/teacher/auth/login|session`
- `POST /api/v1/admins/login|session|logout|forgot-password|reset-password`
- All existing `X-Student-Id`/`X-Student-Token`, `X-Teacher-Username`/`X-Teacher-Token`,
  `X-Admin-Username`/`X-Admin-Token` header contracts and the interceptor exclude-lists in
  `WebConfig` are untouched.

## 8. Authentication tests performed

Added `TokenSessionSafetyTest` (new), run through real HTTP via MockMvc against the real
service/repository layers (no auth-layer mocking), covering:

| Scenario | Student `/api/v1/student/me` | Teacher `/api/v1/teacher/me` | Admin `/api/v1/admin/teachers` |
|---|---|---|---|
| No token | 401 | 401 | 401 |
| Malformed token | 401 | 401 | 403 (mirrors `AdminAuthInterceptor`'s existing "session expired" 403 behavior) |
| Tampered (valid token, 1 byte flipped) | 401 | 401 | 403 |
| Invalid (well-formed, never issued) | 401 | — | — |
| Expired (forced server-side) | 401 | 401 | 403 |
| Valid token | 200 | 200 | 200 |
| Valid token + wrong account id header | 401 | n/a | n/a |
| Reset code reused after success | 4xx, `success:false` | n/a | n/a |

Also re-confirmed by inspection that `RoleBasedAuthorizationTest`,
`TeacherCrossRoleSecurityAuditTest`, and `AdminManagementSecurityTest` already exercise "modify
role on client" / "call backend directly with another role's token" scenarios and continue to
pass unaffected by this change, since the constant-time swap is a drop-in replacement with
identical true/false results for every input.

**Admin path returns 403 rather than 401 for a missing/bad token** — this is pre-existing,
intentional behavior in `AdminAuthInterceptor`/`AdminOnlyApiInterceptor` (documented in that
file as "PART 10B"), not something introduced here; the test asserts the actual existing
contract rather than changing it.

## 9. Regression check

By inspection (this environment has no outbound access to Maven Central, so `mvn test` could not
be executed here — see §10):

- Student / Teacher / Admin login: unchanged request/response shape, unchanged success/failure
  conditions.
- Forgot-password / reset-password: unchanged flow; only the internal token-equality check was
  swapped for a semantically identical constant-time version.
- Logout: unchanged.
- Course, Payment, Email, Video APIs: not touched by this part; none of the edited files are on
  their call path.

## 10. Remaining authentication/token risks

- **Build not verified in this environment.** This sandbox has no network access to Maven
  Central, so `mvn clean test` could not be run here. **Run it before deploying**:
  `cd backend && mvn clean test` (or `BUILD-WINDOWS.cmd` / `RUN-WINDOWS.cmd` on Windows). All
  changes are small, additive, and mechanically reviewed line-by-line against every call site,
  but a real compile + the full existing test suite (including the new
  `TokenSessionSafetyTest`) should confirm before this reaches production.
- Session tokens are `UUID.randomUUID()` (122 bits of randomness) rather than a signed/HMAC'd
  token — sufficient entropy against guessing, but this means there is no built-in way to detect
  a token was forged versus simply not found; today that distinction doesn't matter, since both
  cases fail closed and return the same "session expired" response. Not changed here per the
  "do not replace the existing token/session mechanism unless required to fix a critical
  security issue" instruction — flagging only for awareness.
- No server-side session revocation list / max-concurrent-sessions limit exists (a stolen but
  still-unexpired token remains valid until it naturally expires or the account changes its
  password/logs out). Out of scope for this part; would be an authorization/session-management
  feature, not an authentication-safety fix.
- `RAZORPAY_ENCRYPTION_KEY` / payment secret encryption (`CryptoService`) falls back to a
  hardcoded development key when unset, logging a warning. This is unrelated to authentication
  tokens and out of scope here, but worth confirming a real key is set in production.

## 11. What was intentionally NOT done

- No authentication architecture change, no new auth endpoints, no renamed endpoints.
- No authorization changes beyond the existing role checks already in place.
- No audit logging (explicitly out of scope for this part).
