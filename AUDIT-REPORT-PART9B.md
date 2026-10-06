# AUDIT REPORT — PART 9B: Teacher Lesson Management

## 1. Existing architecture reused (nothing duplicated)

| Concern | Existing artefact reused | New artefact? |
|---|---|---|
| Lesson storage | `CourseLesson` entity → `course_lessons` (already has `title`, `description`, `display_order`, `active`, `video_url`) | No |
| Lesson repository | `CourseLessonRepository` (`findByModuleIdOrderByDisplayOrderAscIdAsc`) | No |
| Lesson write path | `AdminCourseContentService(Impl)` — `createLesson` / `updateLesson` / `moveLesson` already existed | No |
| Lesson DTOs | `AdminLessonRequest`, `AdminLessonResponse` | No |
| Ownership chain | `Course.teacherId` (PART 8A) + `TeacherAuthInterceptor` session | No |
| Content tree | `AdminCourseContentResponse` (modules + lessons) | No |

**No `teacher_lessons` table, entity, repository, DTO or status field was created.**
No migration and no schema change. Ordering uses the existing `display_order`;
activation uses the existing `active` boolean — the very flag the Student Portal
already filters on (`findByModuleIdAndActiveTrueOrderByDisplayOrderAscIdAsc`,
`findActiveByCourseId`), so activate/deactivate immediately and correctly changes
student visibility with no extra rules.

## 2. What PART 9B adds

| File | Change |
|---|---|
| `service/TeacherCourseContentService.java` | + `createLesson`, `updateLesson`, `moveLesson`, `setLessonActive` (additive) |
| `service/impl/TeacherCourseContentServiceImpl.java` | Lesson ownership gate `assertOwnsLesson` (lesson → module → course → teacher) then delegates to the existing `AdminCourseContentService`; video/duration are preserved, never taken from the teacher payload |
| `controller/TeacherCourseContentController.java` | 4 new endpoints under the existing `/api/v1/teacher/**` guard |
| `teacher-admin/course-content.html` | Lesson list per module + add/edit form (title, description, display order, active) + ↑/↓ reorder + Activate/Deactivate |
| `backend/src/test/java/com/vitc/TeacherLessonManagementTest.java` *(new)* | Positive + negative authorisation tests |

Module management from PART 9A is untouched and still works exactly as before.

## 3. Endpoints (all behind the teacher session interceptor)

| Method | Path | Behaviour |
|---|---|---|
| GET | `/api/v1/teacher/courses/{courseId}/content` | Module + lesson tree (existing, unchanged) |
| POST | `/api/v1/teacher/modules/{moduleId}/lessons` | Add lesson (order = count + 1 when omitted, active by default) |
| PUT | `/api/v1/teacher/lessons/{lessonId}` | Edit title, description, display order, active |
| PATCH | `/api/v1/teacher/lessons/{lessonId}/move?direction=-1\|1` | Reorder inside its module |
| PATCH | `/api/v1/teacher/lessons/{lessonId}/status?active=true\|false` | Activate / deactivate |

Video management is deliberately **not** exposed: the teacher payload's video
fields are ignored and the stored `video_url` / `duration` are carried over
untouched on every update.

## 4. Hierarchy

`Course → Modules → Lessons → Videos` is preserved. A lesson is always created
through its `moduleId`, so it can only ever land in an existing module of an
assigned course, e.g.

```
Java Full Stack
  Module 1 — Java Fundamentals   → Introduction, Variables, Data Types
  Module 2 — OOP                 → Classes, Objects, Inheritance
```

## 5. Security — backend enforced

- Teacher identity comes only from the session-resolved `teacherId` request
  attribute; no endpoint accepts a teacher id from the client.
- Every `moduleId` / `lessonId` from the browser is untrusted: the row is loaded
  and the full chain walked — lesson → module → course → `course.teacherId` —
  before any read or write. Mismatch → `403`, missing row → `404`.
- Therefore blocked: editing another teacher's lesson, manipulated lesson ids,
  creating a lesson inside an unassigned course's module, and moving/toggling a
  lesson of another course.
- Unauthenticated calls are rejected with `401` by `TeacherAuthInterceptor`.

## 6. Verification — `mvn test`: **35 tests, 0 failures**

`TeacherLessonManagementTest` proves:

1. Add lesson (×3) — sequential display orders, correct `moduleId`, active by default.
2. Edit lesson title.
3. Add description, then edit the description.
4. Change display order — "Variables" moved above "Introduction" and the new
   order is what the content tree returns.
5. Deactivate then activate — description and other fields survive the toggle.
6. Lessons belong to the correct module (asserted via the content tree).
7. Teacher A gets `403` on create/edit/move/status against Teacher B's
   module/lesson; Teacher B's row is verified unchanged afterwards; anonymous
   calls get `401`.

Pre-existing suites — including `StudentLearningSecurityTest`,
`AdminManagementSecurityTest`, `TeacherCourseSecurityTest`,
`PaymentToEmailFlowTest` — all still pass, so Student Portal and Main Admin
behaviour is unchanged.

## 7. How to run

```bash
cd backend
mvn spring-boot:run        # http://localhost:8080  (Swagger: /swagger-ui.html)
```

Then open the site root (any static server, or open the HTML files directly):

```
teacher-login.html  →  teacher-admin/index.html  →  Manage Content
```

Default teacher account is seeded on first start (`VITCteacher`, see
`backend/README.md`).
