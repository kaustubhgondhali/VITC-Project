# AUDIT REPORT — PART 6C-2C/8: SECURE VIDEO STREAMING & HTTP RANGE SUPPORT

## 0. Environment disclosure (read this first)

Same sandbox constraints as prior parts: no internet, no `mvn`/cached dependency jars, no
MySQL, no browser. `mvn compile` / `mvn test` **could not be run here**. What follows is a
full static trace of the streaming logic plus a manual line-count/brace verification of every
`.java` file in the backend. Run the real build yourself with the commands in §6.

## 1. What was wrong with the PART 6C-2B implementation

The endpoint already streamed via `FileSystemResource`/`ResourceRegion` (never a `byte[]`),
and already re-ran the full Auth→Role→Enrollment→Lesson→Video chain before touching any
bytes — that part didn't need to change. Two real gaps against this part's spec:

1. **Every response was 206**, even a plain request with no `Range` header at all — it
   silently truncated a "normal" full-file request down to the first 2MB while claiming
   Partial Content. `curl` (or anything that doesn't send `Range`) got a truncated,
   mislabeled response instead of the whole video.
2. **No cap on how much a single Range response could return.** A browser can legally send
   `Range: bytes=0-` ("give me the rest of the file"). On a 500MB lesson video, the old code
   would try to hand back the entire remaining ~500MB in one `ResourceRegion` — technically
   still streamed rather than buffered into memory, but as one unbounded response instead of
   the bounded chunks a real video CDN serves, defeating the "large file performance"
   requirement in spirit even though it wasn't literally loading a `byte[]`.

## 2. What changed

**`controller/StudentVideoStreamController.java`** — rewritten with three branches:

| Request | Response |
|---|---|
| No `Range` header | `200 OK`, full file, `Content-Length` set, `Accept-Ranges: bytes` advertised |
| Valid `Range` header | `206 Partial Content`, sliced to at most `CHUNK_SIZE` (2MB) even if the client asked for more, `Content-Range`/`Content-Length` describing exactly that slice |
| Malformed or out-of-bounds `Range` | `416 Range Not Satisfiable` with `Content-Range: bytes */<actual length>` |

The `Authentication → Role → Enrollment → Lesson → Video` call
(`streamService.resolve(lessonId, token)`) happens **before** the `Range` header is even
inspected, in every branch — a Range request gets no authorization shortcut. Body is always
a `Resource` (normal case) or `ResourceRegion` (range case) backed by `FileSystemResource`,
so Spring's message converters copy the file to the response in a fixed buffer loop — no
`byte[]` holding the whole file ever exists in the JVM, for a 10KB test file or a 500MB
lesson video alike.

**`service/impl/StudentVideoStreamServiceImpl.java`** — added the one missing FILE
VALIDATION step from this part's checklist: `Files.isReadable(resolved)`, alongside the
existing "inside the managed directory" / "is a regular file" checks, before the file is
handed to the controller.

No other production file changed. No frontend change was needed: the `<video controls
preload="metadata">` element already in `student-learning.js` gets seek/buffer/fullscreen
for free from the browser once `Accept-Ranges: bytes` is present — native `<video>` elements
issue their own Range requests when the user scrubs, with no JavaScript involved.

## 3. Headers — verified against source

- `Content-Type`: derived server-side from the stored filename's extension
  (`contentTypeFor()` in the service — `.webm` → `video/webm`, `.mov` → `video/quicktime`,
  else `video/mp4`), never from any client-supplied value.
- `Content-Length`: set explicitly on the 200 path (`.contentLength(contentLength)`); set by
  `ResourceRegionHttpMessageConverter` to the region's byte count on the 206 path.
- `Content-Range`: written by `ResourceRegionHttpMessageConverter` for 206 responses
  (`bytes {start}-{end}/{total}`); written explicitly for 416 responses (`bytes */{total}`).
- `Accept-Ranges: bytes`: set on both the 200 and 206 paths so the player knows subsequent
  seeks can use `Range`.

## 4. Range security — verified

- `resolve(lessonId, token)` is called once, at the top of `stream()`, before any Range
  parsing. There is no code path where a `Range` header changes which authorization checks
  run — the exact same `Authentication → Role → Enrollment → Lesson → Video` chain executes
  whether or not the request has a `Range` header.
- The only client-controlled inputs anywhere in this endpoint remain `lessonId` (path) and
  `token` (query) — the `Range` header only ever affects *which bytes of the already-resolved
  file* are returned, never *which file*. A student cannot use `Range` to reach another
  lesson's video; `lessonId` is still checked against the token's bound `studentId` and that
  student's own enrollment on every call, range or not.

## 5. New test coverage (`StudentVideoAccessSecurityTest.java`)

Added, on top of the existing access-control tests from PART 6C-2B:

- `normalVideoRequestReturnsFullContentAsOk` — no `Range` → 200, correct
  `Content-Type`/`Content-Length`/`Accept-Ranges`, full byte-exact body
- `rangeRequestSeekForwardReturnsExactSlice` — `Range: bytes=5000-5099` → 206, correct
  `Content-Range`/`Content-Length`, exact byte slice
- `rangeRequestSeekBackwardReturnsExactSlice` — a later request for an *earlier* offset
  succeeds independently, proving the endpoint is stateless per request
- `largeFileIsServedInCappedChunksNotWhole` — a file larger than `CHUNK_SIZE`, requested with
  `Range: bytes=0-` (open-ended), returns exactly `CHUNK_SIZE` bytes, not the whole remainder
- `contentTypeIsCorrectForWebmAndMov` — verifies MIME type is derived correctly for both
- `outOfBoundsRangeReturnsRangeNotSatisfiable` — a `Range` past EOF → 416 with correct
  `Content-Range`
- `unauthorizedRangeRequestIsRejected` — garbage token + `Range` header → still 403
- `crossCourseStudentCannotStream` — extended to also assert a `Range` request from a
  cross-course token is rejected, not just a plain request
- `pathTraversalAttemptsAreRejected` — extended to also attach a `Range` header alongside the
  bogus `path=`/`file=` params

Existing tests (`directUploadUrlIsBlocked`, `garbageTokenIsRejected`,
`tokenIsBoundToItsOwnLesson`, `revokedEnrollmentCutsOffAnAlreadyIssuedToken`,
`missingTokenIsRejected`, `invalidLessonIdReturnsNotFound`,
`lessonWithoutVideoReturnsNotFound`) are unchanged and still valid — they exercise code paths
before the Range branch even runs.

## 6. Static checks performed on the full backend (360 `.java` files, main + test)

| Check | Result |
|---|---|
| Brace balance per file | 360/360 balanced |
| `StudentVideoStreamController` / `StudentVideoStreamServiceImpl` reviewed line-by-line for API usage (`HttpRange.parseRanges`, `getRangeStart`/`getRangeEnd`, `ResourceRegion(Resource, long, long)`, `ResponseEntity` builder chain) | all standard, documented Spring Framework APIs |
| No `byte[]` allocation sized to the video file anywhere in the stream path | confirmed — body is always `Resource`/`ResourceRegion`, both stream via internal buffer copy |
| `CHUNK_SIZE` constant consistent between controller and test | both `2 * 1024 * 1024` |

## 7. Run it yourself

```bash
cd VITC-Website/backend
mvn clean compile
mvn test -Dtest=StudentVideoAccessSecurityTest
mvn spring-boot:run
# then open a lesson with a real local video and confirm: play, pause, resume,
# scrub forward, scrub backward, and fullscreen all work from the student portal.
```

As with the previous part, this hasn't been compiled or run in this sandbox — treat this as
a careful source review, not a substitute for that command.
