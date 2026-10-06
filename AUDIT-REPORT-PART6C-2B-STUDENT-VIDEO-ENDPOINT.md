# AUDIT REPORT — PART 6C-2B/8: PROTECTED STUDENT VIDEO ENDPOINT

## 0. Environment disclosure (read this first)

Same sandbox constraints as prior parts: no internet access, no `mvn` binary or cached
Maven/Gradle dependency jars, no MySQL server, no browser. `javac`/`mvn` compilation and
`mvn test` **could not be executed here**. Everything below is a static, file-by-file
verification of the source — not a live build or test run. Section 4 gives you the exact
commands to run the real build/test on your machine.

## 1. Finding: the endpoint already exists

`GET /api/v1/student/lessons/{lessonId}/video` — the exact route this part asked for — was
already fully built in **PART 6C-2A**, not stubbed:

| Piece | File |
|---|---|
| Controller | `controller/StudentVideoStreamController.java` |
| Service interface | `service/StudentVideoStreamService.java` |
| Service impl (the actual auth chain) | `service/impl/StudentVideoStreamServiceImpl.java` |
| Signed capability token | `security/VideoAccessTokenService.java` |
| Blocks direct `/uploads/videos/**` access | `security/VideoDirectAccessInterceptor.java` |
| Wiring (interceptor registration, static resource handler) | `config/WebConfig.java` |
| Token minted at lesson-lookup time | `service/impl/StudentLearningServiceImpl.java` (`playableUrl`) |
| Frontend consumption (`<video src>`) | `assets/js/student-learning.js` |
| Existing security tests | `src/test/java/com/vitc/StudentVideoAccessSecurityTest.java` |

This part's actual work was verifying that chain end-to-end against every case this part's
spec calls out, and closing test-coverage gaps — not redesigning it, per the "do not
redesign" instruction. No production code changed.

## 2. Authorization flow — verified against source, case by case

| Case | Requirement | Verified in |
|---|---|---|
| 1. Not authenticated | No usable session → rejected | `StudentVideoStreamServiceImpl.resolve()` step 1: an absent/garbage `token` fails `tokenService.parse()` → `ForbiddenException` (403). A structurally missing `token` param never reaches the method — `@RequestParam String token` → Spring's `MissingServletRequestParameterException` → `GlobalExceptionHandler` → 400. (This endpoint is deliberately excluded from `StudentAuthInterceptor`'s header-based session check — see the controller javadoc — because a native `<video>` tag can't attach `X-Student-Id`/`X-Student-Token` headers. The signed token *is* this endpoint's authentication.) |
| 2. Not enrolled | 403 | step 4: `enrollmentRepository.findFirstByUserIdAndCourseId(...)` filtered to `ACTIVE`/`COMPLETED` → `ForbiddenException` if absent or any other status |
| 3. Cross-course | 403 | token is bound to a `studentId`; step 4 re-checks *that* student's enrollment against *this* lesson's actual course — a token for a student in Course A resolves Course B's lesson and fails the Course-A-student-in-Course-B enrollment check |
| 4. Invalid lesson | 404 | step 3: `lessonRepository.findById(lessonId).filter(active)` → `ResourceNotFoundException` |
| 5. No video | 404 | `videoUrl == null/blank` (or not under the managed `/uploads/videos/` prefix) → `ResourceNotFoundException` |
| 6. Authorized | 200/206 stream | all five checks pass → file resolved, streamed as a `ResourceRegion` |

Flow order in code matches the spec exactly: **token/auth → role (active STUDENT lookup) →
lesson exists/active → enrollment (course derived from the lesson, not client input) →
video presence → path resolution → stream**.

## 3. Video path security — verified

- The endpoint's only inputs are the `lessonId` path variable and the `token` query param.
  There is no `path=`/`file=` parameter anywhere in the controller signature — a client
  cannot submit a filesystem path at all, so `?path=C:...`, `?file=../../...`,
  `?file=/etc/...` are simply unused query params, not routed to any file-reading code.
