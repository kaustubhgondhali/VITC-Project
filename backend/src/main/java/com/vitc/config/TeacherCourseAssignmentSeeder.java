package com.vitc.config;

import com.vitc.entity.Course;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development/demo convenience only: gives the Teacher Dashboard something
 * real to show out of the box, without building a Main-Admin
 * "assign teacher to course" screen yet (that is out of scope for this
 * part - see PART 8A spec, item 5).
 *
 * <p>Listens for {@link ApplicationReadyEvent} rather than being another
 * {@code ApplicationRunner}: that event fires only after every
 * {@code ApplicationRunner} bean (admin/teacher/catalogue/course-content
 * seeders) has already completed, so this never has to guess at their
 * relative {@code @Order} - it is guaranteed to run last.</p>
 *
 * <p>Safety rules:</p>
 * <ul>
 *   <li>Only ever touches courses whose {@code teacher_id} is still
 *       {@code null} - a course an admin has explicitly assigned (or
 *       explicitly unassigned) is never overwritten.</li>
 *   <li>Only runs while the first seeded Teacher Admin currently owns zero
 *       courses, so it never re-assigns courses away from a teacher the
 *       admin has since reorganised.</li>
 *   <li>Can be switched off entirely with
 *       {@code app.teacher.demo-assign-courses=false} (e.g. in production).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeacherCourseAssignmentSeeder {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Value("${app.teacher.demo-assign-courses:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void assignUnassignedCoursesToDefaultTeacher() {
        if (!enabled) {
            return;
        }
        List<User> teachers = userRepository.findByRole(UserRole.TEACHER);
        if (teachers.isEmpty()) {
            return;
        }
        User teacher = teachers.get(0);
        if (courseRepository.countByTeacherId(teacher.getId()) > 0) {
            return; // already has courses assigned - never touch it again
        }
        List<Course> unassigned = courseRepository.findByTeacherIdIsNull();
        if (unassigned.isEmpty()) {
            return;
        }
        unassigned.forEach(course -> course.setTeacherId(teacher.getId()));
        courseRepository.saveAll(unassigned);
        log.info("Assigned {} unassigned course(s) to default teacher '{}' for dashboard demo data.",
                unassigned.size(), teacher.getUsername());
    }
}
