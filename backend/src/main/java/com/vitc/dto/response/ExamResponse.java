package com.vitc.dto.response;

import com.vitc.entity.Exam;
import com.vitc.entity.enums.ExamStatus;
import java.time.*;

public record ExamResponse(Long id, Long enrollmentId, Long studentId, String studentName,
        String email, String phone, Long courseId, String courseName, String courseCode,
        LocalDate completionDate, LocalDate appliedDate, ExamStatus status, LocalDate examDate, LocalTime examTime,
        String mode, String locationOrLink, String notes) {
    public static ExamResponse of(Exam e) {
        var n = e.getEnrollment(); var c = n.getCourse();
        return new ExamResponse(e.getId(), n.getId(), n.getUser() == null ? null : n.getUser().getId(),
                n.getStudentName(), n.getEmail(), n.getPhone(), c.getId(), c.getTitle(), c.getCode(),
                e.getCompletionDate(), e.getAppliedDate(),
                e.getStatus(), e.getExamDate(), e.getExamTime(), e.getMode(), e.getLocationOrLink(), e.getNotes());
    }
}
