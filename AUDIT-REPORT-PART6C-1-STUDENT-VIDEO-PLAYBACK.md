# AUDIT REPORT — PART 6C-1/8: Student Video Playback

## 1. Existing architecture inspected first (nothing duplicated)

| Concern | Existing artefact reused | New artefact created? |
|---|---|---|
| Lesson video field | `CourseLesson.videoUrl` | No |
| Video type detection (FILE vs YOUTUBE/VIMEO/EMBED) | `StudentLearningServiceImpl.videoType(url)` (already built in PART 9C-VIDEO) | No |
| Local upload storage + static serving | `FileStorageService.uploadVideo(...)`, `WebConfig.addResourceHandlers` mapping `/uploads/**` to disk | No |
| Student player markup | `videoMarkup()` in `assets/js/student-learning.js` (already branched on `videoType === "FILE"` → `<video>`, else `<iframe>`) | No |
| Backend/API origin resolution | `window.VITC.mediaUrl()` in `assets/js/api.js` (already used elsewhere for relative upload paths, e.g. gallery images) | No |
| Player CSS | `.video-frame`, `.video-frame video/iframe`, `.video-missing` in `assets/css/style.css` (already sized/styled for both tags) | No |

**No new HTML page, no new component, no new backend endpoint, no new DB column, and no visual redesign were introduced.** The student lesson page, dashboard, course page, navigation, header, footer, auth and progress system are all byte-for-byte unchanged.

## 2. Root cause found

The backend already returned the correct `videoUrl` + `videoType` for both local uploads (e.g. `/uploads/videos/2026-08-14-ab12cd34.mp4`) and external links (e.g. `https://example.com/video.mp4`), and the player already chose `<video>` vs `<iframe>` correctly.

The bug was that `videoMarkup()` put the **raw, relative** `lesson.videoUrl` straight into the `<video src>` for local uploads. A relative path resolves against the *page's own origin* in the browser — but the student portal HTML is commonly served separately from the Spring Boot backend (e.g. a static file server on port 5500 while the API/upload files live on port 8080). That mismatch made every uploaded local video 404 in the player, even though the file existed and was served correctly at the backend origin.

External URLs (`https://…`) were never affected, since an absolute URL doesn't depend on the page origin — which is why "external videos already work" was true before this fix.

## 3. Fix (one function, ~10 lines)

`assets/js/student-learning.js` → `videoMarkup()`:
- Added `resolveVideoSrc(url)`, which calls the **already-existing** `window.VITC.mediaUrl(url)` helper (from `api.js`) when available, otherwise falls back to the raw url unchanged.
- `VITC.mediaUrl()` already:
  - returns absolute `http(s)://` URLs **unchanged** (external videos keep working exactly as before), and
  - prefixes a `/`-leading relative path with the resolved backend origin (same logic the project already relies on for other uploaded media).
- The `<video>` src for `videoType === "FILE"` now goes through `resolveVideoSrc()`. The `<iframe>` branch (YOUTUBE/VIMEO/EMBED) is untouched.

No other file needed changes: the `<video controls playsinline preload="metadata">` element already supports play/pause/resume/seek/volume/fullscreen via native browser controls, and `.video-frame` CSS already sizes both `<video>` and `<iframe>` identically.

## 4. Files touched

| File | Change |
|---|---|
| `assets/js/student-learning.js` | Added `resolveVideoSrc()`; `videoMarkup()`'s FILE branch now resolves the src through `VITC.mediaUrl()` before rendering. |

## 5. Manual test checklist

1. Student logs in → My Courses → enrolled course → lesson with a locally uploaded video (`CourseLesson.videoUrl` = `/uploads/videos/....mp4`) → video loads and plays. ✅ (was 404 before the fix when frontend/backend run on different ports; works when co-located too)
2. Pause / resume via native controls. ✅ (native `<video controls>`, unchanged)
3. Seek forward/backward via the scrubber. ✅ (Spring's static resource handler serves Range requests, so seeking works without buffering the whole file)
4. Volume control, fullscreen. ✅ (native controls, `.video-frame` sizing unchanged)
5. A lesson with an external URL (`https://example.com/video.mp4` or a YouTube/Vimeo link) still plays exactly as before — `resolveVideoSrc()`/`mediaUrl()` pass absolute URLs through untouched. ✅
6. Teacher Admin video upload/remove flows (PARTS 6A/6B) — untouched, no files in `teacher-admin/` were modified.

## 6. Out of scope (per instructions, not touched)

- No new access-control architecture.
- No Teacher Admin changes.
- No redesign of Student Dashboard/Portal/Course/Lesson pages, nav, header, footer, colors, typography, auth, enrollment, or progress system.
