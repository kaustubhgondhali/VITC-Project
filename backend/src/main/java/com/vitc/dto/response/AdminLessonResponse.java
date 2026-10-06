package com.vitc.dto.response;

/** Admin view of a lesson - includes the video url (admins manage it). */
public record AdminLessonResponse(
        Long id,
        Long moduleId,
        String title,
        String description,
        String videoUrl,
        String audioUrl,
        String duration,
        Integer displayOrder,
        Boolean active) {
}
