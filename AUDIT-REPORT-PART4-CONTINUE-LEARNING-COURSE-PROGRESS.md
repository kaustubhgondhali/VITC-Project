# AUDIT REPORT — PART 4/8: CONTINUE LEARNING & COURSE PROGRESS

## 0. Environment disclosure

This sandbox has no internet access and no `mvn` binary / cached Maven
dependencies (a request to `repo.maven.apache.org` is rejected by the network
proxy). `mvn clean install`, `mvn spring-boot:run` and `mvn test` cannot be
executed here. What **was** done instead, and is safe to trust:

- Read every file relevant to progress/lesson/continue-learning end to end
  (controller → service → repository → entity → DTO → frontend JS → HTML).
- A custom brace/paren balance checker was run across all 344 backend
  `.java` files (comment- and string-aware) — **0 unbalanced**.
- Searched the whole backend for `TODO`, `FIXME`, "not implemented",
  `UnsupportedOperationException` — **0 matches**.
- Checked `@RequestMapping` paths across student-facing controllers for
  collisions — **0 collisions**.
- Confirmed the `enrollments.status` column already supports `COMPLETED` in
  `database/vitc_db_fresh.sql` — no migration needed for this part.

You still need to run the real build (§3 below) before deploying.

## 1. What Part 4 asked for vs. what already existed

Parts 1–3C had already built almost the entire system this part describes.
Before writing anything, I read the existing implementation instead of
duplicating it, per the "reuse, don't duplicate" instruction:

| Requirement | Already implemented (file) | Status |
|---|---|---|
| Progress tracking (completed/total lessons, %, current/last lesson) | `StudentLessonProgress` entity, `StudentLearningServiceImpl` | ✅ reused, untouched |
| Lesson completion updates progress | `POST /api/v1/student/lessons/{id}/progress` → `saveProgress()` | ✅ reused, untouched |
| Progress reflects on Dashboard / My Courses / course page | `student.js` (`renderStats`, `renderContinue`, `courseCard`) reads live from `GET /courses`; `student-learning.js` reads live from `GET /courses/{id}` | ✅ reused, untouched — all three surfaces are driven by the same backend response, so there is nothing to keep in sync manually |
| Continue Learning → course → module → lesson → open it | `resumeLessonId` computed server-side (`resumeLesson()`), `continueHref()` in `student.js`, boot logic in `student-learning.js` opens it automatically, including "open the first available lesson" when nothing was started | ✅ reused, untouched |
| Progress display (%, completed, total, remaining) | `courseCard()` renders "`pct`% Complete (`completed`/`total` Lessons)"; remaining is `total − completed`, computed inline where shown | ✅ reused, untouched |
| Security — student can only touch own progress | Session token → `studentId` from `StudentAuthInterceptor`; every query is scoped `findByStudentIdAndLessonId(studentId, …)` — a student can never address another student's row because the row is looked up by their own id, not a row id from the client | ✅ reused, untouched |
| **Course completion** — mark enrolment completed once all lessons are done | **Nothing did this before this part** | 🆕 added |

## 2. The one real gap: course completion

Every lesson could be marked complete, and course-level percentage reached
100%, but the `Enrollment.status` field never moved from `ACTIVE` to
`COMPLETED` — so nothing durable recorded "this student finished this
course" the way the spec requires ("mark the course as completed using the
existing enrollment/progress architecture").

### Fix — `StudentLearningServiceImpl.java`

Added one private method, called only when a lesson is saved as completed:

```java
private void maybeCompleteCourse(Long studentId, Long courseId) {
    List<CourseLesson> lessons = lessonRepository.findActiveByCourseId(courseId);
    if (lessons.isEmpty()) return;
    Map<Long, StudentLessonProgress> progress = progressMap(studentId, courseId);
    boolean allCompleted = lessons.stream().allMatch(l -> isCompleted(progress.get(l.getId())));
    if (!allCompleted) return;
    enrollmentRepository.findFirstByUserIdAndCourseId(studentId, courseId).ifPresent(enrollment -> {
        if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollmentRepository.save(enrollment);
        }
    });
}
```

