package com.vitc.controller;

import com.vitc.service.StudentVideoStreamService;
import com.vitc.util.FileSliceResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PART 6C-2A/8, hardened in PART 6C-2C/8 - the protected media stream endpoints.
 *
 * <p><b>Deliberately not under {@code StudentAuthInterceptor} / {@code @RequireRole}:</b> a
 * native HTML {@code <video>} element issues its own GET (and Range) requests directly to
 * {@code src}, without JavaScript in the loop, so it cannot attach the
 * {@code X-Student-Id} / {@code X-Student-Token} headers every other student API relies on.
 * This endpoint is excluded from that interceptor in {@code WebConfig} and instead trusts
 * only the short-lived, signed {@code token} query parameter minted by
 * {@code GET /api/v1/student/lessons/{lessonId}} - which itself sits behind the full
 * Authentication -> Role -> Enrollment -> Lesson-access chain.</p>
 *
 * <p><b>PART 6C-2C/8 - every request re-runs the full chain, Range or not:</b>
 * {@link #stream} calls {@code streamService.resolve(lessonId, token)} unconditionally,
 * before the Range header is even inspected. There is no fast path that skips
 * authentication/enrolment for a seek - a scrub/seek Range request is exactly as expensive,
 * security-wise, as the very first request for a lesson. See
 * {@link com.vitc.service.impl.StudentVideoStreamServiceImpl} for the token/enrolment
 * re-verification this performs on every single call.</p>
 *
 * <p><b>Streaming, not buffering:</b> the response body is always a {@code Resource} or
 * bounded {@code Resource} backed by a {@code FileSystemResource} - Spring's message converters
 * copy the file to the response in a fixed-size buffer loop. This class never reads a video
 * file into a {@code byte[]}, regardless of file size.</p>
 *
 * <ul>
 *   <li>No {@code Range} header -&gt; normal playback: {@code 200 OK}, full
 *       {@code Content-Length}, {@code Accept-Ranges: bytes} advertised so the player knows it
 *       can subsequently seek.</li>
 *   <li>A {@code Range} header -&gt; {@code 206 Partial Content} with {@code Content-Range} /
 *       {@code Content-Length} for just that slice, capped at {@link #CHUNK_SIZE} even if the
 *       browser asked for the rest of the file (e.g. {@code bytes=0-}) - this is what keeps a
 *       100/250/500MB file from being streamed as one unbounded response; the player simply
 *       issues another Range request for the next chunk, exactly like a normal CDN video
 *       stream.</li>
 *   <li>A malformed or out-of-bounds {@code Range} -&gt; {@code 416 Range Not Satisfiable} with
 *       {@code Content-Range: bytes * /<length>}, per HTTP semantics, instead of a 500 or a
 *       silently-wrong response.</li>
 * </ul>
 */
@Tag(name = "Student Learning", description = "Protected course video streaming")
@RestController
@RequiredArgsConstructor
public class StudentVideoStreamController {

    /**
     * Hard ceiling on how many bytes a single response ever returns, Range request or not.
     * Keeps memory/network use flat no matter how large the underlying file is or how much of
     * it the browser's Range header asks for in one go - large files are always served as a
     * sequence of bounded chunks, the same way real video CDNs behave, rather than one huge
     * write.
     */
    private static final long CHUNK_SIZE = 2 * 1024 * 1024; // 2MB

    private final StudentVideoStreamService streamService;

    @Operation(summary = "Stream an authorised lesson's local video (requires a lesson-scoped token)")
    @GetMapping("/api/v1/student/lessons/{lessonId}/video")
    public ResponseEntity<?> streamVideo(
            @PathVariable Long lessonId,
            @RequestParam String token,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        return stream(streamService.resolve(lessonId, token), rangeHeader);
    }

    @Operation(summary = "Stream an authorised lesson's local audio (requires a lesson-scoped token)")
    @GetMapping("/api/v1/student/lessons/{lessonId}/audio")
    public ResponseEntity<?> streamAudio(
            @PathVariable Long lessonId,
            @RequestParam String token,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        return stream(streamService.resolveAudio(lessonId, token), rangeHeader);
    }

    private ResponseEntity<?> stream(
            StudentVideoStreamService.ResolvedVideo video, String rangeHeader) throws IOException {

        // Authentication -> Role -> Enrollment -> Lesson -> Video, re-run on EVERY call -
        // a Range request gets no shortcut, no caching of a prior check; it goes through this
        // exact same call before a single byte is read or a header is written.
        FileSystemResource resource = new FileSystemResource(video.path());
        long contentLength = resource.contentLength();
        MediaType mediaType = MediaType.parseMediaType(video.contentType());

        if (rangeHeader == null || rangeHeader.isBlank()) {
            return normalResponse(resource, contentLength, mediaType);
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException malformedRange) {
            return unsatisfiable(contentLength);
        }
        if (ranges.isEmpty()) {
            return unsatisfiable(contentLength);
        }

        // Only the first requested range is honoured - what every real video player actually
        // sends (one range per request, issuing a fresh request per chunk as it buffers/seeks).
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        if (start < 0 || start >= contentLength || end < start) {
            return unsatisfiable(contentLength);
        }

        long rangeLength = Math.min(CHUNK_SIZE, end - start + 1);
        FileSliceResource region = new FileSliceResource(video.path(), start, rangeLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + (start + rangeLength - 1) + "/" + contentLength)
                .contentType(mediaType)
                .contentLength(rangeLength)
                .body(region);
    }

    /** No Range header: ordinary full-file playback, still streamed rather than buffered. */
    private static ResponseEntity<FileSystemResource> normalResponse(
            FileSystemResource resource, long contentLength, MediaType mediaType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(mediaType)
                .contentLength(contentLength)
                .body(resource);
    }

    /** RFC 7233 - a Range that cannot be satisfied gets 416 with the resource's real length. */
    private static ResponseEntity<Void> unsatisfiable(long contentLength) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                .build();
    }
}
