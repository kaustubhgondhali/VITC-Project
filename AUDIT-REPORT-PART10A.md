# AUDIT REPORT — PART 10A: Teacher Backend Authorization

## 1. What was implemented

A single, central Teacher authorization authority now guards **every** Teacher API.

| File | Change |
|---|---|
| `service/TeacherAuthorizationService.java` | **NEW** — the one contract for Teacher authorization: `requireTeacher`, `requireAssignedCourse`, `requireOwnedModule`, `requireOwnedLesson`, `assignedCourses`. |
| `service/impl/TeacherAuthorizationServiceImpl.java` | **NEW** — implements the full chain: authenticated → role `TEACHER` + status `ACTIVE` → course assigned (`Course.teacherId`) → module → lesson. Any failure throws `ForbiddenException` → **HTTP 403**. |
| `security/TeacherAuthInterceptor.java` | Split authentication from authorization. Invalid/expired/missing session → **401**. Valid session but role ≠ `TEACHER` or account not `ACTIVE` → **403 Forbidden**, request never continues. |
| `service/impl/TeacherCourseContentServiceImpl.java` | Local ownership logic removed; all checks delegate to `TeacherAuthorizationService`. |
| `service/impl/TeacherCourseServiceImpl.java` | Same — course list and single-course read go through the central authority. |
| `service/impl/TeacherDashboardServiceImpl.java` | Stats built only from `authorization.assignedCourses(teacherId)` (role re-checked). |
| `src/test/java/com/vitc/TeacherApiAuthorizationTest.java` | **NEW** — 7 tests covering every Teacher endpoint. |

No duplicate authentication or authorization system was created: the existing session model
(`TeacherAuthInterceptor` → `teacherId` request attribute), the existing `users` / `courses` /
`course_modules` / `course_lessons` tables and the existing `AdminCourseContentService` write path
are all reused.

## 2. Every Teacher API is covered

The teacher id is **always** the server-resolved session attribute — never a body/query value.
All path ids (courseId, moduleId, lessonId) are treated as untrusted and re-loaded + re-checked.

| API | Check |
|---|---|
| `GET /api/v1/teacher/courses` | assigned courses only |
| `GET /api/v1/teacher/courses/{id}` | `requireAssignedCourse` |
| `GET /api/v1/teacher/courses/{id}/content` | `requireAssignedCourse` |
| `POST /api/v1/teacher/courses/{id}/modules` | `requireAssignedCourse` |
| `PUT /api/v1/teacher/modules/{id}` | `requireOwnedModule` |
| `PATCH /api/v1/teacher/modules/{id}/move` (reorder) | `requireOwnedModule` |
| `PATCH /api/v1/teacher/modules/{id}/status` (activate/deactivate) | `requireOwnedModule` |
| `POST /api/v1/teacher/modules/{id}/lessons` | `requireOwnedModule` |
| `PUT /api/v1/teacher/lessons/{id}` | `requireOwnedLesson` |
| `PATCH /api/v1/teacher/lessons/{id}/move` (reorder) | `requireOwnedLesson` |
| `PATCH /api/v1/teacher/lessons/{id}/status` | `requireOwnedLesson` |
| `GET/PUT/DELETE /api/v1/teacher/lessons/{id}/video` | `requireOwnedLesson` |
| `GET /api/v1/teacher/dashboard/stats` | assigned courses only |
| `GET /api/v1/teacher/me`, `/auth/*` | session teacher only |

## 3. Course-level rule

Teacher A assigned **Java**, Teacher B assigned **Python**:

* Java (own) → **ALLOWED**
* Python (not assigned) → **403 Forbidden**, even when the id is hand-edited in the URL,
  passed as a parameter, or the request is sent directly with curl/Postman.
* Non-existent id → 404 (no assignment information leaked).
* Frontend button/route hiding is treated as cosmetic only — the backend is the enforcement point.

## 4. Test results (`mvn test`)

```
AdminManagementSecurityTest            6 tests  0 failures
PaymentSettingsIntegrationTest         5 tests  0 failures
PaymentToEmailFlowTest                 2 tests  0 failures
SmtpSettingsIntegrationTest            4 tests  0 failures
StudentLearningSecurityTest            6 tests  0 failures
TeacherApiAuthorizationTest (NEW)      7 tests  0 failures
TeacherCourseSecurityTest              4 tests  0 failures
TeacherDashboardStatsIntegrationTest   3 tests  0 failures
TeacherLessonManagementTest            2 tests  0 failures
TeacherModuleManagementTest            2 tests  0 failures
TeacherVideoManagementTest             4 tests  0 failures
VitcApplicationTests                   1 test   0 failures
------------------------------------------------------------
TOTAL                                 46 tests  0 failures  0 errors
```

New PART 10A tests:
1. assigned teacher can use every Teacher API on their own course
2. course APIs reject an unassigned course (403)
3. module APIs reject foreign modules (create / edit / reorder / status → 403)
4. lesson + video APIs reject foreign lessons (403 on all 8 calls)
5. valid non-teacher session (STAFF) → 403
6. inactive/suspended teacher → 403
7. no session at all → 401

## 5. Preserved functionality

Student, Main Admin, course, payment and authentication flows are untouched — their own
interceptors and services were not modified, and their test suites still pass.

## 6. How to run

```bash
cd backend
mvn spring-boot:run        # http://localhost:8080  (Swagger: /swagger-ui.html)
```
Then open the static site (`index.html`, `teacher-*.html`, `admin/`) with any static server.
