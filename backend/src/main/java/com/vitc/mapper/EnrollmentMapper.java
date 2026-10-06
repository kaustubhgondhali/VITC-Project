package com.vitc.mapper;

import com.vitc.dto.request.EnrollmentRequest;
import com.vitc.dto.response.EnrollmentResponse;
import com.vitc.entity.Course;
import com.vitc.entity.Enrollment;

public final class EnrollmentMapper {

    private EnrollmentMapper() {
    }

    public static Enrollment toEntity(EnrollmentRequest request, Course course) {
        Enrollment entity = new Enrollment();
        apply(entity, request, course);
        return entity;
    }

    public static void apply(Enrollment entity, EnrollmentRequest request, Course course) {
        entity.setStudentName(request.studentName());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setCollege(request.college());
        entity.setNotes(request.notes());
        entity.setCourse(course);
        entity.setAmount(course.getPrice());
    }

    public static EnrollmentResponse toResponse(Enrollment entity) {
        return new EnrollmentResponse(
                entity.getId(),
                entity.getStudentName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getCollege(),
                entity.getCourse() != null ? entity.getCourse().getId() : null,
                entity.getCourse() != null ? entity.getCourse().getTitle() : null,
                entity.getAmount(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt());
    }
}
