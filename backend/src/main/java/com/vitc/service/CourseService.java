package com.vitc.service;

import com.vitc.dto.request.CourseRequest;
import com.vitc.dto.response.CourseResponse;
import java.util.List;

public interface CourseService {

    List<CourseResponse> getAll();

    CourseResponse getById(Long id);

    CourseResponse create(CourseRequest request);

    CourseResponse update(Long id, CourseRequest request);

    void delete(Long id);

    List<CourseResponse> getActive();

    CourseResponse getByCode(String code);

    List<CourseResponse> getByCategory(String category);
}
