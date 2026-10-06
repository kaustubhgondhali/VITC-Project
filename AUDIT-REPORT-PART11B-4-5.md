# AUDIT REPORT — PART 11B-4/5: STUDENT ENROLLMENT & COURSE AUTHORIZATION

Scope: verify (and where needed, harden) strict backend course-level authorization for
**Students**, based on actual purchase/enrollment records, continuing from PART 11B-3
(Teacher Course Authorization).

## 1. Finding

The uploaded project (`part11b-3-teacher-course-authorization.zip`) already contains a complete,
previously-built implementation of student enrollment authorization — done as part of the
earlier **PART 11B-1 / PART 11B-2** work (see inline comments in
`StudentAuthInterceptor.java` and `StudentLearningServiceImpl.java`). No gaps were found against
the PART 11B-4/5 requirements, so **no source changes were required**. This report documents the
verification and repackages the project unchanged.

## 2. How each requirement is enforced

### 2.1 Identity comes from the server, never the client
`security/StudentAuthInterceptor.java` resolves the caller from the `X-Student-Id` +
`X-Student-Token` session headers against `UserRepository`, checks the session hasn't expired,
and checks the account is `ACTIVE` + role `STUDENT`. The resolved numeric `studentId` is placed
on the request as `STUDENT_ID_ATTRIBUTE` and mirrored into `CurrentUserContext` for the
role-check interceptor. Controllers only ever read this attribute — the request body, path
variables, and query string are never trusted for identity.

### 2.2 Every protected read/write re-verifies enrollment
`service/impl/StudentLearningServiceImpl.java#requireEnrollment(student, courseId)` looks up
`Enrollment` by **server-known `studentId` + requested `courseId`**, and requires the status be
`ACTIVE` or `COMPLETED` (`ACCESS_STATES`). It is called on the enrollment boundary for:
- `course(studentId, courseId)` — course detail + modules/lessons
- `modules(studentId, courseId)` — module/lesson listing
- `lesson(studentId, lessonId)` — resolves the lesson's parent course first, then checks
  enrollment for *that* course before returning the video URL
- `saveProgress(studentId, lessonId, ...)` — same course resolution + enrollment check before
  any progress row is read or written

Any course id that isn't backed by an active enrollment for that student — typed manually,
swapped from another student's course, or guessed — throws `ForbiddenException`, mapped to
`403 Forbidden`, before any course/module/lesson/video data is built or returned.

### 2.3 Progress is isolated per student
`StudentLessonProgressRepository.findByStudentAndCourse(studentId, courseId)` filters at the
query level by the authenticated student's id; there is no endpoint that accepts a student/user
id from the browser to select whose progress to read or write. `saveProgress` resolves the
progress row via `findByStudentIdAndLessonId(student.getId(), lessonId)` — `student` is always
the session-resolved account, so Student A can never read or mutate Student B's progress by
editing the URL, body, or query string.

### 2.4 Course-catalog vs. protected content
`controller/CourseController.java` (`/api/v1/courses/**`) is the public storefront catalog used
for browsing/purchase pages (`courses.html`, `course-java.html`, buy flow) and its
`CourseResponse` DTO carries no lesson/video/module content — so no protected data is exposed
pre-authorization. Actual lesson content and video URLs are only ever served through
`/api/v1/student/**`, which sits behind `StudentAuthInterceptor` + the per-course
`requireEnrollment` check above.

### 2.5 Existing automated coverage
`backend/src/test/java/com/vitc/StudentLearningSecurityTest.java` already exercises this exact
matrix end-to-end via `MockMvc` against a real Spring context:
- Enrolled student → Java course/lessons → `200 OK`
- Same student → Python course id (not enrolled) → `403 Forbidden`, no course data
- Same student → Python lesson id (not enrolled) → `403 Forbidden`
- Cross-student progress access is exercised alongside the course checks

## 3. Verification matrix (requirement → mechanism → result)

| Requirement | Mechanism | Result |
|---|---|---|
| Student + purchased course → allowed | `requireEnrollment` finds `ACTIVE`/`COMPLETED` enrollment | ✅ |
| Student + unpurchased course → 403 | `requireEnrollment` throws `ForbiddenException` | ✅ |
| Course id swapped in URL/query/body → 403 | Enrollment re-checked server-side every call, never trusts the id's origin | ✅ |
| Lesson/video access without enrollment → 403 | `lesson()` resolves parent course, then `requireEnrollment` | ✅ |
| Student id swapped to view another student's progress → 403 | Identity is the session-resolved `studentId`; no endpoint accepts a foreign id | ✅ |
| No protected content leaks before authorization | 403 is thrown before any DTO is constructed | ✅ |
| Existing Student Portal UI/nav/features unchanged | No frontend or route changes made | ✅ |

## 4. Files touched in this pass

None. This pass was verification-only; the project is repackaged as received.

## 5. How to verify yourself

```
cd backend
mvn test -Dtest=StudentLearningSecurityTest
```

Or manually with a real student session token (`X-Student-Id` / `X-Student-Token` headers):
- `GET /api/v1/student/courses/{ownCourseId}` → 200
- `GET /api/v1/student/courses/{otherCourseId}` → 403
- `GET /api/v1/student/lessons/{lessonFromOtherCourse}` → 403
- `POST /api/v1/student/lessons/{lessonFromOtherCourse}/progress` → 403

## 6. Note on this environment

This sandbox has no network access to Maven Central, so `mvn test` could not be executed here to
produce a live run of the suite above — the confirmation in this report is a full static/code
audit of the interceptor, service, repository, and existing test file. Please run the command in
§5 locally (or in CI) as the final gate before deploying.
