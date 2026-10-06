package com.vitc.repository;

import com.vitc.entity.Enrollment;
import com.vitc.entity.enums.EnrollmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByEmailIgnoreCase(String email);

    List<Enrollment> findByStatus(EnrollmentStatus status);

    List<Enrollment> findByCourseId(Long courseId);

    boolean existsByEmailIgnoreCaseAndCourseId(String email, Long courseId);

    /**
     * PART: Course Delete safety check. An enrolment is the historical record
     * that ties a course to a student's order/payment/progress trail, so its
     * presence (any status - PENDING, CONFIRMED, CANCELLED, ...) is what makes
     * a course "has business/historical data" and therefore not permanently
     * deletable. Used by {@code CourseServiceImpl#delete} before touching
     * anything.
     */
    long countByCourseId(Long courseId);

    /* ---------------- Student portal ---------------- */

    List<Enrollment> findByUserIdOrderByIdDesc(Long userId);

    Optional<Enrollment> findFirstByUserIdAndCourseId(Long userId, Long courseId);

    Optional<Enrollment> findFirstByEmailIgnoreCaseAndCourseIdOrderByIdDesc(String email, Long courseId);

    /* ---------------- Teacher Dashboard ---------------- */

    /** Distinct students (by email) with the given status, across every course a teacher owns. */
    @Query("""
            select count(distinct lower(e.email)) from Enrollment e
             where e.course.id in :courseIds
               and e.status = :status
            """)
    long countDistinctStudentsByCourseIdsAndStatus(List<Long> courseIds, EnrollmentStatus status);

    /** Distinct students (by email) enrolled at all, regardless of status, across a teacher's courses. */
    @Query("""
            select count(distinct lower(e.email)) from Enrollment e
             where e.course.id in :courseIds
            """)
    long countDistinctStudentsByCourseIds(List<Long> courseIds);
}
