# AUDIT REPORT — PART 15 (= the request's "PART 1/6"): WHY MY COURSES NEVER LOADS

**This part is diagnosis only, per the brief's own instruction ("Wait for the
next part after completing this audit"). No code was changed in this part.**
Every finding below is traced from the actual source in the uploaded project,
not guessed.

## ROOT CAUSE

`student-dashboard.html`'s boot sequence (`assets/js/student.js`, the
`DOMContentLoaded` handler for `#studentDashboard`) never calls the courses
API at all when the authenticated student's profile has
`mustChangePassword: true`:

```js
// assets/js/student.js, boot()
profile = await StudentAuth.me();          // GET /api/v1/student/me

if (profile.mustChangePassword) {
    loadingBox.style.display = "none";
    changeCard.style.display = "block";
    return;                                // <-- stops here, every time
}

// render(profile) — the ONLY place that calls StudentAuth.courses()
// (GET /api/v1/student/courses) — is never reached above this line.
await render(profile);
```

This is exactly why DevTools shows **no Student Course API request at all**
(not a failed request, not a 403/404 — genuinely never sent) and why the page
shows "Set your own password" regardless of the `#studentCourses` URL
fragment: `#studentCourses` is a plain same-page anchor link (`<a
href="#studentCourses">My Courses</a>` in the subnav) with **no JavaScript
hash router behind it anywhere in the project** — I grepped
`assets/js/*.js` for `hashchange`/`location.hash` and found zero matches — so
it can only scroll to an element that exists; it cannot force the dashboard
past the password-change gate.

**Why would `mustChangePassword` be `true` for an account that has a real,
active enrolment?** Traced in `StudentAccountServiceImpl`:

- `User.mustChangePassword` defaults to `false` at the Java field level, and
  the DB migration that added the column
  (`database/student_portal_upgrade.sql`) backfills existing rows with
  `DEFAULT 0` (false).
- But `provisionForPaidOrder()` — the code path that creates a **brand-new**
  student account at the moment a course purchase is confirmed — explicitly
  sets `.mustChangePassword(true)` on that new account, and the only place
  that ever flips it back to `false` is a successful call to
  `POST /student/auth/change-password`.

So a student who purchased a course (their account was auto-created with a
temporary password and `mustChangePassword = true`) and has **not yet
completed** the forced password-change step will be shown the "Set your own
password" card on *every* visit to the dashboard, and My Courses will *never*
be fetched for them — no matter how many valid, `ACTIVE` enrolments they have.
This is consistent with every symptom in the brief: login succeeds, the
dashboard loads, no console error, no course API call, "Set your own
password" is what's actually showing.

I could not query the live database for the real value of
`users.must_change_password` for `user_id = 4` / `VITCSTU10003` in this
sandbox (no DB access — see "Environment" below), so I cannot personally
confirm this is Sonal's exact value versus some other, rarer path (e.g. a
transient `/student/me` failure, or the profile response failing to parse).
The single query to run to confirm it is given in "Required verification"
below.

## FRONTEND ISSUE

None of the *rendering* code is broken. `renderCourses`, `courseCard`,
`renderStats`, `renderContinue` are all correct and all driven by the real
`GET /api/v1/student/courses` response, per the brief's Step 17 ("no fake
static course cards" — confirmed true, there are none). The issue is purely
in `boot()`'s **control flow**: the password-change branch is a dead end that
never falls through to loading courses, and there is no way for the student
to reach My Courses from that screen except by actually completing the
password change form.

## BACKEND ISSUE

None found. `GET /api/v1/student/courses` → `StudentLearningServiceImpl.myCourses()`
exists, is correctly wired (`StudentLearningController`), and — per the PART 14
fix already in this project — now also self-heals legacy/unlinked enrolments
before returning the list. The backend is simply never asked, because the
frontend never gets past the gate above it.

## DATABASE ISSUE

