# AUDIT REPORT — PART 7A: Teacher Authentication Foundation

## Scope
Adds a third, completely independent authentication track — **Teacher Admin** —
alongside the existing Main Admin and Student authentication. Only the
authentication foundation is implemented: login, session verification,
logout, and the forced first-login password change. No teaching/content
functionality is included yet (by design, per spec).

## What already existed (untouched)
- Main Admin auth: `AdminController`, `AdminServiceImpl`, `AdminAuthInterceptor`, `admins` table — **unchanged**.
- Student auth: `StudentController`, `StudentAccountServiceImpl`, `StudentAuthInterceptor`, `users` table — **unchanged behaviour**. The `users` table gained two things (see below) but every existing student column, query, and endpoint is untouched.

## Design decision: reuse, not duplicate
The task asked to reuse the existing authentication architecture wherever
possible. The `users` table already had exactly the shape a Teacher account
needs — a `role` enum, `password_hash`, `must_change_password`, and a
session-token/expiry pair — because the Student portal was built on it. So
Teacher accounts are **rows in the same `users` table**, not a new table:

| Requirement | Where it comes from |
|---|---|
| Role | `UserRole.TEACHER` (new enum constant, added to the existing `role` column) |
| BCrypt password hash | existing `password_hash` column, existing `PasswordEncoder` bean |
| `must_change_password` | **already existed** on `users` (added for the student portal) — reused as-is, not recreated |
| Session token / expiry | existing `student_session_token` / `student_session_expires_at` columns — reused as-is |
| Login identifier | **one new column**: `username` (nullable, unique). `studentLoginId` could not be reused for this because a separate query (`findMaxStudentLoginSequence`) parses it as `VITCSTU#####` and would break on a non-numeric value like `VITCteacher`. |

So the only schema change is the addition of a single nullable `username`
column on `users` — everything else asked for (`must_change_password`)
was already there.

## New backend files
- `entity/enums/UserRole.java` — added `TEACHER`.
- `entity/User.java` — added `username` column.
- `repository/UserRepository.java` — added `findByUsernameIgnoreCase` / `existsByUsernameIgnoreCase`.
- `security/TeacherAuthInterceptor.java` — guards `/api/v1/teacher/**` via `X-Teacher-Username` / `X-Teacher-Token` headers. Structurally identical to `StudentAuthInterceptor`, but checks `role == TEACHER` and is a **separate bean on a separate path** — a Teacher token is never accepted on Admin or Student endpoints and vice versa.
- `dto/request/TeacherLoginRequest.java`, `TeacherPasswordChangeRequest.java`, `TeacherSessionVerifyRequest.java`
- `dto/response/TeacherProfileResponse.java`, `TeacherSessionResponse.java` — neither ever includes `password_hash`.
- `service/TeacherAccountService.java` + `service/impl/TeacherAccountServiceImpl.java` — login / verifySession / logout / profile / changePassword.
- `controller/TeacherController.java` — `POST /api/v1/teacher/auth/login`, `POST /api/v1/teacher/auth/session`, `POST /api/v1/teacher/auth/logout`, `GET /api/v1/teacher/me`, `POST /api/v1/teacher/auth/change-password`.
- `config/TeacherSeeder.java` — idempotent `ApplicationRunner`, same pattern as `AdminSeeder`:
  - Creates `VITCteacher` / `VITC@123` **only if a user with that username does not already exist**.
  - Stores only `passwordEncoder.encode(password)` — the raw password is held in a local variable just long enough to hash it, then discarded; it is **never logged** (unlike `AdminSeeder`, which does log its default admin password — this was intentionally not copied for the teacher seeder).
  - Sets `mustChangePassword = true` on creation only.
  - On every later restart, `existsByUsernameIgnoreCase("VITCteacher")` short-circuits the runner — the password and `mustChangePassword` flag of an existing row are never touched again.

