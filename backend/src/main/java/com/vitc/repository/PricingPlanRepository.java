package com.vitc.repository;

import com.vitc.entity.PricingPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingPlanRepository extends JpaRepository<PricingPlan, Long> {

    List<PricingPlan> findByActiveTrueOrderByDisplayOrderAsc();

    List<PricingPlan> findByCategoryIgnoreCaseOrderByDisplayOrderAsc(String category);

    List<PricingPlan> findAllByOrderByDisplayOrderAsc();
}
