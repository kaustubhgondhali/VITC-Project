package com.vitc.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * PART 6C-2A/8 - closes the direct-URL hole that {@code WebConfig}'s static
 * resource handler otherwise leaves open.
 *
 * <p>{@code WebConfig} serves every file under {@code app.upload.dir} at
 * {@code app.upload.public-path + "/**"} with no authentication at all, by
 * design, for ordinary uploads (gallery images, blog images, documents,
 * teacher/admin avatars, etc.) that are meant to be publicly linkable. Lesson
 * videos live in the same directory tree (under {@code uploads/videos/}) but
 * must NOT be reachable that way: a student (or anyone) who captured the URL
 * from a network tab could otherwise play a private course video forever,
 * fully bypassing enrolment.
 *
 * <p>This interceptor is scoped ONLY to {@code /uploads/videos/**} (see
 * {@code WebConfig#addInterceptors}) and always answers 403. Every other
 * upload path is completely untouched and keeps working exactly as before.
 * The one legitimate way to watch a lesson video is
 * {@code GET /api/v1/student/lessons/{lessonId}/video}, which is reached only
 * through the full enrolment-checked lesson lookup.</p>
 */
@Component
public class VideoDirectAccessInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Direct video access is not permitted. "
                        + "Open the lesson from the student portal.\"}");
        return false;
    }
}
