# PART 5/10 — Upload Size Configuration & Safe Media Storage

## Files changed

- `backend/src/main/resources/application.properties`
- `backend/src/main/java/com/vitc/service/MediaCompatibilityService.java`
- `backend/src/main/java/com/vitc/service/impl/FileStorageServiceImpl.java`
- `backend/src/main/java/com/vitc/security/upload/UploadSecurityProperties.java`
- `backend/src/test/java/com/vitc/TeacherVideoUploadSizeLimitTest.java`
- `README.md`

## Configuration

The transport limits have a single source of truth:

- `app.media.max-file-size=500MB`
- `app.media.max-request-size=520MB`
- `app.media.audio-max-file-size=100MB`

Spring multipart limits and backend media validation reference these values. The request limit is
larger to allow multipart framing while still preventing unlimited requests. A reverse proxy
must be configured to allow no more than the request limit.

## Storage

Binary data remains on disk under `app.upload.dir` (`uploads/` by default). `media_files` stores
the generated relative reference, sanitized original name, MIME type, byte size, media type,
folder, uploader and timestamps. The original filename is never used as a physical path.

Generated names use a date plus UUID and are written only after normalized paths are confirmed to
remain below the configured root. POSIX executable permissions are removed. Folder names are
sanitized, and cleanup checks managed URL prefixes and normalized paths before deleting anything.

## Filename handling and security

Spaces, parentheses, hyphens, Unicode, multiple dots and long names are accepted when the media
content is valid. Names are reduced to a safe final display segment and truncated for metadata;
the stored filename is independent of the original. This prevents traversal, collisions and
accidental access outside the upload root.

## Testing

The archive was checked for ZIP integrity. FFmpeg/FFprobe conversion fixtures from Part 4 were
verified. The project already contains video size-limit coverage, updated to the central
`app.media.max-file-size` property. Full Maven execution still requires Java 21 and Maven on the
machine running the tests.