- The real path always comes from `CourseLesson.videoUrl`, which itself is only ever
  written by `FileStorageServiceImpl` with a generated filename — never client input.
- `StudentVideoStreamServiceImpl.resolve()` strips the known `/uploads/videos/` prefix,
  resolves it under `Paths.get(uploadDir)`, and checks `resolved.startsWith(root)` +
  `Files.isRegularFile(resolved)` before ever touching the file — defence in depth even
  though the input can't actually contain `..` segments from a client.
- `/uploads/videos/**` itself is no longer publicly servable at all —
  `VideoDirectAccessInterceptor` answers 403 to every request under that prefix,
  authenticated or not (`WebConfig` registers it ahead of the static resource handler).
  Every other `/uploads/**` path (gallery images, docs, avatars) is untouched.
- External URLs (`https://...`) are detected by prefix check and returned as-is from
  `StudentLearningServiceImpl.playableUrl()` / rejected with 404 by the stream service if
  someone tries to hit the stream endpoint for a lesson whose video isn't a local file —
  they're never opened as a local path, never downloaded, never proxied.

## 4. What I changed this part

Only the test file — added explicit coverage for cases the existing test didn't exercise
(it already covered direct-URL blocking, happy path streaming, garbage token, cross-lesson
token replay, and revoked-enrollment token cutoff):

- `missingTokenIsRejected` — no `token` param at all (400, per §2 above)
- `crossCourseStudentCannotStream` — a *second*, genuinely different enrolled student/course
  pair, not just a revoked enrollment (Case 3 as literally described: enrolled in Course A,
  requesting Course B's video)
- `invalidLessonIdReturnsNotFound` — nonexistent `lessonId` (Case 4)
- `lessonWithoutVideoReturnsNotFound` — real lesson, real enrollment, `videoUrl = null`
  (Case 5)
- `pathTraversalAttemptsAreRejected` — confirms bogus `path=`/`file=` query params are inert

No controller, service, security, or config source file needed a change — the chain already
satisfied every case in the spec.

## 5. Static checks performed on the full backend (335 `.java` files under `src/main`)

| Check | Result |
|---|---|
| Brace balance (per-file `{`/`}` count) | 335/335 balanced |
| Route mapping collision check for `.../lessons/{lessonId}/video` | 2 matches: `StudentVideoStreamController` (`/api/v1/student/...`) and `TeacherCourseContentController` (`/api/v1/teacher/...`, pre-existing) — different base paths, no collision |
| Every symbol `StudentVideoStreamServiceImpl` depends on actually exists | `EnrollmentRepository.findFirstByUserIdAndCourseId`, `CourseLessonRepository` (JpaRepository), `CourseLesson.getModule()`/`CourseModule.getCourse()`, `ForbiddenException`/`ResourceNotFoundException` constructors — all present and match usage |
| Exception → HTTP status mapping | `ResourceNotFoundException` → 404, `ForbiddenException` → 403, missing/malformed param → 400, all via `GlobalExceptionHandler` |
| `application.properties`/`-test.properties` keys referenced by `@Value` in the new-in-6C-2A classes (`app.upload.dir`, `app.upload.public-path`, `app.security.video-token-key`, `app.video.stream-token-ttl-minutes`) | all present |
| Frontend (`student-learning.js`) actually renders `lesson.videoUrl` from the API response into `<video src>` for `videoType === "FILE"` | confirmed, unchanged |

## 6. Run it yourself

```bash
cd VITC-Website/backend
mvn clean compile          # compiles the whole backend
mvn test -Dtest=StudentVideoAccessSecurityTest   # runs just this part's test class
mvn spring-boot:run        # then exercise the endpoint manually / via the student portal
```

If `mvn compile` or the test class surfaces anything, it'll be the first real compiler/test
signal on this endpoint since it can't be produced in this sandbox — worth doing before
relying on this in production.
