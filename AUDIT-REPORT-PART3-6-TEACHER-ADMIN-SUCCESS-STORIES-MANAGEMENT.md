# AUDIT REPORT — PART 3/6: Teacher Admin Success Stories Management

Continues from PART 1/6 (public page + foundation) and PART 2/6 (database & backend).
This part adds the Teacher Admin UI + the small backend additions it needed. Nothing in
PARTS 1-2/6, or in the rest of Teacher Admin (Dashboard, My Courses, Content, Students,
Student Progress, Profile), was redesigned or removed.

## What was added

### Backend (additive only — no existing method signature changed, nothing removed)

- `FileStorageService` / `FileStorageServiceImpl`
  - `uploadSuccessStoryVideo(file, uploadedBy)` — new. Same MP4/WebM/MOV + size-limit
    validation as the existing lesson `uploadVideo`, but stores into a **new, separate**
    folder: `uploads/success-story-videos/`. This is deliberate: `uploads/videos/` (lesson
    videos) is intentionally blocked from direct access by `VideoDirectAccessInterceptor`
    (paid content, playable only through the enrolment-checked student stream endpoint).
    A Success Story video is the opposite — it's meant to be publicly playable on the
    public "Success Stories" page once published — so it needed its own, unblocked folder.
    `uploadVideo` (lessons) is untouched aside from being refactored to share the private
    `storeVideo(file, uploadedBy, folder)` helper — behaviourally identical to before.
  - `deleteFileIfManaged(url, folder)` — new, generalised version of the existing
    `deleteVideoIfManaged`. Deletes a physical file + its `MediaFile` row only when the URL
    points inside the given managed folder; anything else (external URL, wrong folder) is
    left untouched. `deleteVideoIfManaged` now just calls this with the lesson `videos`
    folder — same behaviour as before, zero change for existing callers.

- `TeacherSuccessStoryController`
  - `POST /api/v1/teacher/success-stories/video-upload` (multipart) — new. Uploads a
    story's video file and returns its URL, to be sent back as `videoUrl` on the
    create/update call. Guarded by the same `TeacherAuthInterceptor` +
    `@RequireRole(Role.TEACHER)` as every other endpoint in this controller — no new
    security wiring needed.
  - Every endpoint from PART 2/6 (`GET`, `GET /category/{cat}`, `GET /{id}`, `POST`,
    `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/publish`, `PATCH /{id}/unpublish`) is
    unchanged.

- `SuccessStoryServiceImpl.delete(id)`
  - Now also does best-effort cleanup of the story's uploaded video and thumbnail files
    (only if they live under the success-story-managed folders — an external/YouTube URL,
    or a thumbnail that was never actually uploaded through this feature, is left alone).
    The database delete itself is unchanged.

No SQL migration was needed for this part — `status`, `course_id`, `created_by`,
`created_by_role` already exist from PART 2/6's `success_stories_upgrade_part2.sql` /
`vitc_db_fresh.sql`. The two new upload folders (`success-story-videos/`,
`success-stories/`) are created automatically on first upload, the same way every other
`uploads/<folder>/` sub-directory in this project already is.

### Frontend

- `teacher-admin/assets/teacher.js` — added one entry to the shared `NAV` array:
  **Success Stories**, linking to `success-stories.html`. Every other nav item, and the
  sidebar/mobile-drawer/auth code around it, is unchanged.

- `teacher-admin/success-stories.html` — **new page**, built with the exact same shared
  primitives every other Teacher Admin page already uses (`Teacher.mountSidebar`,
  `Teacher.skeleton`, `Teacher.emptyState`, `Teacher.bindModal`, `Teacher.fieldError`,
  `Teacher.withButtonLoading`, `Teacher.api`, `admin.css` + `teacher.css` classes). No new
  design system, no changes to `admin.css` or `teacher.css`.
  - **List/table**: Thumbnail, Name (+ designation), Category, Course, Video (preview
    button), Status (badge), Created date, Actions — responsive table-to-card layout on
    narrow screens, same pattern as `students.html`.
  - **Filters**: category, status, and a name/designation search box.
  - **+ Add Video Review** button and per-row **View / Edit / Delete / Publish /
    Unpublish** actions.
  - **Add/Edit modal**: Name, Category (Student/Teacher/Parent), Course (dropdown
    populated from the teacher's own courses via the existing `GET /teacher/courses`),
    Designation, Short Description, Video (file picker → uploads via the new
    `/teacher/success-stories/video-upload` endpoint with a live progress bar), Thumbnail
    (file picker → uploads via the existing generic `POST /files` endpoint, same as every
    other image upload on the site, folder `success-stories`), and Status (Draft/Published).
    - The create/update payload never sends `status` directly, matching PART 2/6's
      deliberate design (a story always starts `DRAFT`; publish/unpublish are their own
      endpoints). Instead, after a successful create/update, if the form's Status field
      differs from what was just saved, the page makes one follow-up
      `PATCH .../publish` or `PATCH .../unpublish` call. This gives the Teacher a single
      "Save Video Review" action while keeping the backend's DRAFT-by-default,
      explicit-publish design intact and unbypassed.
  - **View modal**: read-only detail view with inline video playback.
  - **Delete**: `window.confirm()` guard (same pattern as "Remove Video" / "Deactivate"
    elsewhere in Teacher Admin) before calling `DELETE /teacher/success-stories/{id}`.

## Security

Unchanged from PART 2/6: every `/api/v1/teacher/success-stories/**` endpoint (including
the new `video-upload` one) sits behind `TeacherAuthInterceptor` (session token) **and**
`@RequireRole(Role.TEACHER)`, so a Main Admin or Student session is rejected with 403.
The public, read-only, published-only `SuccessStoryController` (`/api/v1/success-stories/**`)
is completely untouched.

## Manual test flow (per the PART 3/6 spec)

```
Teacher login
  -> Teacher Admin
  -> Success Stories (new sidebar item)
  -> "+ Add Video Review"
  -> fill Name, pick Category (Student/Teacher/Parent), optionally pick a Course
  -> "Choose Video File" -> uploads via POST /teacher/success-stories/video-upload
  -> optionally "Choose Thumbnail Image" -> uploads via POST /files
  -> "Save Video Review" (creates as DRAFT, or immediately publishes if Status=Published)
  -> row appears in the management table
  -> "Publish" (if left Draft) -> status badge flips to Published
  -> verify the story now appears on the public /success-stories.html page
  -> "Unpublish" -> disappears from the public page again, still in the management list
  -> "Edit" -> change a field -> "Save Video Review" -> list updates
  -> "Delete" -> confirm -> row removed, uploaded video/thumbnail files cleaned up
```

Everything else in Teacher Admin (Dashboard, My Courses, Content Management, Students,
Student Progress, Profile) is untouched and continues to work exactly as before this part.

## Build verification note

Same limitation as PART 2/6's own audit note: this sandbox only has an allow-listed set of
package-registry domains (npm, PyPI, crates.io, GitHub, Ubuntu archives) and no access to
Maven Central, so `mvn -q compile` could not be run here. Every changed/added Java file was
reviewed by hand, cross-checked with `grep` for stale references, and had its braces/parens
balance-checked. Please run `mvn -q compile` (or `BUILD-WINDOWS.cmd`) locally as the final
check before deploying, exactly as recommended in PART 2/6.
