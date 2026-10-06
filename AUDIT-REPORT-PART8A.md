# AUDIT REPORT — PART 8A: Teacher Admin Dashboard

## Scope
Build the Teacher Admin dashboard (identity, stats, logout) on top of the
Teacher authentication foundation shipped in PARTs 7A-7C. No changes to
Main Admin, Student Admin, existing authentication, existing course
functionality, or the existing frontend/backend architecture.

## 1. What was added

### Backend
| File | Change |
|---|---|
| `entity/Course.java` | Added a single nullable `teacher_id` column. Plain `Long`, not a JPA relationship - every existing course row, query, and API response keeps working unchanged. |
| `repository/CourseRepository.java` | `findByTeacherId`, `countByTeacherId`, `findByTeacherIdIsNull` |
| `repository/CourseModuleRepository.java` | `countByCourseIdIn` |
| `repository/CourseLessonRepository.java` | `countByCourseIds`, `countVideosByCourseIds` (lessons with a non-blank `videoUrl`) |
| `repository/EnrollmentRepository.java` | `countDistinctStudentsByCourseIdsAndStatus` (distinct by email) |
| `dto/response/TeacherDashboardStatsResponse.java` | New record: `myCourses`, `totalModules`, `totalLessons`, `totalVideos`, `activeStudents` |
| `service/TeacherDashboardService.java` + `impl/TeacherDashboardServiceImpl.java` | Computes the five numbers **only** from `Course.teacherId = <the logged-in teacher>` - never the full catalogue. A teacher with zero assigned courses correctly gets all zeros (no fallback to "all courses"). |
| `controller/TeacherDashboardController.java` | `GET /api/v1/teacher/dashboard/stats`, under the same `/api/v1/teacher/**` base path already guarded by `TeacherAuthInterceptor` in `WebConfig` - no new security wiring needed. Identity comes from the `teacherId` request attribute the interceptor publishes server-side, never from a client-supplied value. |
| `config/TeacherCourseAssignmentSeeder.java` | Dev/demo convenience only (see "Known limitation" below). |

Nothing in `AdminController`, `AdminCourseContentController`, `CourseController`,
`StudentController`, `StudentLearningController`, `TeacherController`,
`TeacherAuthInterceptor`, `TeacherAccountService(Impl)`, or any DTO/mapper
already in the project was modified. `CourseMapper` (used by every existing
Main-Admin course-management call) never reads `teacherId`, so course
create/update/list behaviour for Main Admin is byte-for-byte unchanged.

### Frontend
`teacher-admin/index.html` — the PART 7 placeholder card ("Teacher
Authentication Foundation... not part of this build yet") was replaced with
the real dashboard, built from the *existing* `admin/assets/admin.css`
design system only (same `.layout`/`.topbar`/`.card`/`.grid.stats`/`.stat`
classes the Main Admin dashboard uses) - no new stylesheet, no redesign:

- `<h1>Teacher Admin Dashboard</h1>`
- "Welcome, {name}" - filled from the session the existing
  `Teacher.getSession()` already stores from the login response
  (`fullName`, falling back to `username`), never hard-coded. For the
  seeded default account this renders as `Welcome, VITC Teacher` /
  `Welcome, VITCteacher` depending on which is set.
- Five stat cards (My Courses, Total Modules, Total Lessons, Total Videos,
  Active Students), populated from `GET /teacher/dashboard/stats` via the
  existing `Teacher.api` helper.
- `Logout` button - unchanged behaviour, still calls the existing
  `Teacher.logout()` (clears `localStorage`, calls
  `POST /teacher/auth/logout` which clears the session token server-side,
  redirects to `teacher-login.html`).

`teacher-admin/assets/teacher.js` was **not modified** - `Teacher.api`,
`Teacher.getSession`, `Teacher.logout`, `Teacher.esc` already covered
everything the dashboard needed.

## 2. Requirement-by-requirement

**1. Teacher Dashboard / design system** - Reuses `admin/assets/admin.css`
directly (`<link rel="stylesheet" href="../admin/assets/admin.css">`, was
already the case in PART 7). Same cards, buttons, grid, colours, typography,
responsive breakpoints as Main Admin - see `.stats{grid-template-
columns:repeat(auto-fill,minmax(220px,1fr))}` in that shared stylesheet,
identical to how `admin/dashboard.html` lays out its own stat cards.

**2. Teacher identity** - Never hard-coded. `whoName` /
`welcomeHeading` are set from `Teacher.getSession().fullName /
.username`, which was populated from the server's login/session-verify
response (`TeacherProfileResponse`) - not a literal string in the HTML.

**3. Dashboard statistics, scoped to the teacher** - `dashboardStats()`
starts from `courseRepository.findByTeacherId(teacherId)` and every
subsequent count (`totalModules`, `totalLessons`, `totalVideos`,
`activeStudents`) is derived only from that course-id list. If the list is
empty the method returns immediately with all zeros - it never queries
"all courses" as a fallback.

**4. Logout** - Uses the existing `TeacherAccountServiceImpl.logout()`,
which clears `sessionToken`/`sessionExpiresAt` server-side (already built
in PART 7, unchanged). Two additions to close the "back/cache" gap the
spec calls out explicitly:
- `Cache-Control: no-store, no-cache, must-revalidate` + `Pragma: no-cache`
  meta tags on `teacher-admin/index.html`, and
- a `pageshow` listener that re-checks `Teacher.getSession()` when the page
  is restored from the browser's back-forward cache and redirects to
  `teacher-login.html` if the session is gone.

  This mirrors the same "no other panel in this project already had a
  bfcache guard" gap noted for the whole app (Admin/Student pages don't
  have one either), but PART 8A explicitly requires it for the Teacher
  Dashboard, so it was added here without touching `admin/` or the student
  pages.

  The **real** security boundary remains server-side either way: even if a
  cached page were shown, every dashboard call now carries
  `X-Teacher-Username`/`X-Teacher-Token`, and `TeacherAuthInterceptor`
  rejects any request once the token has been cleared by logout - the
  bfcache guard is UX-layer, not the security layer, exactly like the
  existing `requireAuth()`/`verifySession()` pattern PART 7C documented.

