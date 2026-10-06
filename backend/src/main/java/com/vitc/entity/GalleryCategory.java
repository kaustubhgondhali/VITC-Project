package com.vitc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "gallery_categories")
public class GalleryCategory extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    public GalleryCategory(String name) {
        this.name = name;
    }
}