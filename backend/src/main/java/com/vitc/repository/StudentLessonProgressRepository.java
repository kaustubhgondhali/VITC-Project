package com.vitc.repository;

import com.vitc.entity.StudentLessonProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentLessonProgressRepository extends JpaRepository<StudentLessonProgress, Long> {

    Optional<StudentLessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    /** Progress rows of one student limited to one course - never another student's data. */
    @Query("""
            select p from StudentLessonProgress p
              join p.lesson l
              join l.module m
             where p.student.id = :studentId
               and m.course.id = :courseId
            """)
    List<StudentLessonProgress> findByStudentAndCourse(Long studentId, Long courseId);

    List<StudentLessonProgress> findByStudentId(Long studentId);

    /** Used when an admin deletes a lesson, so no orphan progress rows remain. */
    void deleteByLessonId(Long lessonId);

    void deleteByLessonModuleId(Long moduleId);
}
