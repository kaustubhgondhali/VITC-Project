# VITC Website — PART 7A/8: Code-Level Audit (Static Inspection)

**Label matters here: everything below is code inspection, not test
execution.** Per the brief's own rule ("do not claim a test passed based
only on code inspection"), nothing in this document should be read as a
PASS/FAIL test result. For actual PASS/FAIL results, use
`TEST-SCRIPT-PART-7A-8-STUDENT-TEACHER-SECURITY.md` against a real running
instance. What follows is: does the code, as written, implement the
authorization and video-security guarantees the brief asks about — traced
through the actual call chain, not assumed.

Why execution wasn't possible in this environment: this sandbox's network
is restricted to a fixed domain allow-list that doesn't include Maven
Central, so the Spring Boot backend (`backend/pom.xml`) cannot be built
here; there's also no database and no browser-automation tool available.

---

## Authorization Regression — traced through the actual code

### 1. Student cannot access Teacher functionality
`TeacherAuthInterceptor` guards `/api/v1/teacher/**` and requires
`X-Teacher-Username` / `X-Teacher-Token` headers resolving to a session
with `role == TEACHER` and `status == ACTIVE`. A valid **Student**
session presented against a Teacher endpoint is explicitly detected
(`hasValidNonTeacherSession`) and answered `403 Forbidden` rather than
silently succeeding or falling through. Code supports this correctly.

### 2. Student cannot access another student's private data
Every `StudentController` / `StudentLearningController` endpoint takes
`studentId` only via `@RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE)`
— there is no `@PathVariable` or `@RequestParam` for a student id anywhere
in either controller. The id is resolved server-side from the session
token and cannot be overridden by anything the browser sends. Code
supports this correctly, *assuming* every service method behind these
controllers actually uses the injected `studentId` for its DB query
(spot-checked `StudentLearningServiceImpl` — it does; a full line-by-line
audit of every service method was not performed given the scope of this
pass).

### 3. Teacher cannot modify another Teacher's course
`TeacherAuthorizationService.requireAssignedCourse(teacherId, courseId)`
is the single choke point: it loads the course fresh from the DB and
throws `ForbiddenException` (→ 403) unless `course.getTeacherId().equals(teacherId)`.
Traced this through `TeacherCourseContentServiceImpl` — all 12 public
methods (create/update/reorder/activate for modules and lessons, plus the
3 video-management methods) call `assertOwnsCourse` / `assertOwnsModule` /
`assertOwnsLesson` before doing anything else, and those three helpers
all resolve to `TeacherAuthorizationService`. No method bypasses it. Also
checked `TeacherCourseController` and `TeacherStudentProgressController` —
same pattern (`requireAssignedCourse`, `assignedCourses`). Code supports
this correctly.

### 4 & 5. Teacher can only access assigned courses / their own students
`TeacherStudentProgressServiceImpl.myStudents()` iterates only
`authorization.assignedCourses(teacherId)` — it does not query the full
student table. `courseStudents()` and `studentProgress()` both call
`requireAssignedCourse` before touching enrollment/progress data. Code
supports this correctly.

### 6. Unauthorized users cannot access protected APIs
Both `StudentAuthInterceptor` and `TeacherAuthInterceptor` deny (401) any
request missing the relevant header pair before touching the database.
The one deliberately-unauthenticated-looking endpoint,
`GET /api/v1/student/lessons/{lessonId}/video` (a native `<video>` tag
can't send custom headers), is documented as intentionally outside the
interceptor and instead requires a short-lived signed token — see below.
Code supports this correctly.

**Frontend-hiding vs. backend authorization**: the brief specifically
warned that hiding UI isn't security. Confirmed the authorization is
backend-enforced (interceptors + `TeacherAuthorizationService`,
independent of the frontend), not just conditional rendering in
`student.js` / `teacher.js`. This is the right architecture; it's not
something a static read can *prove* is unbypassable, though — that's
exactly what Section 3 of the test script is for.

---

## Video Regression — traced through the actual code

- **Upload / Replace / Remove**: `TeacherCourseContentServiceImpl.setLessonVideo()`
  uploads the new file *before* touching the DB row, only deletes the old
  file *after* the DB write succeeds, and only deletes it if it was a
  VITC-managed local file (never for an external URL). `clearLessonVideo()`
  follows the identical write-then-delete ordering. This is a genuinely
  safe order of operations for avoiding orphaned state on a failure
  midway through.
- **Protected Student playback**: `StudentVideoStreamController` is
  intentionally excluded from `StudentAuthInterceptor` (documented reason:
  a native `<video>` element can't send auth headers) and instead requires
  a signed `token` query param. `StudentVideoStreamServiceImpl.resolve()`
  re-verifies, **on every single request including every Range/seek
  request**: token signature, token expiry, token-to-lesson binding,
  student role/active status, lesson exists/active, and a fresh enrollment
  check (`ACTIVE`/`COMPLETED` only) — then resolves the file path and
  additionally re-checks it's inside the managed upload root, exists, is a
  regular file, and is readable, before ever opening a stream. This is a
  properly layered re-verification, not a check-once-then-trust pattern.
- **Seeking**: `StudentVideoStreamController.stream()` handles `Range`
  correctly (206 Partial Content, chunked at 2MB even if the browser asks
  for more, 416 for malformed/out-of-bounds ranges) and — per the point
  above — re-runs the full auth chain for every Range request too, so
  seeking can't be used to skip authorization.
- **External URL compatibility**: `resolve()` explicitly throws
  `ResourceNotFoundException` for any `videoUrl` that isn't under the
  managed `/uploads/videos/` prefix, with a comment that external URLs
  (YouTube/Vimeo) are expected to be embedded directly by the frontend
  rather than routed through this endpoint — consistent with how a
  student-facing player would actually use it.

None of this was changed. No video architecture was touched, per the
brief's explicit instruction.

---

## What this pass does and doesn't tell you

**Does tell you**: the authorization and video-security *design*, as
written in the code, is coherent and consistently applied everywhere it
was checked — no shortcut or bypass path was found in the controllers and
services examined.

**Doesn't tell you**: whether it behaves this way when actually run
(dependency wiring, a runtime `NullPointerException`, a misconfigured
`WebConfig` interceptor registration, a DB constraint that doesn't match
the entity, etc. would all be invisible to a static read). It also
doesn't cover the Student/Teacher UI flows (loading states, empty states,
notifications, modals, responsive layout) at all — those require an
actual browser and are entirely in `TEST-SCRIPT-PART-7A-8-STUDENT-TEACHER-SECURITY.md`.

## Recommendation

Run the test script above against a real local instance, focusing first
on Section 3 (Authorization) since that's the highest-stakes category —
then let me know any FAILs and I'll dig into the specific code path with
you.
