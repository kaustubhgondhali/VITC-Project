package com.vitc.service;

import com.vitc.dto.request.AdminTeacherRequest;
import com.vitc.dto.request.AdminTeacherUpdateRequest;
import com.vitc.dto.request.TeacherCourseAssignmentRequest;
import com.vitc.dto.request.TeacherPasswordResetRequest;
import com.vitc.dto.response.AdminTeacherCourseResponse;
import com.vitc.dto.response.AdminTeacherResponse;
import com.vitc.dto.response.TeacherCredentialsResponse;
import com.vitc.entity.enums.UserStatus;
import java.util.List;

/**
 * PART 10C - Main Admin -&gt; Teachers. A section of the EXISTING Main Admin
 * panel: every endpoint lives under {@code /api/v1/admin/**} and is therefore
 * already guarded by {@code AdminAuthInterceptor}, so a Teacher or Student
 * session token can never reach it (403). No second admin system is created.
 *
 * <p>Course assignment is persisted on the existing {@code courses.teacher_id}
 * column, which is the same column {@code TeacherAuthorizationService} checks
 * on every Teacher API call - so assigning/removing a course changes the
 * teacher's real backend authorisation, not just the UI.</p>
 *
 * <p>Deactivating a teacher or removing an assignment never deletes modules,
 * lessons, course content or any historical record: only the authorisation is
 * withdrawn.</p>
 */
public interface AdminTeacherService {

    List<AdminTeacherResponse> list();

    AdminTeacherResponse get(Long teacherId);

    /** Creates the Teacher account (BCrypt hash only) and optionally assigns courses. */
    TeacherCredentialsResponse create(AdminTeacherRequest request);

    AdminTeacherResponse update(Long teacherId, AdminTeacherUpdateRequest request);

    /** ACTIVE / INACTIVE. Deactivation also kills any live teacher session. */
    AdminTeacherResponse updateStatus(Long teacherId, UserStatus status);

    /** Replaces the teacher's course assignments with exactly the given list. */
    AdminTeacherResponse assignCourses(Long teacherId, TeacherCourseAssignmentRequest request);

    /** Removes a single course assignment; the course and its content stay intact. */
    AdminTeacherResponse removeCourse(Long teacherId, Long courseId);

    List<AdminTeacherCourseResponse> assignedCourses(Long teacherId);

    /** Every course, with its current assignment - used by the assign dialog. */
    List<AdminTeacherCourseResponse> allCourses();

    TeacherCredentialsResponse resetPassword(Long teacherId, TeacherPasswordResetRequest request);
}
