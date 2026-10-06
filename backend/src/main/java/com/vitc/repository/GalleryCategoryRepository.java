package com.vitc.repository;

import com.vitc.entity.GalleryCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GalleryCategoryRepository extends JpaRepository<GalleryCategory, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<GalleryCategory> findByNameIgnoreCase(String name);
}