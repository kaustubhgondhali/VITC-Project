package com.vitc.repository;

import com.vitc.entity.BlogPost;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    Optional<BlogPost> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<BlogPost> findByPublishedTrueOrderByPublishedAtDesc();
}
