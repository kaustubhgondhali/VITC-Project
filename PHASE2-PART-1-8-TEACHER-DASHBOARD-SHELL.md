# PHASE 2 — PART 1/8 — Audit Existing Teacher System & Build Dashboard Shell

## 0. Audit finding (read this first)
Before changing anything, the existing project was inspected. It already contains a
**complete, tested Teacher backend** (auth, dashboard stats, courses, content management,
student progress — built across earlier PART 8C/9C/10A/11B/12A work) and a **partial**
Teacher Admin frontend (`teacher-admin/index.html` dashboard + `course-content.html`).

Nothing in this part rebuilds or duplicates that system. All changes below are additive:
a shared sidebar navigation shell, three new frontend pages that consume **existing**
backend endpoints, and one small, additive extension to the dashboard-stats response.

## 1. Files created
- `teacher-admin/my-courses.html` — full "My Courses" list (all courses assigned to the
  teacher), with Published/Draft badges and links into Content Management / Students.
  Calls the existing `GET /teacher/courses` — no new endpoint.
- `teacher-admin/students.html` — course picker + enrolled-students list with a progress
  bar per student. Calls the existing `GET /teacher/courses/{courseId}/students`.
- `teacher-admin/student-progress.html` — course + student picker, lesson-by-lesson
  completion detail. Calls the existing
  `GET /teacher/courses/{courseId}/students/{studentId}/progress`. Can be deep-linked
  with `?courseId=&studentId=` from the Students page.
- `teacher-admin/profile.html` — read-only profile view (name/username/email/last login)
  reusing the existing `GET /teacher/me`, plus the same change-password flow already on
  the dashboard. Nav includes "Profile" because this read is already supported server-side.
- `PHASE2-PART-1-8-TEACHER-DASHBOARD-SHELL.md` — this report.

## 2. Files modified
- `teacher-admin/assets/teacher.js` — added `Teacher.mountSidebar(activeKey)`. Purely
  additive: renders a sidebar into an existing empty `<aside id="teacherSidebar">` using
  the **same CSS classes as the Main Admin sidebar** (`.sidebar/.nav-group/.nav-item/.burger`
  from `admin.css`), so no new visual system was introduced. Wires the sidebar's Logout
  link and the topbar burger button. No existing exports were changed or removed.
- `teacher-admin/index.html` — added the sidebar placeholder + burger button; added three
  new stat cards (Published Courses, Draft Courses, Total Enrolled Students) reading fields
  already returned by the existing `/teacher/dashboard/stats` call; added a "View all
  courses" link to the new My Courses page. No existing stat, API call, or auth logic changed.
- `teacher-admin/course-content.html` — added the sidebar placeholder + burger button and
  one `Teacher.mountSidebar("content")` call; changed the "Back to My Courses" link target
  from `index.html` to the new `my-courses.html`. **No other line in this file was touched**
  — all existing module/lesson/video management logic is untouched.
- `backend/.../dto/response/TeacherDashboardStatsResponse.java` — added three fields:
  `publishedCourses`, `draftCourses`, `totalEnrolledStudents`. Existing five fields
  (`myCourses`, `totalModules`, `totalLessons`, `totalVideos`, `activeStudents`) unchanged.
- `backend/.../repository/EnrollmentRepository.java` — added one read-only query method,
  `countDistinctStudentsByCourseIds(List<Long>)` (same shape as the existing
  `countDistinctStudentsByCourseIdsAndStatus`, minus the status filter). No existing method
  changed.
- `backend/.../service/impl/TeacherDashboardServiceImpl.java` — computes the three new
  numbers from data already loaded in this method (`myCourses`, `courseIds`) plus the one
  new repository call. Existing five calculations are byte-for-byte unchanged.
- `backend/src/test/java/com/vitc/TeacherDashboardStatsIntegrationTest.java` — added
  assertions for the three new fields against the existing fixture (no fixture change).

## 3. APIs created/modified
- No new endpoint. `GET /api/v1/teacher/dashboard/stats` now returns 3 additional fields
  in the same response object; all 5 previous fields are unchanged, so this is backward
  compatible with any existing caller.
- No changes to `/teacher/courses`, `/teacher/courses/{id}/content`, `/teacher/courses/{id}/students`,
  `/teacher/courses/{id}/students/{studentId}/progress`, `/teacher/me`, or any `/teacher/auth/**`
  endpoint — the new frontend pages simply call these as they already exist.

## 4. Authentication changes
None. All new pages call `Teacher.requireAuth()` on load (same as the existing dashboard),
use the existing `vitc_teacher` localStorage session, the existing `X-Teacher-Username` /
`X-Teacher-Token` headers, and the existing `Teacher.logout()` / `Teacher.verifySession()`.
No JWT or alternate session mechanism was introduced.

## 5. Authorization changes
None on the backend. Every new page's data request goes through the existing
`TeacherAuthInterceptor` (resolves `teacherId` from the session only) and existing
per-course ownership checks in `TeacherAuthorizationService` / the course-content and
student-progress services — a course ID or student ID typed into a new page's URL is
re-verified server-side exactly as before; the frontend never supplies a trusted teacher ID.

## 6. Testing performed
- Re-read `TeacherAuthInterceptor`, `TeacherAuthorizationService`, and all Teacher
  controllers/services to confirm identity always comes from the session, never the client.
- Traced every new page's fetch calls against the actual controller mappings (`TeacherCourseController`,
  `TeacherStudentProgressController`, `TeacherController.me`) to confirm no invented endpoints.
- Verified `Course.getActive()` (Lombok `@Getter`) exists and defaults to `true`, so
  Published/Draft counts handle legacy rows with a null flag the same way the entity does.
- Updated and traced `TeacherDashboardStatsIntegrationTest`'s existing two-teacher fixture
  by hand against the new fields (Teacher A: 2 published/0 draft/3 distinct enrolled
  ignoring status; Teacher B: 1 published/0 draft/2 distinct enrolled) — added as new
  assertions in the same test.
- Confirmed no other test or source file constructs `TeacherDashboardStatsResponse`
  positionally (only the one service file does), so extending the record cannot break
  a hidden call site.
- Grepped the whole project for links into `teacher-admin/*.html` from outside that folder
  to confirm the "Back to My Courses" link change doesn't orphan any other page.
- Manual/static review only — `mvn` is not available in this environment, so the backend
  could not be compiled or run here. **Please run `mvn clean test` (or at least
  `mvn clean spring-boot:run`) after extracting, per `backend/TROUBLESHOOTING.md`, before
  relying on this build** — see "Remaining issues" below.

## 7. Remaining issues / things to verify on your machine
- Backend was reviewed line-by-line but not compiled (no network/Maven in this sandbox).
  Run `mvn clean test` first; if `TeacherDashboardStatsIntegrationTest` passes, the new
  fields are wired correctly end-to-end.
- Students / Student Progress pages are functional list/detail views built directly on the
  existing, already-tested endpoints — they were not asked to be pixel-polished beyond the
  existing VITC Admin styling, so treat them as the "shell" this part asked for, not a
  final design pass.
- "Content Management" and "My Courses" nav items intentionally both open `my-courses.html`
  since Content Management is inherently per-course (you must pick a course first) — this
  was a design choice made to avoid a redundant duplicate page; flag if you'd rather it work
  differently.
- Per the brief, no Phase 3/4 work (e.g. grading, messaging, assignment review) was started.