**5. Restrictions**
- Only the logged-in teacher's own courses are ever queried (see #3).
- No Main Admin functionality is exposed: `TeacherDashboardController` only
  calls `TeacherDashboardService`, which only touches
  `CourseRepository`/`CourseModuleRepository`/`CourseLessonRepository`/
  `EnrollmentRepository` read paths already used elsewhere - no admin-only
  service or endpoint is reachable from it.
- No Student Admin functionality is exposed - no student-portal endpoint or
  service is called.
- No course architecture was duplicated - `Course` → `CourseModule` →
  `CourseLesson` is still the only content structure; the dashboard only
  adds read-only aggregation on top of it.
- No new table was created - `teacher_id` is one nullable column on the
  existing `courses` table (`spring.jpa.hibernate.ddl-auto=update` will add
  it automatically on next start, the same mechanism every earlier part
  relied on for schema changes).
- No unrelated page was redesigned - `admin/**` and the student pages were
  not touched.

## 3. Known limitation (by design, not a bug)

There is currently no Main-Admin screen to assign a teacher to a course -
that's out of scope for PART 8A ("Complete only the Teacher Dashboard
foundation in this part"). Without it, a freshly-seeded database would show
an honestly-empty dashboard (all zeros) for the default teacher, since no
course would be assigned to anyone.

To make the dashboard demonstrable without that admin screen,
`TeacherCourseAssignmentSeeder` runs once on `ApplicationReadyEvent` (after
every other startup seeder has finished) and assigns any still-unassigned
courses to the first seeded Teacher Admin - **only** if that teacher
currently owns zero courses, and **only** touching courses whose
`teacher_id` is still `null`. It never reassigns a course away from a
teacher, and it can be turned off with
`app.teacher.demo-assign-courses=false` (e.g. for production, once a real
assignment screen exists). This is dev/demo convenience, not part of the
dashboard's security model - the model is "whatever `Course.teacherId`
says", full stop.

## 4. Verification performed
- Full manual review of every new/changed file for syntax correctness
  (brace balance, import completeness, method signatures against the
  interfaces/records they implement).
- Traced every existing consumer of `Course` (`CourseMapper`,
  `CatalogSeeder`, `CourseContentSeeder`, `AdminController`,
  `CourseController`) to confirm none of them reference the new
  `teacherId` field, so none of them change behaviour.
- Confirmed `TeacherAuthInterceptor`'s existing path registration
  (`/api/v1/teacher/**` in `WebConfig`) already covers the new
  `/api/v1/teacher/dashboard/stats` endpoint without any config change.
- **Not** independently verified with a live `mvn compile` / `mvn test` -
  this sandbox has no network access to Maven Central (only a fixed set of
  package registries, none of them `repo.maven.apache.org`), so
  `spring-boot-starter-parent` itself can't be resolved here. Please run
  `mvn clean compile` (or open the project in your IDE) as the first step
  after unzipping, before anything else - if that turns up any issue, send
  the error and it'll get fixed immediately.
