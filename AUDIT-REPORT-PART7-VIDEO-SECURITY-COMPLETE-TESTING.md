# AUDIT REPORT — PART 7/8: VIDEO UPLOAD SECURITY & COMPLETE TESTING

## 0. Environment disclosure (read this first)

Same sandbox constraints as every prior part: no internet access to Maven Central (confirmed
by a direct `curl` to `repo.maven.apache.org` / `repo1.maven.org`, both returning `403` from
the sandbox's egress proxy), no cached `~/.m2` repository, no MySQL. **`mvn compile` /
`mvn test` could not be executed here.** What follows is a complete static trace of every
item in PART 7's checklist against the real source, a brace-balance structural check of the
entire backend (362 files, including the one added in this part), and a symbol-by-symbol
cross-check of the one new test file against the actual entity/repository declarations it
uses. Run the real build with the commands in §5 before shipping — treat this report as
provisional until `mvn test` passes locally.

## 1. What PART 7 asked for vs. what already existed

Parts 1–6 (see `AUDIT-REPORT-PART6C-2A` through `-2E`) already implemented and fully tested
the teacher video upload/replace/remove flow and the student playback/streaming/security
flow. Tracing PART 7's 12 manual tests + automated-test checklist against the existing 27
test classes in `backend/src/test/java/com/vitc/`:

| # | Checklist item | Status before this part | Covering test(s) |
|---|---|---|---|
| 1 | Teacher Login → Dashboard | already covered | `AuthenticationCoreSystemTest`, `TeacherDashboardIntegrationFlowTest` |
| 2 | Course Content navigation | already covered | `TeacherCourseSecurityTest`, `TeacherModuleManagementTest` |
| 3 | Upload MP4 → stored → `CourseLesson.videoUrl` updated → visible in Teacher Admin | already covered | `TeacherVideoManagementTest.teacherCanUploadEditAndRemoveTheVideoOfOwnLesson` |
| 4 | Video playback | already covered | `StudentVideoAccessSecurityTest.normalVideoRequestReturnsFullContentAsOk` + range tests |
| 5 | Student playback (enrolled) | already covered | `StudentVideoAccessSecurityTest.enrolledStudentGetsPlayableToken`, `VideoLifecycleCompatibilityTest.authorizedStudentStreams_unauthorizedStudentStaysBlocked` |
| 6 | Replace video1→video2, old file cleaned up | already covered | `TeacherVideoManagementTest.replacingLocalVideoDeletesTheOldFileAfterSuccess`, `VideoLifecycleCompatibilityTest.replaceVideoLifecycle_oldGoneNewPlays_evenForAPreIssuedToken` |
| 7 | Remove video, lesson remains | already covered | `TeacherVideoManagementTest.removingLocalVideoClearsUrlDeletesFileAndPreservesLessonModuleAndCourse` |
| 8 | Invalid file (.pdf) rejected | already covered | `TeacherVideoManagementTest.rejectsUnsupportedFileTypes` |
| 9 | **Large file (above configured limit) rejected** | **missing — no test exercised `app.video.max-file-size`'s reject branch** | **added: `TeacherVideoUploadSizeLimitTest`** |
| 10 | Unauthorized teacher → 403, nothing stored | already covered | `TeacherVideoManagementTest.teacherCannotTouchVideoOfAnotherTeachersLesson` |
| 11 | External URL keeps working | already covered | `VideoLifecycleCompatibilityTest.externalVideoUrl_survivesTheLifecycleUnchanged` |
| 12 | Existing functionality (dashboard, courses, modules, lessons, reorder, activate/deactivate, profile, logout, student login/access/progress, admin, payments, orders, SMTP, public site) | already covered | `TeacherDashboardStatsIntegrationTest`, `TeacherLessonManagementTest`, `TeacherCourseInfoEditTest`, `TeacherStudentRosterTest`, `TeacherStudentProgressPageTest`, `StudentLearningSecurityTest`, `StudentProfileSecurityTest`, `AdminManagementSecurityTest`, `AdminTeacherManagementTest`, `RoleBasedAuthorizationTest`, `PaymentSettingsIntegrationTest`, `PaymentToEmailFlowTest`, `SmtpSettingsIntegrationTest`, `TeacherCrossRoleSecurityAuditTest`, `TeacherAdminSeparationTest` |

Named automated-test classes from PART 7's prompt (`TeacherVideoManagementTest`,
`TeacherLessonManagementTest`, `TeacherCourseSecurityTest`,
`TeacherCourseLevelAuthorizationTest`, `TeacherApiAuthorizationTest`,
`StudentTeacherApiProtectionTest`) were inspected in full — all already exercise multipart
upload, valid MP4, invalid file, unauthorized teacher, replace, remove, external URL, and
student access. **Nothing in them was deleted or altered.**

