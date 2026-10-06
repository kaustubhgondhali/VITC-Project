# AUDIT REPORT — PART 7B: Teacher Login + Mandatory Password Change

## Finding
Every requirement in the PART 7B spec was already implemented in PART 7A
("Teacher Authentication Foundation"). This part is therefore a **verification
pass**, not a rebuild. No source file was changed. The sections below map
each spec requirement to the exact existing code that satisfies it.

## 1. Teacher Login (`teacher-login.html`)
- Posts `{username, password}` to `POST /api/v1/teacher/auth/login`.
- `TeacherAccountServiceImpl.login()` looks the user up by username,
  requires `role == TEACHER` and `status == ACTIVE`, then checks the
  password with `passwordEncoder.matches(rawPassword, user.getPasswordHash())`
  — a BCrypt comparison. The raw password is never stored or compared as
  plain text anywhere.
- Default credentials `VITCteacher` / `VITC@123` are created once by
  `TeacherSeeder` (only if that username doesn't already exist), hashed
  immediately with `passwordEncoder.encode(...)` before saving.

## 2. Mandatory Password Change
- `must_change_password` is the existing `users.must_change_password`
  column (`User.mustChangePassword`), set `true` on seed creation.
- Login response (`TeacherSessionResponse.mustChangePassword`) carries this
  flag to the client. `teacher-admin/index.html` opens a full-screen,
  non-dismissable modal (`.modal{position:fixed;inset:0;...}`, no close
  button, no backdrop-click handler) whenever the flag is true — on the
  client value **and** again after a server-verified re-check
  (`Teacher.verifySession()` calling `POST /teacher/auth/session`), so a
  stale/tampered local flag can't skip the modal. Nothing behind the modal
  is reachable while it's open (fixed overlay, z-index 60).

## 3. Password Validation (`POST /api/v1/teacher/auth/change-password`)
Enforced server-side in `TeacherAccountServiceImpl.changePassword()`,
never trusting client-side checks alone:
- **Current password** — `passwordEncoder.matches(currentPassword, user.getPasswordHash())`; mismatch → `400 Current password is incorrect`.
- **New password rules** — `TeacherPasswordChangeRequest` enforces
  `@NotBlank` + `@Size(min=8, max=64)` (same bound as the rest of the
  project's password fields), rejected with a validation error otherwise.
- **Confirm password** — explicit `newPassword.equals(confirmPassword)`
  check; mismatch → `400 New password and confirmation do not match`.
- (Extra hardening already present, not required by spec but harmless:
  new password must differ from the current one.)

## 4. Successful Password Change
`changePassword()`, after all validations pass:
1. `user.setPasswordHash(passwordEncoder.encode(request.newPassword()))` — BCrypt hash replaces the old one.
2. `user.setMustChangePassword(false)`.
3. A fresh session token is issued and `userRepository.save(user)` persists all of it in one transaction.
4. The frontend stores the new token/flag and closes the modal, so the Teacher Admin panel becomes reachable without a second login.
Plain text is never written to the database, a log line, or an API response at any point in this flow.

## 5. Security
- `TeacherProfileResponse` / `TeacherSessionResponse` — no password/hash field exists on either DTO, and no controller path returns the `User` entity directly, so the hash can never leak through the API.
- No frontend JS file contains a password constant; both `teacher-login.html` and `teacher-admin/index.html` only ever read passwords out of form inputs at submit time and send them straight to the API over the request body.
- `TeacherSeeder` logs only the username on first creation — the password variable is used once (to hash it) and discarded; `changePassword` and `login` never log request bodies.
- `TeacherAuthInterceptor` protects every `/api/v1/teacher/**` route except `/auth/login` and `/auth/session`, so there's no route that reaches the dashboard without a valid, still-active session token — and the `mustChangePassword` re-check on that same session-verify call means a session can't be used to dodge the forced change by hitting the dashboard API directly.

## 6. Existing Authentication Protection (regression check)
- `UserRole.TEACHER` is a new enum constant; grepped the codebase for any `switch` over `UserRole` — none exist, so this can't break exhaustive-switch compilation elsewhere.
- Main Admin (`AdminController` / `AdminAuthInterceptor` / `admins` table) — zero files touched.
- Student auth (`StudentController` / `StudentAccountServiceImpl` / `StudentAuthInterceptor`) — zero behavioural changes; `StudentAccountServiceImpl` explicitly requires `role == UserRole.STUDENT` at login, so a Teacher row can never authenticate as a Student, and vice versa (`TeacherAccountServiceImpl` requires `role == UserRole.TEACHER`).
- `AdminStudentServiceImpl` lists students via `userRepository.findByRole(UserRole.STUDENT)` — Teacher rows in the same `users` table are never surfaced in the Student admin list.
- `WebConfig` registers `TeacherAuthInterceptor` on its own path (`/api/v1/teacher/**`) alongside the untouched Admin/Student interceptor registrations — three fully independent guard chains, confirmed by reading the current file (reproduced above).
- Brace-balance check run over every Teacher-related Java file plus `WebConfig.java` and `UserRole.java` — all balanced, no structural edit left half-done.

## 7. Verification
Manual code-path trace performed for all 15 steps in the spec's verification
script (seed → login → forced modal → wrong current password rejected →
mismatched confirm rejected → valid change → flag cleared → dashboard
reachable → logout → re-login with new password → modal no longer forced →
restart → password not reset). Every step is backed by the code cited
above. **A live `mvn spring-boot:run` could not be executed in this
sandbox** — outbound network here is restricted to a fixed domain allow-list
that does not include Maven Central, so dependencies can't be resolved
offline, and there's no local `.m2` cache. This is the one gap versus a
fully executed build; run `mvn clean spring-boot:run` (or your IDE) once
after downloading to get a live confirmation. The code itself follows the
project's existing, already-compiling Admin/Student patterns method-for-
method, so compile risk is low.

## Files touched in this pass
None — PART 7B required no code changes; this report documents the
verification. `AUDIT-REPORT-PART7A.md` remains the authoritative record of
what was built.
