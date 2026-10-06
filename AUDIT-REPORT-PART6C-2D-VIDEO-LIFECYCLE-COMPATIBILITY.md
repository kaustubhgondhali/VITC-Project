# AUDIT REPORT — PART 6C-2D/8: VIDEO LIFECYCLE COMPATIBILITY

## 0. Environment disclosure (read this first)

Same sandbox constraints as prior parts: no internet, no `mvn`/cached dependency jars, no
MySQL, no browser. `mvn compile` / `mvn test` **could not be run here**. What follows is a
full static trace of the Replace/Remove/External lifecycle against the Student Portal's
read and streaming paths, plus a manual brace-balance check of every `.java` file in the
backend. Run the real build yourself with the commands in §5.

## 1. Scope

This part does not redesign Teacher Admin or Student Portal. It verifies that the Replace
(PART 6A) and Remove (PART 6B) video flows already implemented stay correct against the
Student Portal's secure playback path (PART 6C-2A/2B/2C), and adds the one class of test
that did not exist yet: an end-to-end test that drives both the Teacher write path and the
Student read/stream path together, in the same test, across a full lifecycle.

## 2. What was already correct (verified, unchanged)

Traced through `TeacherCourseContentServiceImpl`, `FileStorageServiceImpl`,
`StudentLearningServiceImpl`, and `StudentVideoStreamServiceImpl`:

- **Replace never deletes before the new upload succeeds.** `setLessonVideo()` uploads the
  new file and saves the new `videoUrl` on the lesson row first; the old file is deleted
  only after both of those succeed. A validation failure (bad extension/MIME/size) throws
  before the lesson is touched at all, so `oldVideoUrl` and the physical old file are
  untouched.
- **Remove clears `videoUrl` before deleting the file**, and only ever deletes a file that
  was actually a VITC-managed `uploads/videos/...` path — every other field on the lesson
  (title, description, duration, displayOrder, active) is passed back unchanged, and no
  Lesson/Module/Course/Enrollment/StudentLessonProgress row is ever touched by either flow.
- **External URLs are never touched by disk logic.** `deleteVideoIfManaged()` is a no-op for
  anything that doesn't start with the managed `/uploads/videos/` prefix; `playableUrl()` in
  the Student Portal returns an external URL exactly as stored instead of minting a stream
  token for it; the stream endpoint itself 404s cleanly if ever pointed at one.
- **The stream endpoint re-resolves the lesson's live `videoUrl` on every request** rather
  than caching a file path in the token. The token only carries
  `studentId`/`lessonId`/expiry + signature — which file it points at is decided fresh, from
  the database, each time. This is the detail that makes lifecycle compatibility hold: a
  token minted before a teacher replaces or removes a video is not pinned to the old file.
- **Direct filesystem access to `uploads/videos/**` is blocked unconditionally**
  (`VideoDirectAccessInterceptor`, 403 always), so an old video URL a student may have seen
  in a network tab was never independently reachable in the first place, replace/remove or
  not.

None of this required a code change — it was already built correctly across PART 6A, 6B,
and 6C-2A. This part's job was to prove it, specifically across the two-sided lifecycle,
which no single existing test file did in one place.

## 3. What was added

**`backend/src/test/java/com/vitc/VideoLifecycleCompatibilityTest.java`** (new file, six
tests) — the only file changed for this part. It seeds one teacher, one course, one module,
and one enrolled student, then drives the Teacher Admin multipart video endpoints and the
Student Portal lesson/stream endpoints together against the same lesson:

