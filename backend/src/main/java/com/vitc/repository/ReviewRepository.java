package com.vitc.repository;

import com.vitc.entity.Review;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByApprovedTrueOrderByCreatedAtDesc();

    List<Review> findByFeaturedTrueAndApprovedTrue();

    List<Review> findByCourseCodeIgnoreCase(String courseCode);

    List<Review> findByEmailIgnoreCase(String email);

    Page<Review> findByApprovedTrue(Pageable pageable);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.approved = true and lower(r.courseCode) = lower(:courseCode)")
    Double averageRatingForCourse(String courseCode);
}
