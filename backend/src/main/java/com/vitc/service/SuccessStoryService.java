package com.vitc.service;

import com.vitc.dto.request.SuccessStoryRequest;
import com.vitc.dto.response.SuccessStoryResponse;
import java.util.List;

public interface SuccessStoryService {

    /* ---------------- Public (published only) ---------------- */

    List<SuccessStoryResponse> getPublished();

    List<SuccessStoryResponse> getPublishedByCategory(String category);

    SuccessStoryResponse getPublishedById(Long id);

    /* ---------------- Teacher Admin (any status) ---------------- */

    List<SuccessStoryResponse> getAllForAdmin();

    List<SuccessStoryResponse> getByCategoryForAdmin(String category);

    SuccessStoryResponse getById(Long id);

    SuccessStoryResponse create(SuccessStoryRequest request, String createdBy, String createdByRole);

    SuccessStoryResponse update(Long id, SuccessStoryRequest request);

    void delete(Long id);

    SuccessStoryResponse publish(Long id);

    SuccessStoryResponse unpublish(Long id);
}
