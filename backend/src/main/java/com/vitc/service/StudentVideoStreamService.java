package com.vitc.service;

/**
 * PART 6C-2A/8 - resolves and validates a signed media-access token issued by
 * {@link com.vitc.service.StudentLearningService#lesson}, then re-verifies
 * Authentication -> Role -> Enrollment -> Lesson-access at stream time before
 * handing back the on-disk file location. Never accepts a filesystem path
 * from the caller - the only inputs are the token and the lessonId in the URL.
 */
public interface StudentVideoStreamService {

    /**
     * Validates {@code token} against {@code lessonId}, re-checks the student's
     * enrolment is still active, and returns the absolute path of the local
     * video file on disk (never a URL, never client-supplied).
     *
     * @throws com.vitc.exception.ForbiddenException   token invalid/expired/mismatched,
     *                                                  or enrolment no longer active
     * @throws com.vitc.exception.ResourceNotFoundException lesson missing or has no local video
     */
    ResolvedVideo resolve(Long lessonId, String token);

    /** Resolves the lesson's protected local audio using the same authorization chain. */
    ResolvedVideo resolveAudio(Long lessonId, String token);

    record ResolvedVideo(java.nio.file.Path path, String contentType) {
    }
}
