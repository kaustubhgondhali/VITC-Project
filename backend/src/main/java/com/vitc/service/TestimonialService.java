package com.vitc.service;

import com.vitc.dto.request.TestimonialRequest;
import com.vitc.dto.response.TestimonialResponse;
import java.util.List;

public interface TestimonialService {

    List<TestimonialResponse> getAll();

    TestimonialResponse getById(Long id);

    TestimonialResponse create(TestimonialRequest request);

    TestimonialResponse update(Long id, TestimonialRequest request);

    void delete(Long id);

    List<TestimonialResponse> getApproved();

    TestimonialResponse setApproved(Long id, boolean approved);
}
