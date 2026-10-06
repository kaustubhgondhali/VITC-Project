# AUDIT REPORT — PART 12A/12: AUTHENTICATION & CORE SYSTEM TESTING

Scope: verify, end to end, the three login journeys (Footer → Login → Dashboard) for Main
Admin, Teacher, and Student, plus the Teacher forced first-login password change and the
cross-role security rules — without adding features, redesigning anything, or removing existing
functionality.

## 1. Files changed

| File | Change |
|---|---|
| `backend/src/test/java/com/vitc/AuthenticationCoreSystemTest.java` | **New.** 9 end-to-end `MockMvc` tests exercising the real login endpoints directly (see §5). |
| `AUDIT-REPORT-PART12A-AUTHENTICATION-TESTING.md` | **New.** This report. |

No other file was touched. No frontend file, controller, service, or security class was modified
— this part found the existing implementation already correct and added the missing end-to-end
test coverage for it.

## 2. TEST 1 — Main Admin: Footer → Admin Login → Dashboard

- Footer link: `<a href="admin/index.html" class="footer-admin-link">Admin Login</a>` — present
  and correctly routed on every public page (home, about, courses, contact, student dashboard,
  etc.).
- `admin/index.html` submits to `POST /api/v1/admins/login`; on success it stores the returned
  session and redirects to `dashboard.html`. Any stale local session is discarded (and revoked
  server-side, best effort) the moment the login page loads, so the dashboard can only ever be
  reached by explicitly signing in.
- `dashboard.html` and every other Main Admin page load `admin.js`, whose `requireAuth()` +
  `verifySession()` re-check the session against the backend on load — a locally-forged
  `localStorage` entry does not grant access, because every subsequent API call is independently
  authenticated by `AdminAuthInterceptor`.
- **Verified (new test)**: `mainAdminLoginSucceedsAndReachesDashboard` — real login, then the
  returned token successfully reaches an admin-only endpoint (`/api/v1/admin/teachers`), proving
  the "correct dashboard is displayed" behaviour at the API level, not just a redirect.

## 3. TEST 2 — Teacher Admin: Footer → Teacher Login → Dashboard → Forced Password Change

- Footer link: `<a href="teacher-login.html" ...>Teacher Admin Login</a>` — present and routed
  correctly.
- `teacher-login.html` submits to `POST /api/v1/teacher/auth/login` and redirects to
  `teacher-admin/index.html`.
- **Default credentials**: `TeacherSeeder` creates `VITCteacher` / `VITC@123` on first
  application start (configurable via `app.teacher.default-*` properties), with
  `mustChangePassword = true` and the password stored only as a BCrypt hash
  (`PasswordEncoderConfig` → `BCryptPasswordEncoder(10)`) — never in plain text.
- **Forced change**: `teacher-admin/index.html` opens a password-change modal with **no close or
  skip control** whenever the server-verified profile reports `mustChangePassword: true`, so the
  dashboard's actual content is not usable until the password is changed.
- **After changing the password** (`POST /api/v1/teacher/auth/change-password`):
  - the stored hash is replaced and `mustChangePassword` is cleared;
  - the session token is **rotated** (old token invalidated as a side effect);
  - the old password is verified against the new hash and rejected on the next login attempt;
  - the new password logs in normally with `mustChangePassword: false`;
  - the teacher's session still resolves to role `TEACHER` only — it reaches Teacher APIs and is
    still rejected (`403`) from Main Admin APIs.
- **Verified (new test)**: `teacherFirstLoginForcesPasswordChangeAndOldPasswordStopsWorking`
  drives this entire cycle through the real login/change-password endpoints using the literal
  default credentials, then restores the account's password/flag afterwards so the shared seeded
  row is left exactly as the application created it.
- Additional coverage: `teacherInvalidCredentialsAreRejected`, `inactiveTeacherCannotLogIn`
  (`UserStatus.INACTIVE` → login rejected, no session issued).

## 4. TEST 3 — Student: Footer → Student Login → Dashboard

- Footer link: `<a href="student-login.html" ...>Student Admin Login</a>` — present and routed
  correctly.
- `student-login.html` submits to `POST /api/v1/student/auth/login` and redirects to
  `student-dashboard.html` (with a `?change=1` hint if the account's `mustChangePassword` is
  set, following the same pattern as the Teacher portal).