The one real gap: no test forced the `file.getSize() > videoMaxBytes` branch in
`FileStorageServiceImpl.uploadVideo()`. That branch existed and was already correct — it was
just never exercised by a test.

## 2. What changed in this part

**One file added, nothing else touched:**

- `backend/src/test/java/com/vitc/TeacherVideoUploadSizeLimitTest.java` (new)

This test uses `@TestPropertySource(properties = "app.video.max-file-size=1KB")` to shrink the
size ceiling for just this test class's Spring context (the real default, `500MB`, in
`application.properties` is completely untouched). It uploads a 4KB in-memory payload —
comfortably over the 1KB test ceiling but tiny enough to keep the test fast — and asserts:

1. `PUT /api/v1/teacher/lessons/{id}/video` with the oversized file returns `400 Bad Request`.
2. The lesson's `videoUrl` is never set (`GET` the same lesson's video afterward → no
   `videoUrl` in the response).
3. As a control, a 512-byte upload (under the same 1KB test ceiling) still succeeds with
   `200 OK` and a `/uploads/videos/` URL — proving the test isn't accidentally rejecting
   everything.

No existing test file, entity, controller, service, or DTO was modified. `diff -rq` of every
directory other than `backend/src/test/java/com/vitc/` against the input zip is empty.

## 3. Symbol-by-symbol cross-check of the new test

Every API surface the new test touches was verified against its real declaration rather than
assumed:

| Used in test | Verified against |
|---|---|
| `UserRepository.findByUsernameIgnoreCase(String)` | `UserRepository.java:41` |
| `User.builder()...`, `setSessionToken`, `setSessionExpiresAt` | `User.java` — `@Getter @Setter @Builder` (Lombok), fields `fullName`, `email`, `username`, `passwordHash`, `role`, `status` all present |
| `CourseRepository.findByCode(String)` | `CourseRepository.java:12` |
| `Course.builder()...` fields `code`, `title`, `price`, `active`, `teacherId` | `Course.java` |
| `CourseModule.builder()...` fields `course`, `title`, `displayOrder`, `active` | `CourseModule.java:36,40` |
| `POST /api/v1/teacher/modules/{moduleId}/lessons` | `TeacherCourseContentController.java:103` |
| `PUT /api/v1/teacher/lessons/{lessonId}/video` (multipart) | `TeacherCourseContentController.java:151-152` |
| `GET /api/v1/teacher/lessons/{lessonId}/video` | `TeacherCourseContentController.java:143` |
| `app.video.max-file-size` property name | `FileStorageServiceImpl.java` — `@Value("${app.video.max-file-size:500MB}")` |
| Course code `"SIZEA"` / username `"SIZETEACHERA"` don't collide with any other test class's fixtures | `grep -rn "SIZEA\|SIZETEACHERA"` across `src/test/java/com/vitc/` returns only this new file |

## 4. Structural verification

```
python3 brace-balance check across backend/src/**/*.java
total files: 362
mismatched:  0
```

362 = the 361 files present in the delivered zip + the one new test file. No file lost its
balance; nothing was truncated during editing.

## 5. Build & test — run these before deploying

```bash
cd VITC-Website/backend
mvn clean compile
mvn test
mvn test -Dtest=TeacherVideoUploadSizeLimitTest,TeacherVideoManagementTest,VideoLifecycleCompatibilityTest,StudentVideoAccessSecurityTest,TeacherLessonManagementTest,TeacherCourseSecurityTest,TeacherCourseLevelAuthorizationTest,TeacherApiAuthorizationTest,StudentTeacherApiProtectionTest
mvn spring-boot:run
```

Then manually walk PART 7's TEST 1–12 flow end to end (Teacher Login → Dashboard → My Courses
→ Course Content → Upload MP4 → Video Playback → Student Login → Student Playback → Replace →
Remove → Invalid file → Large file → Unauthorized teacher → External URL → full regression
pass on the areas in TEST 12). If `mvn test` reports any failure, treat this report as
provisional and fix that specific failure before considering PART 7 complete.

## 6. Final statement

Based on a complete static trace of PART 7's checklist against the real, already-implemented
video upload/playback/security code, plus one new test filling the single gap found (large
file rejection), plus a structural (brace-balance) and symbol-level check of everything
changed:

> **PART 7/8 checklist: VERIFIED against source, one missing test added, zero existing tests
> touched or removed.** This is a source-review verification, not a substitute for actually
> running `mvn test` — please do so before relying on it.
