# PART 4/10 — Universal Media Compatibility & FFmpeg

## Behavior

- FFprobe inspects actual streams before storage.
- Video is normalized to MP4 with H.264 video and AAC audio when the original is not already browser-compatible.
- Audio is normalized to MP3 when the original is not already MP3 or AAC/M4A.
- Compatible originals are preserved without an unnecessary duplicate.
- Temporary probe/transcode files are deleted after storage.
- FFmpeg is invoked with direct process arguments, never through a shell.
- Stream mismatches, corrupt files, missing FFmpeg/FFprobe, timeouts, and conversion failures return a safe backend error.

## Deployment

Install both `ffmpeg` and `ffprobe`, ensure the backend process can execute them, and configure the paths if they are not on `PATH`. Conversion is enabled by default and can be disabled with `app.media.ffmpeg-enabled=false`; with conversion disabled, Part 3 validation remains active.

## Testing status

The archive was checked for ZIP integrity and the FFmpeg executable was detected in this build environment. Full Maven tests require Java 21 and Maven in the deployment/test environment. Add integration coverage with browser-compatible, convertible, invalid, and conversion-failure fixtures before production rollout.