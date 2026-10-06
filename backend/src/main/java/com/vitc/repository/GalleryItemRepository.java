package com.vitc.repository;

import com.vitc.entity.GalleryItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GalleryItemRepository extends JpaRepository<GalleryItem, Long> {

    List<GalleryItem> findByCategoryIgnoreCase(String category);
}
