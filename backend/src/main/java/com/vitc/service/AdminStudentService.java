package com.vitc.service;

import com.vitc.dto.request.AdminEnrollmentRequest;
import com.vitc.dto.response.AdminStudentCourseResponse;
import com.vitc.dto.response.AdminStudentDetailResponse;
import com.vitc.dto.response.AdminStudentResponse;
import java.util.List;

/** Admin-side read/manage operations for student accounts (Part 3). */
public interface AdminStudentService {

    List<AdminStudentResponse> list();

    AdminStudentDetailResponse detail(Long studentId);

    List<AdminStudentCourseResponse> courses(Long studentId);

    AdminStudentDetailResponse grantEnrollment(Long studentId, AdminEnrollmentRequest request, String adminUsername);
}
