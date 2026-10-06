package com.vitc.repository;

import com.vitc.entity.CourseModule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    List<CourseModule> findByCourseIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long courseId);

    List<CourseModule> findByCourseIdOrderByDisplayOrderAscIdAsc(Long courseId);

    long countByCourseId(Long courseId);

    /** Used by the Teacher Dashboard to total modules across every course a teacher owns. */
    long countByCourseIdIn(List<Long> courseIds);
}
