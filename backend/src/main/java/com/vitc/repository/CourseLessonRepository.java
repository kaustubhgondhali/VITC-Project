package com.vitc.repository;

import com.vitc.entity.CourseLesson;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseLessonRepository extends JpaRepository<CourseLesson, Long> {

    List<CourseLesson> findByModuleIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long moduleId);

    /** Admin view: active and inactive lessons alike, in display order. */
    List<CourseLesson> findByModuleIdOrderByDisplayOrderAscIdAsc(Long moduleId);

    /** Every active lesson of a course, in study order. */
    @Query("""
            select l from CourseLesson l
              join l.module m
             where m.course.id = :courseId
               and l.active = true
               and m.active = true
             order by m.displayOrder asc, m.id asc, l.displayOrder asc, l.id asc
            """)
    List<CourseLesson> findActiveByCourseId(Long courseId);

    /* ---------------- Teacher Dashboard ---------------- */

    /** Total lessons across every course a teacher owns. */
    @Query("select count(l) from CourseLesson l where l.module.course.id in :courseIds")
    long countByCourseIds(List<Long> courseIds);

    /** Total lessons that have a video attached, across every course a teacher owns. */
    @Query("""
            select count(l) from CourseLesson l
             where l.module.course.id in :courseIds
               and l.videoUrl is not null
               and l.videoUrl <> ''
            """)
    long countVideosByCourseIds(List<Long> courseIds);

    boolean existsByVideoUrl(String videoUrl);

    boolean existsByAudioUrl(String audioUrl);
}
