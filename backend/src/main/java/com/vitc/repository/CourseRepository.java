package com.vitc.repository;

import com.vitc.entity.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCode(String code);

    boolean existsByCode(String code);

    List<Course> findByActiveTrue();

    List<Course> findByCategoryIgnoreCase(String category);

    /* ---------------- Teacher Dashboard ---------------- */

    /** Courses assigned to a given Teacher Admin - the only courses that teacher may see. */
    List<Course> findByTeacherId(Long teacherId);

    long countByTeacherId(Long teacherId);

    List<Course> findByTeacherIdIsNull();
}
