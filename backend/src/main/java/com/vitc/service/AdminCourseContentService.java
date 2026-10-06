package com.vitc.service;

import com.vitc.dto.request.AdminLessonRequest;
import com.vitc.dto.request.AdminModuleRequest;
import com.vitc.dto.response.AdminCourseContentResponse;
import com.vitc.dto.response.AdminLessonResponse;
import com.vitc.dto.response.AdminModuleResponse;
import org.springframework.web.multipart.MultipartFile;

/** Admin-side management of course modules and lessons (Part 3). */
public interface AdminCourseContentService {

    AdminCourseContentResponse content(Long courseId);

    AdminModuleResponse createModule(Long courseId, AdminModuleRequest request);

    AdminModuleResponse updateModule(Long moduleId, AdminModuleRequest request);

    void deleteModule(Long moduleId);

    AdminModuleResponse moveModule(Long moduleId, int direction);

    AdminLessonResponse createLesson(Long moduleId, AdminLessonRequest request);

    AdminLessonResponse updateLesson(Long lessonId, AdminLessonRequest request);

    void deleteLesson(Long lessonId);

    AdminLessonResponse moveLesson(Long lessonId, int direction);

    AdminLessonResponse setLessonAudio(Long lessonId, MultipartFile file);

    AdminLessonResponse clearLessonAudio(Long lessonId);
}
