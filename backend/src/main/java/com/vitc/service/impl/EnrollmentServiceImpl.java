package com.vitc.service.impl;

import com.vitc.dto.request.EnrollmentRequest;
import com.vitc.dto.response.EnrollmentResponse;
import com.vitc.entity.Course;
import com.vitc.entity.Enrollment;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.EnrollmentMapper;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.service.EnrollmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository repository;
    private final CourseRepository courseRepository;

    @Override
    public List<EnrollmentResponse> getAll() {
        return repository.findAll().stream().map(EnrollmentMapper::toResponse).toList();
    }

    @Override
    public EnrollmentResponse getById(Long id) {
        return EnrollmentMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public EnrollmentResponse create(EnrollmentRequest request) {
        Course course = course(request.courseId());
        if (Boolean.FALSE.equals(course.getActive())) {
            throw new BadRequestException("Course is not open for enrollment: " + course.getTitle());
        }
        if (repository.existsByEmailIgnoreCaseAndCourseId(request.email(), course.getId())) {
            throw new BadRequestException("This email is already enrolled in the selected course");
        }
        return EnrollmentMapper.toResponse(repository.save(EnrollmentMapper.toEntity(request, course)));
    }

    @Override
    @Transactional
    public EnrollmentResponse update(Long id, EnrollmentRequest request) {
        Enrollment entity = find(id);
        EnrollmentMapper.apply(entity, request, course(request.courseId()));
        return EnrollmentMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public EnrollmentResponse updateStatus(Long id, EnrollmentStatus status) {
        Enrollment entity = find(id);
        entity.setStatus(status);
        return EnrollmentMapper.toResponse(repository.save(entity));
    }

    @Override
    public List<EnrollmentResponse> getByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).stream().map(EnrollmentMapper::toResponse).toList();
    }

    @Override
    public List<EnrollmentResponse> getByCourse(Long courseId) {
        return repository.findByCourseId(courseId).stream().map(EnrollmentMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Enrollment find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Enrollment", id));
    }

    private Course course(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }
}
