package com.vitc.service;

import com.vitc.common.PageResponse;
import com.vitc.dto.request.ReviewRequest;
import com.vitc.dto.response.ReviewResponse;
import java.util.List;

public interface ReviewService {

    List<ReviewResponse> getAll();

    PageResponse<ReviewResponse> getApprovedPaged(int page, int size);

    ReviewResponse getById(Long id);

    ReviewResponse create(ReviewRequest request);

    ReviewResponse update(Long id, ReviewRequest request);

    void delete(Long id);

    List<ReviewResponse> getApproved();

    List<ReviewResponse> getFeatured();

    List<ReviewResponse> getByCourseCode(String courseCode);

    List<ReviewResponse> getByEmail(String email);

    ReviewResponse updateApproval(Long id, boolean approved);

    Double averageRating(String courseCode);
}
