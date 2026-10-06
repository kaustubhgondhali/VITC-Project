# PART 1/10 — VITC Project Inspection & Teacher Admin Module Management

## 1. Inspection summary (no redesign, no duplication)
- Stack: static HTML front-ends (public site, `admin/`, `teacher-admin/`, student pages) + Spring Boot 3.3 backend (`backend/`), MySQL schema in `database/`.
- Auth: `TeacherAuthInterceptor` (X-Teacher-Username / X-Teacher-Token) for `/api/v1/teacher/**`, separate admin and student session auth. Untouched.
- Content model: `Course -> CourseModule -> CourseLesson` (`course_modules`, `course_lessons`), with `StudentLessonProgress`. Single source of truth; no duplicate tables created.
- Write path: `AdminCourseContentService` is the only place modules/lessons are written; `TeacherCourseContentService` is an ownership gate in front of it via `TeacherAuthorizationService`.
- Student reads: `StudentLearningServiceImpl` uses `findByCourseIdAndActiveTrueOrderByDisplayOrderAscIdAsc`, so inactive modules (and inactive lessons) are already invisible to students.
- Video: lesson row `video_url` + `duration`, streamed by `StudentVideoStreamController`. Not modified (media upload is out of scope for this part).

## 2. Gap found and closed
Teacher Admin already supported create / edit / reorder / activate / deactivate / list of modules.
Missing: **view a single module** and **delete a module** (delete existed only for Main Admin).

## 3. Files changed
- `backend/src/main/java/com/vitc/service/TeacherCourseContentService.java` — added `module(...)` and `deleteModule(...)`.
- `backend/src/main/java/com/vitc/service/impl/TeacherCourseContentServiceImpl.java` — implementations; ownership checked first, delete delegates to the existing admin delete path and then cleans up managed video files of the removed lessons.
- `backend/src/main/java/com/vitc/controller/TeacherCourseContentController.java` — `GET /api/v1/teacher/modules/{moduleId}`, `DELETE /api/v1/teacher/modules/{moduleId}`.
- `teacher-admin/course-content.html` — "Delete" action per module with an explicit confirmation that contrasts it with the reversible "Deactivate".
- `backend/src/test/java/com/vitc/TeacherModuleManagementTest.java` — new tests for view + delete (own module) and 403/401 for other teachers/unauthenticated.

## 4. New files
- This report only. No new entities, repositories, tables or panels.

## 5. Database changes
None. `course_modules` (id, course_id, title, description, display_order, active, created_at, updated_at) already covers every required field.

## 6. Module visibility behavior
- Active module → its active lessons are visible to enrolled students.
- Inactive module → the module and all of its lessons are hidden from students (repository-level filter, verified by `StudentLearningSecurityTest`).
- Deactivate is the default, reversible way to hide content; delete is permanent and removes lessons + their progress rows in one transaction.

## 7. Tests performed
`mvn test` (full suite, 176 tests): create, edit, reorder, activate, deactivate, view, delete, cross-teacher 403, unauthenticated 401, Course→Module→Lesson tree, student visibility.
`TeacherModuleManagementTest`: 4/4 passing.

## 8. Remaining issues (pre-existing, unchanged by this part)
The same 6 failures occur on the untouched uploaded build and on this build, so they are not regressions:
- `PublicContentAdminWriteAuthorizationTest.mainAdminStillReachesContentManagementApis`
- `PublicContentAdminWriteAuthorizationTest.publicSubmissionEndpointsStayOpen`
- `TeacherApiAuthorizationTest.assignedTeacherCanUseEveryTeacherApiOnOwnCourse`
- `TeacherVideoManagementTest.teacherCanUploadEditAndRemoveTheVideoOfOwnLesson`
- `TeacherVideoUploadSizeLimitTest.uploadAtOrUnderConfiguredLimitStillSucceeds`
- `TokenSessionSafetyTest.studentResetCodeCannotBeReusedAfterASuccessfulReset`
(all multipart-upload / rate-limit related, to be handled in the media-upload part)
