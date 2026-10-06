# AUDIT REPORT — PART 7C: Teacher Admin Access Protection + Final Verification

## Finding
No code changes were required. Every protection PART 7C asks for was
already in place at the end of PART 7B, as a direct structural consequence
of how PART 7A built the Teacher auth track (separate interceptor, separate
role, separate session headers, server-side role checks on every guarded
call). This report is the full verification/regression pass the spec asks
for, with the exact code each conclusion is based on.

## 1. Teacher Admin area protection
`teacher-admin/` is a static folder (this project's frontend is plain
static HTML/CSS/JS — see `README.md`, "upload the whole folder to any
static host" — the same is true of `admin/` and the student pages). A
static host cannot gate file access by role, so — **exactly like `admin/`
and the student portal already do** — protection is two-layered:

- **Client-side redirect (UX layer, not the security boundary):**
  `teacher-admin/index.html` calls `Teacher.requireAuth()` on load, which
  checks for a `vitc_teacher` localStorage session and redirects to
  `teacher-login.html` if absent — then calls `Teacher.verifySession()`,
  which re-checks the session against the backend and redirects if the
  backend rejects it. This is the identical pattern `admin/assets/admin.js`
  and the student pages already use (`requireAuth()` then background
  `verifySession()`); Part 7C says to follow "the existing project's
  authentication behavior", so this was deliberately left as-is rather than
  restructured.
- **Server-side enforcement (the real boundary):** every Teacher API call
  from that page carries `X-Teacher-Username` / `X-Teacher-Token`, and
  `TeacherAuthInterceptor` / `TeacherAccountServiceImpl.verifySession()`
  both require `role == UserRole.TEACHER` and `status == ACTIVE` before
  returning any data. A logged-out visitor, a Main Admin, or a Student
  opening `teacher-admin/index.html` directly gets redirected by the client
  check immediately; even if that check were bypassed, every subsequent API
  call would still be rejected server-side because their token can't
  satisfy the role check below.

## 2. Backend API protection (role-based, not just frontend)
No new Teacher business endpoints were added in this part (none were
required — Part 7C's endpoint-protection clause is conditional: "if
Teacher Admin APIs/endpoints are created"). The existing Teacher endpoints
are already role-gated at the framework level, independent of any frontend
code:

| Path | Protected by | Role required |
|---|---|---|
| `POST /api/v1/teacher/auth/login` | — (issues the session) | must target a `TEACHER` row to succeed |
| `POST /api/v1/teacher/auth/session` | — (verifies the session) | rejects if `role != TEACHER` |
| `POST /api/v1/teacher/auth/logout` | `TeacherAuthInterceptor` | `TEACHER` |
| `GET /api/v1/teacher/me` | `TeacherAuthInterceptor` | `TEACHER` |
| `POST /api/v1/teacher/auth/change-password` | `TeacherAuthInterceptor` | `TEACHER` |

`WebConfig.addInterceptors()` registers `teacherAuthInterceptor` on
`/api/v1/teacher/**`, excluding only the two endpoints that must be public
to establish/check a session. This mirrors the Admin (`adminAuthInterceptor`
on `/api/v1/admin/**`) and Student (`studentAuthInterceptor` on
`/api/v1/student/**`) registrations already present — three independent
interceptor chains, unchanged in this part.

### Why cross-role tokens can't work
- **Main Admin → Teacher:** impossible by construction. `Admin` is a
  separate JPA entity/table (`admins`), completely disjoint from `User`
  (`users`, which holds Students and Teachers). `TeacherAuthInterceptor`
  looks up `X-Teacher-Username` in `UserRepository`, which has no rows for
  Main Admin accounts at all.
- **Student → Teacher:** both are rows in `users`, but
  `TeacherAuthInterceptor` explicitly rejects any resolved user whose
  `role != TEACHER`. Additionally, Student login uses `studentLoginId`
  (format `VITCSTU#####`), not `username`, as its identifier — a Student
  session can't even be presented through the Teacher header shape.
- **Teacher → Admin/Student:** symmetric — `AdminAuthInterceptor` only
  resolves rows from the `admins` table; `StudentAuthInterceptor` rejects
  any resolved user whose `role != STUDENT`.

## 3. Mandatory password-change protection (re-verified, unchanged)
- `must_change_password` is re-checked on the server on every session
  verification, not just trusted from the login response — see
  `TeacherAccountServiceImpl.verifySession()` returning
  `TeacherProfileResponse.mustChangePassword`, which
  `teacher-admin/index.html` checks (`if (profile && profile.mustChangePassword)`)
  in addition to the flag stored at login. This means a browser tab that
  already had a session open before a password change (e.g. two tabs)
  gets re-forced into the modal on its next `verifySession()` call, not
  just at initial login.
- `changePassword()` is the only code path that ever sets
  `must_change_password = false`, and it does so in the same transaction
  as the BCrypt hash replacement — the two can't get out of sync.

## 4. Password exposure review
Checked every Teacher-related file for each point:
- **Plain-text storage** — never; `TeacherSeeder` and
  `TeacherAccountServiceImpl.changePassword()` are the only two places a
  raw password is handled, and both immediately pass it to
  `passwordEncoder.encode(...)` before any persistence call.
- **Returned by APIs** — `TeacherProfileResponse` and
  `TeacherSessionResponse` (the only two DTOs any Teacher endpoint returns)
  have no password/hash field; no controller method returns the `User`
  entity itself.
- **BCrypt hash returned to frontend** — same as above; the hash lives only
  on `User.passwordHash`, which is never serialized into a response DTO.
- **Logged** — `TeacherSeeder` logs only the username
  (`"Default teacher account created -> username: {}"`); no `log.*` call
  anywhere in the Teacher login/session/change-password path includes a
  request body or password value.
- **Embedded in frontend JS** — `teacher-login.html` and
  `teacher-admin/index.html`/`teacher.js` contain no hardcoded credential;
  passwords only ever exist transiently as the value of a password `<input>`
  at submit time, sent straight into the fetch body.
- **Exposed in HTML** — password `<input>` fields are `type="password"`
  by default (toggle button only flips to `type="text"` on explicit user
  click); no password value is ever interpolated into page markup.
- **Default password recreated after restart** — `TeacherSeeder` guards
  with `repository.existsByUsernameIgnoreCase(username)` and returns
  immediately (no-op) if the row already exists; it never updates
  `passwordHash` or `mustChangePassword` on an existing row, only on
  first creation.

## 5. Default account behavior (re-verified, unchanged)
`TeacherSeeder.seedDefaultTeacher()`:
- Creates `VITCteacher` / `VITC@123` (`app.teacher.default-*`, overridable
  by env vars) **only if `existsByUsernameIgnoreCase(username)` is false**.
- Also guards against an email collision (`existsByEmailIgnoreCase`) before
  inserting, so a partially-matching row can't cause a duplicate.
- On every subsequent run (restart), the `existsByUsernameIgnoreCase` check
  short-circuits the whole runner — password and `mustChangePassword` on an
  existing row are never touched again, by construction (the code path that
  would touch them is unreachable once the row exists).

## 6. Regression testing — Main Admin / Student / Teacher
No file in `AdminController`, `AdminServiceImpl`, `AdminAuthInterceptor`,
`admin/**`, `StudentController`, `StudentAccountServiceImpl`,
`StudentAuthInterceptor`, or any student-facing HTML/JS was modified in
Part 7B or this part — confirmed by re-reading each of them in full this
pass. Since PART 7C made no code edits, the regression surface is
identical to PART 7B, which was itself unchanged from PART 7A. Traced
each spec point against the current code:

- **Main Admin** — `AdminController`/`AdminAuthInterceptor` untouched;
  `AdminStudentServiceImpl.getAll()` filters with
  `userRepository.findByRole(UserRole.STUDENT)`, so Teacher rows (same
  `users` table) never appear in the Main Admin's student list, and the
  new `TEACHER` enum constant cannot be hit by any `switch` in the
  codebase (checked — none exists over `UserRole`).
- **Student** — `StudentAccountServiceImpl.login()` requires
  `role == UserRole.STUDENT`; a Teacher row can never authenticate on
  `/api/v1/student/auth/login`. Student pages have no Teacher-aware code
  and were not touched.
- **Teacher** — full login → forced-change → dashboard flow re-traced
  against `TeacherAccountServiceImpl` and `teacher-admin/index.html` (see
  PART 7B's audit for the step-by-step code trace, still accurate since no
  file changed): wrong current password → `400 Current password is
  incorrect`; mismatched confirmation → `400 New password and confirmation
  do not match`; success → new BCrypt hash, `mustChangePassword=false`,
  fresh session, dashboard unlocked. Access to Admin/Student-only endpoints
  is refused because the Teacher token isn't accepted by
  `AdminAuthInterceptor` or `StudentAuthInterceptor` (different table /
  different role, per §2 above).

## 7. Restart test
Re-inspected `TeacherSeeder` and confirmed the guard logic is
restart-idempotent by construction (§5). No test database was available in
this sandbox to run a live multi-restart integration test — this was
verified by code inspection, the same limitation noted in the PART 7A/7B
reports regarding `mvn` not being runnable here (no Maven Central access
in this sandbox's network allow-list). Recommended local check: start the
app, confirm the log line `Default teacher account created -> username:
VITCteacher` appears exactly once across repeated restarts.

## 8. Files changed in this part
**None** — no `.java`, `.html`, `.js`, or `.css` file was modified. This
report (`AUDIT-REPORT-PART7C.md`) is the only file added. No database
schema change, no new dependency, no configuration change.

## Summary for the record
| Area | Change made in Part 7C |
|---|---|
| Database | None |
| Backend (Java) | None |
| Frontend (HTML/JS/CSS) | None |
| Security/config | None |
| Docs | Added this audit report only |

The Teacher Admin area is protected the same way Main Admin and Student
areas already are in this project: client-side redirect for UX, and
mandatory server-side role + session-token verification on every API call
as the actual security boundary. All three authentication tracks (Main
Admin / Teacher Admin / Student) remain fully independent — separate
tables or role checks, separate session headers, separate interceptors —
and none was altered.