| Test | Covers checklist items |
|---|---|
| `replaceVideoLifecycle_oldGoneNewPlays_evenForAPreIssuedToken` | 1, 2, 3, 4 — old video plays, replace succeeds, new video plays, old file is gone from disk, **and** a token issued before the replace serves the new bytes instead of erroring or serving stale content |
| `failedReplacement_leavesTheCurrentlyPlayingVideoUntouched` | 5 — a rejected replacement (bad file type) leaves `videoUrl`, the on-disk file, and the student's already-issued token all working exactly as before |
| `removeVideoLifecycle_becomesInaccessible_lessonAndEnrollmentSurvive` | 6, 7, 8 — remove clears the url and deletes the file; a token issued *before* the removal gets 404 on redemption; the lesson/module/course/enrollment survive with fields intact |
| `externalVideoUrl_survivesTheLifecycleUnchanged` | 3, 9 — an external `https://example.com/video.mp4` round-trips through the real Teacher edit endpoint unchanged, is returned unchanged to the Student Portal, 404s (not 500, not a filesystem read) on the stream endpoint, survives a remove with no filesystem call, and survives being replaced by a real local upload |
| `authorizedStudentStreams_unauthorizedStudentStaysBlocked` | 10, 11 — the enrolled student streams the current video; a second student with no enrollment is blocked both at the lesson-lookup endpoint and, separately, at the stream endpoint even with a well-formed token minted for their own account |
| (byte-marker technique used throughout) | every upload in this file embeds a distinct marker byte, so "which file did the endpoint actually serve" is asserted by exact body bytes, not just by URL string — catching the class of bug where the correct URL is returned but a stale file handle is served |

No production file was changed. Every behaviour this test proves was already implemented;
this closes the gap in *test coverage that exercises Teacher-write and Student-read
together*, which the existing `TeacherVideoManagementTest.java` (Teacher side only) and
`StudentVideoAccessSecurityTest.java` (Student side only) did not do.

## 4. Full checklist trace against §6 of the part spec

1. Existing local video plays — `replaceVideoLifecycle_...`, first assertion block.
2. Replace local video — same test, upload of `new.mp4`.
3. New video plays — same test, token-after-replace assertion; also re-proven for the
   external→local case in `externalVideoUrl_survivesTheLifecycleUnchanged`.
4. Old video is no longer served — same test: file existence check on disk, plus the
   raw old URL still returning 403 via the direct-access interceptor.
5. Failed replacement preserves old video — `failedReplacement_...`, full test.
6. Remove video — `removeVideoLifecycle_...`, DELETE call + DB assertion.
7. Removed video cannot be accessed — same test: lesson lookup shows no `videoUrl`/
   `videoType: NONE`, and the pre-removal token 404s.
8. Lesson remains — same test: lesson title, module, course, and enrollment status all
   asserted present/unchanged afterward.
9. External video still works — `externalVideoUrl_survivesTheLifecycleUnchanged`, full
   test.
10. Authorized student can access the current video —
    `authorizedStudentStreams_unauthorizedStudentStaysBlocked`, first half.
11. Unauthorized student remains blocked — same test, second half (lesson lookup 403 +
    stream endpoint 403 even with that student's own valid token).

## 5. Static checks performed on the full backend (361 `.java` files, main + test)

| Check | Result |
|---|---|
| Brace balance per file | 361/361 balanced |
| Every method/field referenced in the new test cross-checked against its real declaration (`EnrollmentRepository.findFirstByUserIdAndCourseId`, `User.studentLoginId`, `AdminLessonRequest` record fields, `VideoAccessTokenService.issue(Long,Long)`, `StudentLessonDetailResponse` `videoUrl`/`videoType` JSON keys) | all present, all match |
| New test file's HTTP surface reused exactly as the existing Teacher/Student test files (`X-Teacher-Username`/`X-Teacher-Token`, `X-Student-Id`/`X-Student-Token`, multipart `PUT .../video`, `DELETE .../video`, `GET /api/v1/student/lessons/{id}`, `GET /api/v1/student/lessons/{id}/video?token=`) | consistent, no new endpoints introduced |
| No production (`main`) source file touched | confirmed — only the new test file was added |

## 6. Run it yourself

```bash
cd VITC-Website/backend
mvn clean compile
mvn test -Dtest=VideoLifecycleCompatibilityTest
mvn test -Dtest=TeacherVideoManagementTest,StudentVideoAccessSecurityTest,VideoLifecycleCompatibilityTest
mvn spring-boot:run
# then, as a Teacher: upload a video, replace it, confirm the old file under
# uploads/videos/ is gone; remove it and confirm the lesson still opens with no video.
# As a Student: confirm the current video streams, seeks, and that an unenrolled
# account cannot open the lesson at all.
```

As with the previous parts, this hasn't been compiled or run in this sandbox — treat this
as a careful source review plus a new, ready-to-run test file, not a substitute for
actually running the commands above.
