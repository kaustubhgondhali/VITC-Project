# AUDIT REPORT — PART 8B: Teacher My Courses & Course Assignment

## 1. What was inspected before writing anything

Per the spec's instruction to inspect the existing architecture first:

- **`Course` entity** (`backend/.../entity/Course.java`) already has a plain
  nullable `teacherId` column (added in PART 8A), deliberately *not* a JPA
  relationship, so no existing course row/query/response changes shape.
- **`CourseRepository`** already has `findByTeacherId(Long)`,
  `countByTeacherId(Long)`, `findByTeacherIdIsNull()` (PART 8A).
- **`TeacherAuthInterceptor`** already guards `/api/v1/teacher/**` and
  publishes the authenticated teacher's id as the `teacherId` request
  attribute — the same mechanism `TeacherDashboardController` already uses.
- **`TeacherCourseAssignmentSeeder`** already establishes a real Teacher ↔
  Course assignment for demo/dev purposes (assigns unassigned courses to the
  first seeded teacher, once, and only touches rows with `teacher_id IS
  NULL`).
- `course_modules` / `course_lessons` / `CourseModule` / `CourseLesson` /
  `AdminCourseContentService` (Main Admin's existing content editor) were
  reviewed as the "content management" architecture that already exists.

**Conclusion: the Teacher ↔ Course relationship already exists** (PART 8A).
Per the spec's own instruction ("if assignment functionality already
exists, reuse it — do not create another Teacher-Course relationship"),
**no new relationship, column, or table was created in this part.**

## 2. What PART 8B actually added

Only the pieces PART 8A explicitly left out of scope: a **read path** for
the teacher to see and open their own assigned courses.

### Backend (new files, all under the existing `/api/v1/teacher/**` auth boundary)

| File | Purpose |
|---|---|
| `service/TeacherCourseService.java` | Interface: `myCourses(teacherId)`, `myCourseById(teacherId, courseId)` |
| `service/impl/TeacherCourseServiceImpl.java` | Implementation — reuses `CourseRepository` + `CourseMapper` + `CourseResponse` (the exact DTO Main Admin's `CourseController` already returns). No second course DTO/entity. |
| `controller/TeacherCourseController.java` | `GET /api/v1/teacher/courses` and `GET /api/v1/teacher/courses/{courseId}` |
| `test/java/com/vitc/TeacherCourseSecurityTest.java` | Authorisation tests (see §4) |

No new entity, no new table, no new migration. `CourseMapper.toResponse`
and `CourseResponse` are reused unchanged.

### Backend security (item 5 of the spec)

`TeacherCourseServiceImpl.myCourseById`:
```java
Course course = courseRepository.findById(courseId)
        .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
if (course.getTeacherId() == null || !course.getTeacherId().equals(teacherId)) {
    throw new ForbiddenException("This course is not assigned to you");
}
```
- `teacherId` always comes from the `TeacherAuthInterceptor`-published
  request attribute (`@RequestAttribute`), **never** from anything the
  client sends — identical pattern to `TeacherDashboardController` and
  `StudentController`.
- Both list (`/teacher/courses`) and single-course
  (`/teacher/courses/{id}`) endpoints are scoped this way, so:
  - the **list** never contains another teacher's / an unassigned course
    (`findByTeacherId(teacherId)` only), and
  - **direct access by id** (e.g. editing the URL/API request to a
    different course id) is independently rejected with `403 Forbidden`
    even if that id is a real, existing course — this is the same
    "reused existing authorization architecture" (`ForbiddenException` →
    `GlobalExceptionHandler` → HTTP 403) already used by
    `StudentLearningServiceImpl` for the equivalent student-side check.
  - a non-existent id gets `404`, not `403`, so the response never leaks
    whether an id exists.
- `GET /api/v1/teacher/courses/**` is covered by the *existing*
  `TeacherAuthInterceptor` registration (`/api/v1/teacher/**` in
  `WebConfig`) — no interceptor/config change was needed.

### Frontend

| File | Change |
|---|---|
| `teacher-admin/index.html` | Added a **"My Courses"** card below the stats grid. Loads `GET /teacher/courses` on page load and renders each assigned course with a **"Manage Content"** button. Empty state ("No courses have been assigned to you yet…") when the teacher has zero assigned courses. |
| `teacher-admin/course-content.html` *(new)* | Destination for "Manage Content". Reads `?courseId=` from the URL, calls `GET /teacher/courses/{id}` to re-verify ownership **server-side** before showing anything, and displays the course title/category/level. Per spec item 6, the full content-management UI is **not** built yet — it shows "Content management for this course is coming soon." while still proving the course id is correctly threaded through from the list to this screen and validated by the backend. |

No changes were made to `teacher-admin/assets/teacher.js` — its existing
`Teacher.api`, `Teacher.requireAuth()`, `Teacher.esc()` helpers were reused
as-is (same pattern as `index.html`'s existing dashboard-stats code).

## 3. Verification performed

### Scenario-based checks (spec item 7)

Implemented as `TeacherCourseSecurityTest` (JUnit + `MockMvc`), mirroring
the existing `StudentLearningSecurityTest` pattern:

1. **`assignedCourseAppearsForOwningTeacher`** — Teacher A, assigned to
   "Test Teacher Java": `GET /teacher/courses` returns exactly that one
   course; `GET /teacher/courses/{javaId}` returns `200`.
2. **`unassignedCourseIsHiddenAndDirectAccessIsForbidden`** — Teacher A's
   course list does **not** contain Teacher B's "Test Teacher Python"
   course; manually requesting `GET /teacher/courses/{pythonId}` as
   Teacher A returns `403`.
3. **`unauthenticatedIsRejected`** — both endpoints return `401` with no
   session headers at all.
4. **`nonExistentCourseIsNotFound`** — an id that doesn't exist at all
   returns `404`, not `403`.

These two teacher/course assignment scenarios (Teacher A → Java, Teacher B
→ Python) directly cover every checklist item in spec §7: assigned course
appears, unassigned course doesn't, manual id access is rejected.

### Regression checks (existing functionality)

- `CourseController` (Main Admin + public course catalogue),
  `CourseService`/`CourseServiceImpl`, `CourseMapper`, `CourseRepository`
  — **not modified**. Grepped every other consumer of `Course` /
  `CourseResponse` to confirm nothing was touched.
- `AdminCourseContentController` / `AdminCourseContentService` (Main
  Admin's module/lesson editor) — **not modified**; still the only
  content-management implementation, guarded by the existing
  `AdminAuthInterceptor` as before.
- `TeacherDashboardController` / `TeacherDashboardService` (PART 8A) —
  **not modified**; still computes stats from `Course.teacherId` exactly
  as before.
- `TeacherController` (login/session/logout/profile/change-password) —
  **not modified**.
- `WebConfig` — **not modified**; the new endpoints fall under the
  already-registered `/api/v1/teacher/**` interceptor path pattern.
- No student-portal file (`student-*.html`, `StudentController`,
  `StudentLearningController`, etc.) was touched.

### What was not run

Same limitation as PART 8A: this sandbox has no network route to
`repo.maven.apache.org`, so `mvn clean compile` / `mvn test` could not be
executed here. Every new/changed Java file was manually checked for
brace/parenthesis balance, correct imports, and signatures matching the
interfaces/records they implement or the existing sibling classes
(`TeacherDashboardController`, `CourseServiceImpl`,
`StudentLearningSecurityTest`) they were modelled on. **Please run `mvn
clean compile` and `mvn test` as the first step after unzipping** — if
anything turns up, send the error and it will be fixed immediately.

## 4. Known limitation (unchanged from PART 8A, still by design)

There is still no Main-Admin *screen* to assign/reassign a teacher to a
course — building that was out of scope for PART 8A and is still out of
scope here (the spec for this part only asks for the Teacher-side "My
Courses" view + read verification). `TeacherCourseAssignmentSeeder`
continues to provide the demo assignment on a fresh database, unchanged.

## 5. How to run

1. Unzip the project.
2. `cd backend && mvn clean compile` (first, per above).
3. Copy `backend/.env.example` to `backend/.env` and fill in DB / email
   credentials as already documented in `backend/README.md`.
4. `mvn spring-boot:run` (dev profile — `ddl-auto=update` will add
   `courses.teacher_id` automatically if it isn't already there from a
   previous PART 8A run).
5. Serve the static site (`index.html`, `teacher-admin/`, `admin/`, etc.)
   with any static file server, or open the HTML files directly — they
   already point at `http://localhost:8080` by default (`teacher.js`
   `API_BASE`).
6. Log in at `teacher-login.html` with a seeded teacher account
   (`TeacherSeeder`) and open the dashboard to see **My Courses**.
