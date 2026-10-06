# AUDIT REPORT — PART 5/6: TEACHER & MAIN ADMIN LOGIN FIX

Scope: verify and, where needed, fix the Main Admin and Teacher login pages so that opening
either login page always shows Username / Password / Login and never auto-redirects to a
dashboard because of an old browser session — mirroring the PART 4/6 Student fix — without
touching Student authentication.

## 1. Root cause findings

**Main Admin (`admin/index.html`) and Teacher (`teacher-login.html`): no active bug found.**

Both pages were audited line by line against the same checklist used for the PART 4/6 Student
fix (`vitc_admin` / `vitc_teacher`, `localStorage`, `sessionStorage`, `window.location`,
`window.location.href`, `window.location.replace`, `location.href`, `location.replace`):

- `admin/index.html` already discards any local admin session on load (`Admin.clearSession()`)
  and best-effort revokes it server-side, before the form is shown. The only path to
  `dashboard.html` is the form's `submit` handler after a successful
  `POST /api/v1/admins/login`.
- `teacher-login.html` already discards any local teacher session on load
  (`Teacher.clearSession()`). The only path to `teacher-admin/index.html` is the form's
  `submit` handler after a successful `POST /api/v1/teacher/auth/login`.
- Neither page contains any unconditional `window.location` / `location.href` /
  `location.replace` call outside those two guarded submit handlers.

This matches the existing `AUDIT-REPORT-PART12A-AUTHENTICATION-TESTING.md`, which already found
and end-to-end tested this exact behaviour for all three roles. No duplicate or legacy
authentication script was found anywhere in the project (`admin.js` and `teacher.js` each exist
in exactly one location) that could reintroduce an automatic redirect.

Since no code defect existed to fix, this part focused on (a) re-verifying every protected page
still enforces the guard, and (b) closing the one real residual risk — a browser holding an
older, previously-buggy copy of `admin.js` / `teacher.js` in its cache — with cache-busting.

## 2. Files changed

| File(s) | Change |
|---|---|
| 19 files under `admin/*.html` | Added `?v=p5` cache-busting query to `assets/admin.js` and `assets/admin.css` references. |
| 8 files under `teacher-admin/*.html` | Added `?v=p5` cache-busting query to `assets/teacher.js` / `assets/teacher.css` references. |
| `teacher-login.html`, `student-login.html` | Added `?v=p5` to the shared `admin/assets/admin.css` reference. |
| `AUDIT-REPORT-PART5-6-TEACHER-MAIN-ADMIN-LOGIN-FIX.md` | **New.** This report. |

No authentication logic, controller, interceptor, entity, or Student file was modified.

## 3. Session changes

None required — `Admin.clearSession()` / `Teacher.clearSession()` on login-page load, and
`Admin.requireAuth()` / `Teacher.requireAuth()` + `verifySession()` on every dashboard page,
were already correct and are unchanged.

## 4. Required authentication flow — verified

**Main Admin:** `admin/index.html` → `POST /api/v1/admins/login` → session stored
(`vitc_admin` in `localStorage`, token from the backend) → `dashboard.html`. Every Main Admin
page calls `Admin.renderShell()` (directly, or via `Admin.crudPage()`), which calls
`requireAuth()` (redirects to `index.html` if no local session) and `verifySession()`
(server-side check via `POST /api/v1/admins/session`; kicks back to `index.html` if the token
is unknown/expired/revoked). Confirmed on all 19 Main Admin pages.

**Teacher:** `teacher-login.html` → `POST /api/v1/teacher/auth/login` → session stored
(`vitc_teacher`) → `teacher-admin/index.html`. Every Teacher page calls `Teacher.requireAuth()`
on load, and `teacher-admin/index.html` additionally calls `Teacher.verifySession()`
(`POST /api/v1/teacher/auth/session`). Confirmed on all 7 Teacher Admin pages.

## 5. Logout verification

- Main Admin: `Admin.logout()` clears `localStorage`, best-effort calls
  `POST /api/v1/admins/logout` (revokes the token server-side), then sends the browser to
  `index.html`.
- Teacher: `Teacher.logout()` clears `localStorage`, calls `POST /api/v1/teacher/auth/logout`,
  then sends the browser to `../teacher-login.html`.
- Direct dashboard access after logout: with `localStorage` cleared, `requireAuth()` redirects
  immediately; even if a token were replayed, `AdminAuthInterceptor` / `TeacherAuthInterceptor`
  independently re-validate it against the (now revoked) server-side session and return
  401/403, so the API calls the dashboard depends on fail regardless of any client-side state.

## 6. Role separation verification

Enforced server-side, not just in the browser:

- `AdminAuthInterceptor` requires `X-Admin-Username` / `X-Admin-Token`; a request carrying only
  `X-Teacher-*` or `X-Student-*` headers is rejected with `403 Forbidden`.
- `TeacherAuthInterceptor` requires `X-Teacher-Username` / `X-Teacher-Token`, resolves the
  account server-side, and rejects (`403`) unless `role == TEACHER` and `status == ACTIVE` — a
  valid Main Admin or Student session is authenticated but not authorised here.
- The three roles use distinct `localStorage` keys (`vitc_admin`, `vitc_teacher`,
  `vitc_student_session`) and distinct header pairs, so the browser has no shared session
  object that could leak across roles; the backend checks above are what actually prevent
  cross-role access even if that were bypassed.

No shared shortcut exists between Main Admin, Teacher, and Student.

## 7. Remaining issues

None found. Main Admin and Teacher login/logout/session/role-separation behaviour already
matched the required flow; the only change made was defensive cache-busting on the shared
front-end assets.
