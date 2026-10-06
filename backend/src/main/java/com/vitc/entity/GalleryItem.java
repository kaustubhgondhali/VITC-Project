package com.vitc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gallery_items")
public class GalleryItem extends BaseEntity {

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "image_url", nullable = false, length = 400)
    private String imageUrl;

    @Column(length = 60)
    private String category;

    @Column(length = 500)
    private String caption;

}
