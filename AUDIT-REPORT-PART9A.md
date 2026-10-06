# AUDIT REPORT — PART 9A: Teacher Course Content Structure & Module Management

## 1. Existing architecture inspected first (nothing duplicated)

| Concern | Existing artefact reused | New artefact created? |
|---|---|---|
| Course | `Course` entity (`courses`), `CourseRepository`, `CourseMapper`, `CourseResponse` | No |
| Module | `CourseModule` entity (`course_modules`) with existing `display_order` + `active` columns, `CourseModuleRepository` | No |
| Lesson / Video | `CourseLesson` entity (`course_lessons`) with existing `video_url` column | No |
| Module write logic | `AdminCourseContentService(Impl)` — create/update/move already implemented | No |
| Module DTOs | `AdminModuleRequest`, `AdminModuleResponse`, `AdminCourseContentResponse` | No |
| Teacher ↔ Course link | `Course.teacherId` (PART 8A) | No |
| Teacher session | `TeacherAuthInterceptor` (`/api/v1/teacher/**`) | No |

**No `teacher_modules` / `teacher_lessons` / `teacher_videos` tables, entities, or
columns were created.** No migration, no schema change, no new status field —
module activation uses the existing `CourseModule.active` boolean, ordering uses
the existing `display_order`.

## 2. What PART 9A adds

| File | Purpose |
|---|---|
| `service/TeacherCourseContentService.java` *(new)* | Teacher-facing module API contract. |
| `service/impl/TeacherCourseContentServiceImpl.java` *(new)* | Ownership gate (teacher → assigned course → module) that then **delegates to the existing `AdminCourseContentService`**, so there is exactly one write path for modules. |
| `controller/TeacherCourseContentController.java` *(new)* | `/api/v1/teacher/**` endpoints (see §3). |
| `teacher-admin/course-content.html` *(replaced placeholder)* | Real module management UI: list, add, edit, reorder (↑/↓), activate/deactivate. |
| `teacher-admin/assets/teacher.js` | Added `api.put` / `api.patch` helpers (additive only). |
| `backend/src/test/java/com/vitc/TeacherModuleManagementTest.java` *(new)* | Positive + negative authorisation tests. |
| `backend/src/test/java/com/vitc/PaymentToEmailFlowTest.java` | Pre-existing flaky seed fixed (deleted the user before its enrollment → FK violation). Test-only change. |

## 3. Endpoints (all under the existing teacher session guard)

| Method | Path | Behaviour |
|---|---|---|
| GET | `/api/v1/teacher/courses/{courseId}/content` | Module (+lesson) tree of an assigned course |
| POST | `/api/v1/teacher/courses/{courseId}/modules` | Add module (auto order = count + 1 when omitted) |
| PUT | `/api/v1/teacher/modules/{moduleId}` | Edit module (title, description, order, active) |
| PATCH | `/api/v1/teacher/modules/{moduleId}/move?direction=-1\|1` | Reorder within the course |
| PATCH | `/api/v1/teacher/modules/{moduleId}/status?active=true\|false` | Activate / deactivate |

Lesson and video management are deliberately **not** exposed to teachers yet.

## 4. Security — backend enforced, not frontend

- Teacher identity comes only from the session-resolved `teacherId` request
  attribute published by `TeacherAuthInterceptor`. No endpoint accepts a
  teacher id from the client.
- Every `courseId` / `moduleId` from the browser is treated as untrusted: the
  row is loaded and `course.teacherId` compared with the session teacher.
  Mismatch → `403 ForbiddenException`; missing row → `404`.
- For modules the chain is walked fully: module → its course → that course's
  `teacherId`. So a teacher cannot edit a module id belonging to another
  teacher's course even though the id is valid.
- The query-string `courseId` in `course-content.html` is a convenience only;
  the page renders whatever the backend allows and shows the rejection message
  otherwise.

## 5. Impact on Main Admin and Students — none

- `AdminCourseContentController` / `AdminCourseContentService` unchanged;
  Main Admin keeps full course/module/lesson control including delete.
- Student portal unchanged: it still reads
  `findByCourseIdAndActiveTrueOrderByDisplayOrderAscIdAsc`, so a module a
  teacher deactivates disappears for students and a reordered module shows in
  the new order, all through the existing enrolment/access rules.
- No entity, table, column, seeder, or student/admin endpoint was modified.

## 6. Verification performed

`mvn test` — **33 tests, 0 failures, 0 errors, BUILD SUCCESS.**

`TeacherModuleManagementTest` covers, with two teachers holding disjoint courses:

1. View assigned course content tree — OK
2. Add module (order 1, then order 2 auto-assigned) — OK
3. Edit module — OK
4. Reorder module (moves from order 1 to order 2) — OK
5. Deactivate module (`active=false`) — OK
6. Activate module (`active=true`) — OK
7. All five operations aimed at the *other* teacher's course/module → **403** — OK
8. Unauthenticated request → **401** — OK

Regression suites re-run green: `AdminManagementSecurityTest`,
`StudentLearningSecurityTest`, `TeacherCourseSecurityTest`,
`TeacherDashboardStatsIntegrationTest`, payment/SMTP suites.

## 7. How to run

```bash
cd backend
mvn spring-boot:run          # http://localhost:8080  (Swagger: /swagger-ui.html)
# then serve the site root, e.g.
python3 -m http.server 5500  # open http://localhost:5500/teacher-login.html
```

Teacher Admin → **My Courses** → **Manage Content** on an assigned course.
