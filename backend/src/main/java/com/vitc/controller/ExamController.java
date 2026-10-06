package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.ExamScheduleRequest;
import com.vitc.dto.response.ExamResponse;
import com.vitc.security.*;
import com.vitc.service.ExamService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class ExamController {
    private final ExamService service;
    @GetMapping("/api/v1/student/exams")
    @RequireRole(Role.STUDENT)
    public ResponseEntity<ApiResponse<List<ExamResponse>>> student(@RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long id) { return ResponseEntity.ok(ApiResponse.ok(service.studentExams(id))); }
    @PostMapping("/api/v1/student/exams/{enrollmentId}/apply")
    @RequireRole(Role.STUDENT)
    public ResponseEntity<ApiResponse<ExamResponse>> apply(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long id,
            @PathVariable Long enrollmentId) {
        return ResponseEntity.ok(ApiResponse.ok("Exam application submitted", service.apply(id, enrollmentId)));
    }
    @GetMapping("/api/v1/teacher/exam-ready-students")
    @RequireRole(Role.TEACHER)
    public ResponseEntity<ApiResponse<List<ExamResponse>>> ready(@RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long id) { return ResponseEntity.ok(ApiResponse.ok(service.teacherReady(id))); }
    @PostMapping("/api/v1/teacher/exams/{enrollmentId}")
    @RequireRole(Role.TEACHER)
    public ResponseEntity<ApiResponse<ExamResponse>> schedule(@RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long id, @PathVariable Long enrollmentId, @Valid @RequestBody ExamScheduleRequest request) { return ResponseEntity.ok(ApiResponse.ok("Exam scheduled", service.schedule(id, enrollmentId, request))); }
}
