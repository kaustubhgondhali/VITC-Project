# PART 3/10 — Audio Upload & Universal Media Validation

## Implemented

- Added MP3, WAV, AAC, M4A, OGG, FLAC, WMA and OPUS support.
- Added backend magic-byte/container checks through `AudioFormats`.
- Added audio size configuration (`app.upload.security.audio-max-size`, default 100MB).
- Added teacher-only lesson endpoints:
  - `PUT /api/v1/teacher/lessons/{lessonId}/audio`
  - `DELETE /api/v1/teacher/lessons/{lessonId}/audio`
- Added `audio_url` to `course_lessons` and to authorized admin/student lesson responses.
- Audio metadata is recorded in the existing `media_files` table as `file_type=AUDIO`, including canonical content type, sanitized original name, generated stored name, size, folder, uploader, and inherited timestamps.
- Storage names are generated server-side; upload folders and resolved paths are normalized and traversal checked; uploaded files have non-executable POSIX permissions.
- Invalid extensions, MIME combinations, corrupt signatures, empty files, dangerous names and oversized files are rejected with user-safe messages.

## Database

Run `database/audio_upload_part3_migration.sql` against an existing database. Fresh database scripts should include the same nullable `audio_url VARCHAR(600)` column in `course_lessons`.

## Limitations

Container signature checks prove the file is structurally recognizable, not that every codec/player can decode it. Full media transcoding and duration extraction are intentionally outside this part.