package com.vitc.service;

import com.vitc.dto.request.FaqRequest;
import com.vitc.dto.response.FaqResponse;
import java.util.List;

public interface FaqService {

    List<FaqResponse> getAll();

    FaqResponse getById(Long id);

    FaqResponse create(FaqRequest request);

    FaqResponse update(Long id, FaqRequest request);

    void delete(Long id);

    List<FaqResponse> getActive();

    List<FaqResponse> getByCategory(String category);

    List<FaqResponse> search(String keyword);

    FaqResponse updateActive(Long id, boolean active);
}