None structurally. The schema and PART 14's reconciliation already handle a
`NULL` `user_id` on Sonal's enrolment. The only database-shaped question left
open is the actual current value of `users.must_change_password` for her row
— see "Required verification."

## AUTHENTICATION ISSUE

None. Login (`POST /student/auth/login`), `StudentAuthInterceptor`, and
`GET /student/me` all work as designed — the profile *is* successfully
retrieved (that's how the frontend even knows `mustChangePassword` is true).
This is a post-authentication UX/flow issue, not an auth failure.

## REQUIRED FIX (for the next part — not implemented yet, per this part's scope)

`boot()` needs a path that lets an already-enrolled, already-purchasing
student see and use My Courses even while `mustChangePassword` is still true
— for example, load `courses` in parallel with (or right alongside) showing
the change-password card, or show both sections rather than treating "must
change password" as mutually exclusive with "has courses." This is a decision
about the intended UX (block entirely until changed vs. allow browsing while
nudging) that Part 2+ should confirm before changing the gate.

## Files that will need modification (Part 2+, not touched this part)

| File | Why |
|---|---|
| `assets/js/student.js` | `boot()`'s `mustChangePassword` branch — the actual fix |
| `student-dashboard.html` | only if the two cards (change-password + courses) need to be shown together rather than swapped, some layout/CSS adjustment may follow |

No backend, database, or other frontend file is implicated by this
diagnosis — `StudentLearningServiceImpl`, `StudentAccountServiceImpl`,
`StudentController`, `StudentLearningController`, and the schema are all
already correct for this specific symptom.

## Required verification before/alongside the Part 2 fix

Since I have no access to the live database or a running backend in this
sandbox (see below), whoever applies the Part 2 fix should first confirm:

```sql
SELECT id, student_login_id, must_change_password, status
FROM users WHERE id = 4;                 -- or student_login_id = 'VITCSTU10003'
```

If `must_change_password = 1`, this report's root cause is confirmed exactly.
If it is `0`, the symptom has a different, rarer cause (most likely a
`/student/me` or `/student/courses` request throwing before `render()`
finishes) and Part 2 should re-diagnose from the browser Network tab against
the real account rather than assume this report's cause.

## Environment disclosure

Same constraint noted in PART 12–14: this sandbox has no internet access to
Maven Central and no live MySQL instance, so I could not run the backend or
query the real `users`/`enrollments` rows for Sonal Gondhali. Nothing above
was verified by execution — it was verified by reading `student.js`,
`StudentAccountServiceImpl.java`, `User.java`, and
`database/student_portal_upgrade.sql` end to end and confirming the only
code path that produces `mustChangePassword: true` for an enrolled student,
and the only code path that consumes it on the frontend. This report makes no
claim beyond what those files actually show.

## Files inspected this part (no changes made to any of them)

Frontend: `student-login.html`, `student-dashboard.html`, `assets/js/student.js`,
`assets/js/api.js`, `assets/js/site.js`, `assets/js/main.js`, `assets/css/student-portal.css`.

Backend: `StudentController.java`, `StudentLearningController.java`,
`StudentAccountServiceImpl.java`, `StudentLearningServiceImpl.java`,
`StudentAuthInterceptor.java`, `EnrollmentRepository.java`, `Enrollment.java`,
`User.java`, `EnrollmentStatus.java`, `CourseModule`/`CourseLesson` repositories.

Database: `database/vitc_db_fresh.sql`, `database/student_portal_upgrade.sql`,
`database/seed_data.sql`, `database/student_profile_upgrade_part6.sql`
(grepped for `must_change_password`, `Sonal`, `VITCSTU10003` — the literal
Sonal/VITCSTU10003 row does not exist in these repo-tracked seed files, which
is expected since it's live data in your running database, not fixture data
in this repo).

No duplicate authentication, User entity, Enrollment entity, Course entity,
course API, progress table, or dashboard was created or proposed — this part
only reads and reports, per Task 4/Task 5 of the brief.
