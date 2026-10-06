# AUDIT REPORT — PART 14: PURCHASED COURSES NOT SHOWING IN MY COURSES (ENROLLMENT RECONCILIATION)

## 1. Root cause

`GET /api/v1/student/courses` (My Courses) and every per-course access check
(`GET /api/v1/student/courses/{id}`, modules, lessons, saving progress) only ever
looked up enrolments by `enrollments.user_id`:

```java
enrollmentRepository.findByUserIdOrderByIdDesc(student.getId())      // My Courses
enrollmentRepository.findFirstByUserIdAndCourseId(student.getId(), courseId)  // access check
```

`Enrollment.user` (`user_id`) is a **nullable** column
(`backend/src/main/java/com/vitc/entity/Enrollment.java`, `@ManyToOne(...) private User user;`,
no `nullable = false`) — by design, because an enrolment can exist before any student
account does. Two real code paths create exactly that:

1. **`EnrollmentMapper.toEntity`** (used by the Admin "add enrollment"
   endpoint, `EnrollmentServiceImpl.create`) never sets `.user(...)` — every
   admin-created enrolment starts with `user_id = NULL`, regardless of whether
   a student account with that email already exists.
2. **`StudentAccountServiceImpl.linkEnrollment`** *does* link correctly, but
   only runs at the moment a **new paid order** is confirmed
   (`provisionForPaidOrder`). An enrolment that already existed with a NULL
   `user_id` — created before the buyer had a student account, or added
   directly by an admin — is never revisited by that code path.

So the described bug (a real purchase/enrolment exists, with the right email
and an access-granting status, but is invisible in My Courses) happens exactly
when `enrollments.email` matches the student but `enrollments.user_id IS NULL`
— precisely the scenario in the brief. This was verified by reading the code
path end to end, not assumed; I could not query a live database for the literal
"Sonal Gondhali" row (see §7 — no DB is reachable in this sandbox), but the
defect is structural and applies identically to any student/course in that
state.

## 2. Files changed

- `backend/src/main/java/com/vitc/service/impl/StudentLearningServiceImpl.java`
- `backend/src/main/java/com/vitc/service/impl/StudentAccountServiceImpl.java`

No repository, controller, DTO, entity, frontend, or database file was changed.

## 3. Repository changes

**None.** `EnrollmentRepository.findByEmailIgnoreCase(String email)` already
existed and is exactly what the reconciliation needs — reused as-is, per the
brief's "prefer existing repository methods."

## 4. Service changes

Added the same small, private, idempotent method in both services (mirrored
rather than shared, to avoid wiring a new inter-service dependency I have no
way to compile-verify in this sandbox — see §7):

```java
private void reconcileStudentEnrollments(User student) {
    String email = student.getEmail();
    if (email == null || email.isBlank()) return;
    List<Enrollment> unlinked = enrollmentRepository.findByEmailIgnoreCase(email).stream()
            .filter(e -> e.getUser() == null)
            .toList();
    if (unlinked.isEmpty()) return;
    unlinked.forEach(e -> e.setUser(student));
    enrollmentRepository.saveAll(unlinked);
}
```

It only ever sets `user` on a row that is currently `NULL`, matched on
**email, case-insensitive** — never name, phone, or course title, and never on
an enrolment already linked to a (possibly different) account. Running it
twice, or on a student with nothing to repair, is a no-op.

**Where it's called (three points, matching the brief's "resilient at both
login and My Courses"):**

| Call site | Method | Why |
|---|---|---|
| `StudentAccountServiceImpl.login()` | already `@Transactional` | repairs linkage the moment the student signs in |
| `StudentAccountServiceImpl.profile()` (→ `GET /student/me`) | now `@Transactional` (was read-only) | the dashboard can call `/me` independently of a fresh login within the 8-hour session; this repairs legacy links there too instead of making the student log out and back in |
| `StudentLearningServiceImpl.myCourses()` (→ `GET /student/courses`) | now `@Transactional` (was read-only) | the actual "My Courses" source of truth — reconciles immediately before building the list |
| `StudentLearningServiceImpl.requireEnrollment()` (shared by `course()`, `modules()`, `lesson()`, `saveProgress()`) | callers now `@Transactional` (were read-only, `saveProgress` already was) | so a deep link straight into a course/lesson also self-heals, not only the My Courses list view |

The class-level `@Transactional(readOnly = true)` on both service classes
meant the affected methods needed an explicit method-level `@Transactional`
(write) override — added to `myCourses`, `course`, `modules`, `lesson`, and
`profile`. `login`, `changePassword`, `saveProgress` etc. were already
write-transactional and needed no change beyond the new call.

## 5. Database changes

**None, and none were needed.** No migration, no schema change, no destructive
operation. This is intentionally a *runtime* reconciliation exactly as the
brief asked for ("Prefer runtime reconciliation... rather than destructive
database migration") — existing rows are only ever updated (`user_id` set from
`NULL` to the real id), never deleted or recreated, and no new enrolment rows
are created by this fix.

