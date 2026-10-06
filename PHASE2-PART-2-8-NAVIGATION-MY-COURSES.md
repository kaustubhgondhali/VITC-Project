# PHASE 2 — PART 2/8 — Complete Teacher Navigation & My Courses

Continues directly from PART 1/8. No new course system, no new auth system, no new
authorization system was introduced — everything below reuses `TeacherCourseController`,
`TeacherCourseService`, `TeacherAuthorizationService`, and `GET /api/v1/teacher/courses`.

## 1. Teacher navigation
The sidebar shell built in Part 1 (`Teacher.mountSidebar`) already covers Dashboard, My
Courses, Content Management, Students, Student Progress, Profile, and Logout across every
Teacher Admin page. No change was needed here beyond what Part 1 shipped — this part just
confirms it's in place on `index.html`, `my-courses.html`, `course-content.html`,
`students.html`, `student-progress.html`, and `profile.html`.

## 2. My Courses — rebuilt as a proper card grid
`teacher-admin/my-courses.html` was reworked from a plain list into a responsive card grid
(`auto-fill, minmax(300px, 1fr)` — wraps cleanly from desktop down to mobile, no horizontal
overflow). Still calls only the existing `GET /api/v1/teacher/courses`.

## 3. Course card contents — only real backend data
Each card shows, using only fields the backend already returns:
- **Icon** — `course.icon` (same emoji-fallback convention already used on the public site
  and student portal: `c.icon || "🎓"`)
- **Title** and **Course code** — `course.title`, `course.code`
- **Category** / **Level** — `course.category`, `course.level`
- **Status** — Published/Draft badge from the existing `course.active` flag (see §6)
- **Number of students** — fetched from the existing `GET /teacher/courses/{id}/students`
  and counted client-side (`list.length`)
- **Number of lessons** — fetched from the existing `GET /teacher/courses/{id}/content`,
  using its existing `lessonCount` field (no new computation invented — that field was
  already being returned, just not consumed by this page before)
- **Last updated** — see §backend change below
- **Manage Content** button → `course-content.html?courseId=`
- **View Students** button → `students.html?courseId=`

"Edit" was deliberately **not** added: `TeacherCourseController` only exposes `GET`
endpoints (list + single course) — there is no teacher-facing endpoint to edit a course's
own metadata (title/price/category/etc.), only to manage its content (modules/lessons/videos),
which "Manage Content" already covers. Adding an Edit button here would have meant either
inventing a new backend write endpoint (out of scope for this part) or a dead button, so it
was left out rather than faked.

## 4. Backend change — one field, additive, already-existing data
- `CourseResponse` gained one field: `updatedAt`. This is **not an invented field** — every
  `Course` row already has a real `updated_at` column, auto-maintained by JPA auditing
  (`BaseEntity.updatedAt` / `@LastModifiedDate`); it just wasn't exposed in the response DTO
  yet. `CourseMapper.toResponse()` now maps it through.
- Confirmed `CourseMapper.toResponse(...)` is the **only** place that constructs a
  `CourseResponse`, so extending the record is safe — no other call site (main or test)
  breaks.
- `CourseResponse` is shared by the public site, student portal, and admin as well as
  teacher — adding one nullable field is backward compatible (existing frontends ignore
  unknown JSON fields); nothing about the public website, payments, or existing course
  pages was touched or redesigned.
- No new endpoint, no schema migration (the column already existed).

## 5. Course ownership — unchanged, re-verified
`TeacherCourseController` / `TeacherCourseServiceImpl` / `TeacherAuthorizationService` were
re-read end to end: the teacher's identity comes only from the `teacherId` request attribute
set by `TeacherAuthInterceptor` from the server-side session — never from the URL or any
client-supplied value. `myCourseById` calls `authorization.requireAssignedCourse(teacherId,
courseId)`, which 403s if the course exists but belongs to a different teacher. This part
changed none of that logic; the existing `TeacherCourseSecurityTest` already covers it.

## 6. Course status — reused the existing mechanism
No new status architecture was introduced. Published/Draft continues to be derived purely
from the existing `Course.active` boolean (added in Part 1, reused here) — a null value is
treated as published, matching the entity's own default (`active = true`).

## 7. Responsive design
The card grid (`auto-fill, minmax(300px, 1fr)`) and the existing `.sidebar`/`.burger`
responsive rules from `admin.css` (already collapsing to an off-canvas menu under 900px)
cover desktop, laptop, tablet, and mobile with no horizontal overflow.

## 8. Empty state
If `GET /teacher/courses` returns an empty list, the page shows: "No courses have been
assigned to you yet. Please contact the Main Admin to get a course assigned to your
account." No placeholder/fake course data is ever rendered.

## Files modified in this part
- `teacher-admin/my-courses.html` — rebuilt as the card grid described above
- `backend/.../dto/response/CourseResponse.java` — added `updatedAt`
- `backend/.../mapper/CourseMapper.java` — maps `updatedAt` through

## Testing performed
- Re-verified `TeacherCourseController`, `TeacherCourseServiceImpl`, and
  `TeacherAuthorizationService` enforce ownership server-side (unchanged, but re-checked
  since this part's requirement 4/8 depend on it).
- Grepped the whole backend for other `new CourseResponse(...)` call sites and for tests
  asserting `CourseResponse`'s shape — found none besides `CourseMapper` itself, so the
  added field cannot break a hidden caller.
- Confirmed `AdminCourseContentResponse.lessonCount` already exists and is exactly what the
  new "lessons" count needed — no backend change required for that number.
- Confirmed `Course.active` defaults to `true` at the entity level, matching the Published
  fallback used in the card and in Part 1's dashboard stats.
- Static/manual review only — Maven is not available in this sandbox, so the backend was
  not compiled here. **Run `mvn clean test` after extracting** to confirm the build is green
  (this part's backend change is a one-field, additive record extension with a single
  mapper call site, low risk, but should still be verified locally).

## Remaining issues
- Per-course student/lesson counts on the My Courses cards are computed via 2 extra API
  calls per card (reusing existing endpoints, as instructed) rather than a new aggregate
  endpoint — fine for a typical teacher's course list size, but flag if you'd rather have a
  single batched endpoint later.
- No Phase 3/4 work started (no grading, messaging, or assignment-review features).
