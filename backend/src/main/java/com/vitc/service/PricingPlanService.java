package com.vitc.service;

import com.vitc.dto.request.PricingPlanRequest;
import com.vitc.dto.response.PricingPlanResponse;
import java.util.List;

public interface PricingPlanService {

    List<PricingPlanResponse> getAll();

    List<PricingPlanResponse> getActive();

    List<PricingPlanResponse> getByCategory(String category);

    PricingPlanResponse getById(Long id);

    PricingPlanResponse create(PricingPlanRequest request);

    PricingPlanResponse update(Long id, PricingPlanRequest request);

    PricingPlanResponse updateActive(Long id, boolean active);

    void delete(Long id);
}