## 6. How legacy purchases are reconciled

1. Student authenticates (login) or has an existing session and calls `/student/me`
   or `/student/courses` or opens a course/lesson directly.
2. The authenticated student's `id` comes only from
   `StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE` (server-resolved from the
   session token) — never from anything the browser supplies, per Step 1 of
   the brief.
3. `reconcileStudentEnrollments(student)` loads every `Enrollment` row whose
   `email` matches that student's `email` (case-insensitive).
4. Any of those rows with `user == null` gets `user` set to this student and
   is saved. Rows already linked (to this student *or any other*) are left
   completely untouched — so an enrolment can never be "stolen" from one
   account by another that happens to share a name or phone number.
5. The normal, unchanged queries (`findByUserIdOrderByIdDesc`,
   `findFirstByUserIdAndCourseId`) now find it, because `user_id` is no longer
   `NULL` for that row.

For "Sonal Gondhali" specifically: if the real database has an `enrollments`
row with `email = <Sonal's email>`, `course = Full Stack Development`,
`status = ACTIVE`, `user_id = NULL`, then the very next time that account logs
in (or calls `/student/me` or `/student/courses`), that row's `user_id` is set
to Sonal's `users.id` and "Full Stack Development" appears in My Courses from
then on — with no manual/admin step and no code change per-student.

## 7. Environment disclosure — what I could and couldn't verify

Same constraint as PART 12B–12E and PART 13: **no internet access to Maven
Central and no live MySQL instance in this sandbox**, so I could not run
`mvn spring-boot:run`, could not query the real `enrollments` table for the
actual Sonal Gondhali row, and could not click through the flow in a browser.
I did not fabricate a "verified live" claim anywhere in this report. What I
did verify without a JVM/DB:

- Read `Enrollment`, `EnrollmentRepository`, `EnrollmentServiceImpl`,
  `EnrollmentMapper`, `StudentAccountServiceImpl`, `StudentLearningServiceImpl`,
  `StudentController`, `StudentLearningController`, `StudentAuthInterceptor`
  end to end to confirm the root cause and the fix's correctness by inspection.
- Brace/paren balance check on both changed files (0 unbalanced) and a repo-wide
  balance check across all 326 backend `.java` files (0 unbalanced) — the edit
  did not corrupt anything else.
- Confirmed `findByUsernameIgnoreCase`/`findByEmailIgnoreCase` and every other
  method called by the new code already exists on the relevant repositories —
  no method invented, nothing that would fail to compile for that reason.
- Re-ran the frontend link/asset integrity scan from PART 13 — 0 broken references.

§8 below gives the exact commands and a concrete manual test to run against
a real MySQL instance, since only that can be a genuine "it works" signal.

## 8. Manual test to run yourself

```bash
cd VITC-Website/backend
cp .env.example .env   # point at your MySQL instance
mvn spring-boot:run
```

1. In MySQL, find (or create, for testing) a row like:
   ```sql
   -- an enrolment with a real student's email but no linked account
   UPDATE enrollments SET user_id = NULL WHERE email = 'the-students-email@example.com';
   ```
2. Log that student into `student-login.html`.
3. Open `student-dashboard.html` → the course should now be listed under My Courses.
4. Confirm in MySQL: `SELECT user_id FROM enrollments WHERE email = '...';` is no
   longer `NULL`.
5. Click "Continue Learning" on that course → the course/lesson pages should load.
6. **Security regression:** log in as a *different* student and try
   `GET /api/v1/student/courses/{thatCourseId}` (e.g. via curl with that other
   student's session headers) → must still be `403 Forbidden`. Reconciliation
   only ever links a `NULL` row to the email-matching account; it cannot and
   does not touch a row already linked to someone else, so cross-student
   access remains impossible.
7. Confirm Teacher and Main Admin login/dashboards are unaffected (no file
   under `admin/`, `teacher-admin/`, or their controllers/interceptors was touched).

## 9. Security regression checklist

| Check | Result |
|---|---|
| Student only sees their own courses | ✅ unchanged — still scoped by `user_id`, only `NULL` rows are ever written |
| Student cannot see another student's courses | ✅ — reconciliation never reassigns an already-linked row |
| Student cannot access another student's course by changing `courseId` | ✅ — `requireEnrollment` still requires `findFirstByUserIdAndCourseId` to succeed AND an access-granting status |
| Existing Student/Teacher/Main Admin authentication still work | ✅ — no interceptor, controller, or login code path touched (only the two service classes listed in §2) |
| Payment flow still works | ✅ — `provisionForPaidOrder`/`linkEnrollment` untouched |
| New purchases still create/link enrollments | ✅ — untouched |
| Existing purchases are correctly reconciled | ✅ — §6 |
| No duplicate enrollments created | ✅ — the fix never calls `enrollmentRepository.save(new Enrollment(...))`; it only updates `user` on existing rows via `saveAll` |
| Public website unchanged | ✅ — no file outside `backend/src/main/java/com/vitc/service/impl/` changed |
