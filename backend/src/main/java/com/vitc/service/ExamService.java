package com.vitc.service;

import com.vitc.dto.request.ExamScheduleRequest;
import com.vitc.dto.response.ExamResponse;
import java.util.List;

public interface ExamService {
    List<ExamResponse> studentExams(Long studentId);
    ExamResponse apply(Long studentId, Long enrollmentId);
    List<ExamResponse> teacherReady(Long teacherId);
    ExamResponse schedule(Long teacherId, Long enrollmentId, ExamScheduleRequest request);
}
