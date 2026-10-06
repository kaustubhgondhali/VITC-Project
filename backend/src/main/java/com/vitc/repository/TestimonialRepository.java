package com.vitc.repository;

import com.vitc.entity.Testimonial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    List<Testimonial> findByApprovedTrueOrderByCreatedAtDesc();
}
