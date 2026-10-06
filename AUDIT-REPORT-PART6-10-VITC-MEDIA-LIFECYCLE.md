# PART 6/10 — Media Activate, Deactivate, Delete & Replace

## Lifecycle implementation

- Upload and replace are available for teacher and main-admin lesson content.
- Existing lesson activation controls are the media lifecycle control:
  `PATCH /api/v1/teacher/lessons/{lessonId}/status?active=true|false`.
- Student lesson queries already require active course, module and lesson records, so inactive
  media lessons are not returned or playable.
- Deactivation does not delete the physical media.
- Admin and teacher lesson/module deletion removes database references and then cleans managed
  video/audio files.

## Replace behavior

The replacement is uploaded, probed/transcoded, validated and stored first. Only after that
does the lesson point to the new URL. The old URL is cleaned only after it is no longer attached
to a lesson. Failed validation or storage leaves the old reference untouched.

## Delete behavior

Managed paths are normalized beneath `app.upload.dir`. External URLs are never deleted. A
database media record that is still referenced by any lesson is refused by the generic media
delete endpoint, preventing deletion of another lesson's media. Missing physical files are
handled gracefully while their stale database record is removed when safe.

## Testing and remaining issues

ZIP integrity and existing FFmpeg fixtures were checked. Existing video lifecycle tests cover
replacement, authorization and streaming behavior. Full Maven execution requires Java 21 and
Maven. Media lifecycle is represented by the lesson `active` flag rather than a second,
duplicated media-status column, preserving the existing course-content architecture.