## Changed backend files
- `config/WebConfig.java` — registered `TeacherAuthInterceptor` on `/api/v1/teacher/**`, excluding `/auth/login` and `/auth/session` (the two endpoints that establish/check a session and therefore can't require one).
- `backend/src/main/resources/application.properties` — added `app.teacher.default-*` properties (env-var overridable, same convention as `app.admin.default-*`).

## New frontend files
- `teacher-login.html` (site root) — standalone login page, `noindex`, styled with the existing `admin/assets/admin.css` (no new CSS framework introduced). Posts to `/api/v1/teacher/auth/login` and stores the session under its own `localStorage` key (`vitc_teacher`), completely separate from the admin (`vitc_admin`) and student session keys.
- `teacher-admin/index.html` — minimal protected landing page. Guards itself via `Teacher.requireAuth()`, re-validates the session against the backend, and shows a forced "set a new password" modal when `mustChangePassword` is true. No course/student/content management is included here — intentionally, per spec.
- `teacher-admin/assets/teacher.js` — shared runtime (API helper, session storage, guard, verify, logout). Mirrors `admin/assets/admin.js`'s auth section; does not touch or import it, so nothing in the Main Admin panel changed.

## Security checklist (from the spec)
- [x] Only the BCrypt hash of the Teacher password is stored (`password_hash` column, `BCryptPasswordEncoder(10)`, same bean used everywhere else).
- [x] Plain-text password is never persisted.
- [x] Plain-text password is never returned by any API — `TeacherProfileResponse` / `TeacherSessionResponse` do not carry a password field, and no controller method returns the `User` entity directly.
- [x] Plain-text password is never logged — `TeacherSeeder` logs only the username on creation, never the password.
- [x] `must_change_password` is `true` on initial creation, and is only ever cleared by the teacher successfully calling `change-password`.
- [x] Restarting the app does not recreate, reset, or touch an existing Teacher row (`existsByUsernameIgnoreCase` guard, identical pattern to `AdminSeeder`).
- [x] Main Admin auth, Student auth, Main Admin panel, and Student portal are all unmodified in behaviour.

## Verification performed
- Manually cross-checked every new/changed Java file's imports, method signatures, and entity/column names against the existing codebase for consistency.
- Confirmed no `switch` statement exists over `UserRole` anywhere in the codebase, so adding the `TEACHER` constant cannot break existing exhaustive-switch compilation.
- Confirmed `admin/users.html` renders `role` as a generic badge (no hardcoded enum list), so it displays `TEACHER` rows without any frontend change.
- Brace-balance / structural sanity check run over all new Java files.
- **Could not run a live `mvn compile`/`mvn test` in this environment** — outbound network access here is restricted to a fixed allow-list of domains that does not include Maven Central, so dependencies cannot be resolved offline. Please run `mvn clean verify` (or your IDE's build) once after downloading — the code follows the project's existing, already-compiling patterns exactly (interceptor, service, controller, DTOs, seeder all mirror the Student/Admin equivalents method-for-method), so risk of a compile error is low, but this is a real gap versus a fully verified build and you should confirm it locally.
- `spring.jpa.hibernate.ddl-auto=update` is active in `application.properties` / `application-dev.properties`, so the new `username` column is created automatically on next start against a dev database. **`application-prod.properties` uses `ddl-auto=validate`** — if you run this in "prod" mode, add the `username` column to the `users` table manually first (or switch to `update` for the first deploy).

## How to test after build
1. Start the backend (dev profile). Confirm the log shows `Default teacher account created -> username: VITCteacher` on first start, and nothing on subsequent restarts.
2. Open `teacher-login.html`, sign in with `VITCteacher` / `VITC@123`.
3. Confirm you land on `teacher-admin/index.html` and the "set a new password" modal appears (since `must_change_password = true`).
4. Set a new password, confirm the modal closes and a fresh session is issued.
5. Reload `teacher-admin/index.html` directly — confirm it stays signed in (session token still valid) and the modal does not reappear.
6. Confirm Main Admin (`admin/index.html`) and Student (`student-login.html`) logins still work exactly as before.