Design notes:
- **No new table, no new service** — reuses `EnrollmentRepository` and the
  `EnrollmentStatus.COMPLETED` value that already existed but was unused.
  Satisfies "do NOT create duplicate progress tables/services."
- Only promotes `ACTIVE → COMPLETED`. An enrolment that is already
  `COMPLETED`, or in any other state, is left alone — idempotent, no
  double-writes on repeat calls.
- Runs inside the same `@Transactional` method as the progress save, so a
  student's lesson-complete and course-complete updates commit together.
- No certificate logic was added — explicitly out of scope for this part.
- Frontend needed **zero changes**: `student.js` already renders whatever
  `status` the API returns as the course pill, and already treats
  `progressPercentage >= 100` as "complete" for the Continue/Review button
  label and the My Courses filter tabs — so the enrolment now reaching
  `COMPLETED` status is picked up automatically on the next page load.

## 3. How to build and run it yourself

```bash
cd VITC-Website/backend
mvn clean install          # downloads deps + runs the test suite below
mvn spring-boot:run        # or: java -jar target/*.jar
```

Frontend is static HTML/CSS/JS — serve `VITC-Website/` with any static
server (or open the files directly) once the backend is running and
`assets/js/student.js`'s API base URL points at it.

## 4. Tests added

`backend/src/test/java/com/vitc/StudentLearningSecurityTest.java` gained two
tests (existing 8 tests in that file were left untouched):

1. **`completingAllLessonsMarksCourseCompleted`** — completes the seed
   course's only lesson, then asserts `GET /courses/{id}` now returns
   `enrollmentStatus: COMPLETED` and `progressPercentage: 100`.
2. **`progressIsIsolatedBetweenStudentsOnTheSameCourse`** — enrols a second
   student in the *same* course, has student A complete the lesson, and
   asserts: student B's own lesson view is still `completed: false`, student
   A's enrolment is `COMPLETED`, and student B's enrolment is still
   `ACTIVE`. This directly exercises "a student cannot modify another
   student's progress" for the case that matters most (same course, same
   lesson, two accounts) rather than only the already-covered "wrong course
   entirely" case.

### Final Testing checklist (from the part brief)

| Test | Covered by |
|---|---|
| Start a lesson | `openLesson()` boot flow in `student-learning.js` + `ownedCourseIsAccessible` test |
| Complete a lesson | `progressIsTracked` test (existing) |
| Progress updates | `progressIsTracked`, `completingAllLessonsMarksCourseCompleted` |
| Dashboard reflects progress | `renderStats`/`renderContinue` read the same `GET /courses` response used everywhere else — no separate cache to fall out of sync |
| My Courses reflects progress | same response, `courseCard()` |
| Continue Learning opens correct lesson | `resumeLessonId` server-computed + `continueHref()` + `student-learning.js` boot target resolution |
| Course completion | 🆕 `completingAllLessonsMarksCourseCompleted` |
| Unauthorized progress update | `foreignCourseAndLessonAreForbidden`, `progressIsTracked` (cross-course), 🆕 `progressIsIsolatedBetweenStudentsOnTheSameCourse` (same-course, same-lesson, two accounts) |

Run just this file once `mvn` is available:

```bash
mvn -Dtest=StudentLearningSecurityTest test
```

## 5. Files changed in this part

- `backend/src/main/java/com/vitc/service/impl/StudentLearningServiceImpl.java` — added `maybeCompleteCourse()`, called from `saveProgress()`
- `backend/src/test/java/com/vitc/StudentLearningSecurityTest.java` — added 2 tests
- This report

No other file was touched. The public website was not redesigned; no
unrelated system (payments, SMTP, admin, teacher portal, certificates) was
modified.