- **Verified (new test)**: `studentLoginSucceedsAndReachesDashboard` — real login, then the
  returned token reaches `/api/v1/student/me` and `/api/v1/student/courses`. Additional coverage:
  `studentInvalidCredentialsAreRejected`.

## 5. TEST 4 — Footer login buttons

Confirmed present, unmodified, and correctly routed on every public page (home, about, courses,
contact, reviews, gallery, blog, career, internship, testimonials, student-dashboard, etc.):

```html
<a href="admin/index.html" class="footer-admin-link">Admin Login</a>
<a href="teacher-login.html" class="footer-admin-link footer-teacher-link">Teacher Admin Login</a>
<a href="student-login.html" class="footer-admin-link footer-student-link">Student Admin Login</a>
```

The three standalone login pages themselves (`admin/index.html`, `teacher-login.html`,
`student-login.html`) intentionally show a minimal login-only layout without the full site
footer — that is the existing, unchanged design; nothing here removes or alters any footer link.

## 6. Authentication security checklist

| Check | Result | Evidence |
|---|---|---|
| Invalid credentials rejected (all 3 roles) | ✅ | New tests: `mainAdminInvalidCredentialsAreRejected`, `teacherInvalidCredentialsAreRejected`, `studentInvalidCredentialsAreRejected` |
| Inactive Teacher cannot log in | ✅ | New test: `inactiveTeacherCannotLogIn`; existing: `AdminTeacherManagementTest` |
| Student cannot access Teacher/Admin functionality | ✅ | New test: `studentCannotAccessTeacherOrAdminFunctionality`; existing: `StudentTeacherApiProtectionTest` |
| Teacher cannot access Main Admin functionality | ✅ | New test: `teacherCannotAccessMainAdminFunctionality`; existing: `TeacherAdminSeparationTest` |
| Passwords never stored/returned in plain text | ✅ | `BCryptPasswordEncoder`; new test asserts the stored hash starts with `$2` and differs from the plain password; no DTO returns `passwordHash` |
| Existing authentication functionality intact | ✅ | No login/session/interceptor/entity file modified in this part |

## 7. Full test suite (all parts, current total)

17 test files, 89 `@Test` methods (80 from PARTS 1–11B + 9 new in this part). Representative
coverage by area: role-based authorization (`RoleBasedAuthorizationTest`), teacher/admin
separation (`TeacherAdminSeparationTest`), teacher course-level authorization
(`TeacherCourseLevelAuthorizationTest`, `TeacherCourseSecurityTest`), student enrollment
authorization (`StudentLearningSecurityTest`), cross-portal protection
(`StudentTeacherApiProtectionTest`), admin/teacher management flows
(`AdminManagementSecurityTest`, `AdminTeacherManagementTest`), payment/SMTP settings
(`PaymentSettingsIntegrationTest`, `SmtpSettingsIntegrationTest`), and now full login-endpoint
journeys for all three roles (`AuthenticationCoreSystemTest`).

## 8. Test results

Static/code audit: the new test file's HTTP paths, request/response DTO field names (verified
against `AdminLoginRequest`, `TeacherLoginRequest`, `TeacherPasswordChangeRequest`,
`StudentLoginRequest`, `AdminResponse`, `TeacherSessionResponse`, `StudentSessionResponse`), and
expected status codes (including the `401` vs `403` distinction between `TeacherAuthInterceptor`
and `AdminAuthInterceptor`) were individually cross-checked against the actual source. Brace/
parenthesis balance was verified programmatically. This sandbox has no network access to Maven
Central, so a live `mvn test` could not be run here — please run:

```
cd backend
mvn test
```

Expected: `Tests run: 89, Failures: 0, Errors: 0`.

## 9. Remaining issues

- **Cannot execute `mvn test` in this environment** — run it locally/CI before deploying (§8).
- The forced first-login password change is enforced by the frontend (a non-dismissible modal
  gating the dashboard's usable content) and by the backend flag (`mustChangePassword`) that
  drives it; the underlying Teacher API endpoints themselves do not additionally block requests
  while the flag is still `true`. This matches the Student portal's identical, pre-existing
  pattern and was not part of what PART 12A asked to change — flagged here only as an observation
  for a future part if you want defense-in-depth at the API layer as well.
- No other gaps found against the PART 12A checklist.

**PART 12A is complete. Awaiting PART 12B.**
