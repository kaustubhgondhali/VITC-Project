# AUDIT REPORT — PART 6/6: COMPLETE VITC REGRESSION TESTING

Scope: verify PARTS 1–5 together, fix only issues those changes could have caused, no new
features, no redesign.

**Method note (read first):** this sandbox has no outbound network access, so I could not run
`mvn spring-boot:run` against a live database, open a real browser, or hit live HTTP endpoints.
Every item below was verified by static analysis instead: reading the actual request/response
code paths end to end, syntax-checking every inline `<script>` block in all 56 HTML files with
a real JS parser, checking every `<script src>` / `<link href>` in the project resolves to a
real file, checking brace balance on all 381 backend `.java` files, and re-tracing the Spring
`@GetMapping`/interceptor chain for every endpoint discussed below. Where that's what "PASS"
means, it's stated explicitly; nothing here is a live click-through and I'm not claiming it is.

## Course Problem

**Root cause:** already fixed in PART 2/6 (backend route ordering) and PART 3/6 (frontend
decoupling). `CourseController` declares `/active` before `/{id}` with a comment explaining
Spring resolves the literal path first regardless, and `loadCourseCatalogue()` in `site.js`
calls `V.api.courses()` (→ `GET /api/v1/courses/active`) directly instead of going through the
combined `V.catalog()` promise that used to also fetch `/assignments/active` — so a failure on
assignments can no longer break the public Courses page. Rendering is a single
`h.innerHTML = items.map(...).join('')` assignment (verified in `site.js`), so no duplicate-
append path exists.

**Fix (this part):** none needed — re-verified only.
**Files changed:** none.
**API verification:** traced `CourseController.active()` → `CourseService.getActive()`; returns
`200` with the active-course list by construction (no auth guard on this endpoint, matching
"public"). Not executed against a live DB in this sandbox.
**Frontend verification:** `admin/courses.html`'s Publish/Hide button toggles `active: !r.active`
and saves via the same course-update endpoint the catalogue reads from; confirmed the `active`
checkbox field exists in the course form config.

## Login Problem

**Student root cause/fix:** fixed in PART 4/6 — `student-login.html`'s form handler now always
calls `clearSession()` on page load before showing the form; unchanged in this part.
**Teacher root cause/fix:** audited in PART 5/6 — no bug found; `teacher-login.html` already
called `Teacher.clearSession()` on load. Unchanged in this part.
**Main Admin root cause/fix:** audited in PART 5/6 — no bug found; `admin/index.html` already
called `Admin.clearSession()` (+ best-effort server revoke) on load. Unchanged in this part.

No login-flow file needed further changes in this part; PART 5/6 already added `?v=p5`
cache-busting to `admin.js`/`admin.css`/`teacher.js`/`teacher.css` as a defensive measure
against a browser holding an older cached copy of those scripts.

## Authentication

- **Session behavior:** three independent `localStorage` keys — `vitc_student_session`,
  `vitc_teacher`, `vitc_admin` — each holding a server-issued token + expiry. Every dashboard
  page calls a `requireAuth()`-style guard on load, and the primary landing page of each portal
  (`student-dashboard.html`, `teacher-admin/index.html`, `admin/dashboard.html`/every
  `Admin.renderShell()` call) additionally re-validates the token against the backend
  (`verifySession()` / `GET /student/me`).
- **Logout behavior:** each role's `logout()` clears its `localStorage` key, calls the matching
  backend logout endpoint to revoke the token server-side, then redirects to that role's own
  login page.
- **Protected dashboard behavior:** confirmed all 19 Main Admin pages route through
  `Admin.renderShell()`/`crudPage()` (→ `requireAuth()`), all 7 Teacher pages call
  `Teacher.requireAuth()`, and the student pages independently re-check via `GET /student/me`.
  A cleared/expired/revoked session is rejected both client-side (immediate redirect) and
  server-side (`AdminAuthInterceptor` / `TeacherAuthInterceptor` / the student equivalent each
  return 401/403 on a bad or revoked token), so this holds even if `localStorage` were forged.
- **Role separation:** `AdminAuthInterceptor` rejects any request that isn't carrying a valid
  `X-Admin-*` pair — a valid `X-Teacher-*`/`X-Student-*` pair gets `403`, not `401`.
  `TeacherAuthInterceptor` mirrors this and additionally checks `role == TEACHER` and
  `status == ACTIVE` server-side. No shared session object or shortcut exists between the three
  header pairs.

## Testing

```text
Public Courses       PASS   (route order + endpoint code verified statically; not hit live)
Course Publishing    PASS   (active-flag wiring verified statically; not hit live)
Student Login        PASS   (code path verified statically; not clicked through in a browser)
Teacher Login         PASS   (code path verified statically; not clicked through in a browser)
Main Admin Login      PASS   (code path verified statically; not clicked through in a browser)
Logout                PASS   (all three logout()s verified: clear + revoke + redirect)
Dashboard Protection   PASS   (guard present on all 19 admin + 7 teacher pages; student pages independently re-check)
Role Separation        PASS   (interceptor logic re-read; distinct header pairs, 403 on cross-role)
Backend Build          UNVERIFIED — see below
Frontend Console       PASS   (0 real JS syntax errors across all 56 HTML files' inline scripts;
                                0 broken script/CSS file references anywhere in the project;
                                every Admin.*/Teacher.* method called from HTML is exported by
                                admin.js/teacher.js — no undefined-function calls found)
```

**Backend Build — why it's UNVERIFIED, not PASS/FAIL:** this sandbox has no network access, so
`mvn compile`/`mvn spring-boot:run` cannot download the project's dependencies (Spring Boot
3.3.4, Java 21, Lombok, etc.) and I could not actually execute a build here. As a substitute I
statically checked all 381 `.java` files for brace balance (all balanced) and confirmed no Java
file was touched in PARTS 5 or 6 — so this build carries exactly the same compile risk as
whatever state you last confirmed the project built in. **Please run `mvn clean install`
yourself** (or `BUILD-WINDOWS.cmd`) after unzipping; if it fails, that failure predates PART 5/6
and I did not introduce it.

## Files Changed (this part)

- `AUDIT-REPORT-PART6-6-COMPLETE-REGRESSION-TESTING.md` — **new**, this report.

No other file was modified in PART 6/6 — every check above passed on the code as delivered at
the end of PART 5/6.

## Database

- **Schema changed:** no.
- **Data changed:** no.
- Nothing in PARTS 5–6 touched any `.sql` file, entity, repository, or seed data.

## Remaining Issues

- **Backend Build** could not be executed in this environment (no network access to resolve
  Maven dependencies) — run it locally to get a real PASS/FAIL before deploying.
- Everything else above was verified as thoroughly as static analysis allows; a live
  click-through in an actual browser against a running backend + database is still worth doing
  once, since that's the one thing I genuinely cannot simulate here.
