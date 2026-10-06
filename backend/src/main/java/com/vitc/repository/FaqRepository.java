package com.vitc.repository;

import com.vitc.entity.Faq;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findByActiveTrueOrderByDisplayOrderAsc();

    List<Faq> findByCategoryIgnoreCaseOrderByDisplayOrderAsc(String category);

    boolean existsByQuestionIgnoreCase(String question);

    List<Faq> findByQuestionContainingIgnoreCaseOrAnswerContainingIgnoreCase(String question, String answer);
}
