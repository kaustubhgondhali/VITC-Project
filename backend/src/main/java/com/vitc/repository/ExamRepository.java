package com.vitc.repository;

import com.vitc.entity.Exam;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByEnrollmentId(Long enrollmentId);
    List<Exam> findByEnrollmentUserIdOrderByIdDesc(Long userId);
    List<Exam> findByEnrollmentCourseTeacherIdAndStatusOrderByIdDesc(Long teacherId, com.vitc.entity.enums.ExamStatus status);
}
