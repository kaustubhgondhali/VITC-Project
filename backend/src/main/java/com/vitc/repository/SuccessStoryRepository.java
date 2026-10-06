package com.vitc.repository;

import com.vitc.entity.SuccessStory;
import com.vitc.entity.enums.SuccessStoryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuccessStoryRepository extends JpaRepository<SuccessStory, Long> {

    /* ---------------- Public (published only) ---------------- */

    List<SuccessStory> findByStatusOrderByDisplayOrderAscCreatedAtDesc(SuccessStoryStatus status);

    List<SuccessStory> findByStatusAndCategoryIgnoreCaseOrderByDisplayOrderAscCreatedAtDesc(
            SuccessStoryStatus status, String category);

    /* ---------------- Teacher Admin (any status) ---------------- */

    List<SuccessStory> findAllByOrderByDisplayOrderAscCreatedAtDesc();

    List<SuccessStory> findByCategoryIgnoreCaseOrderByDisplayOrderAscCreatedAtDesc(String category);

    long countByCategoryIgnoreCase(String category);

    long countByStatus(SuccessStoryStatus status);
}
