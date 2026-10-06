package com.vitc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
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
@Table(name = "blog_posts")
public class BlogPost extends BaseEntity {

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String excerpt;

    /*
     * PART 5 - the recurring "alter table blog_posts modify column content
     * LONGTEXT not null" on every startup (and the metadata-lock hang that
     * follows it if anything else holds an open connection to this table)
     * was caused by combining @Lob with an explicit columnDefinition here.
     * MySQL's JDBC driver reports a LONGTEXT column back as
     * Types.LONGVARCHAR, not the CLOB type @Lob maps a String to, so
     * Hibernate's schema comparison saw a "mismatch" and re-issued the same
     * ALTER on every single boot - even though nothing had actually changed.
     * columnDefinition = "LONGTEXT" already fully and explicitly declares the
     * physical column type, so @Lob is redundant here; removing it lets
     * Hibernate compare against LONGVARCHAR/LONGTEXT directly and stop
     * re-altering an already-correct column. No data is touched by this
     * change - it only affects how Hibernate compares/generates DDL for an
     * existing column.
     */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "cover_image_url", length = 400)
    private String coverImageUrl;

    @Column(length = 120)
    private String author;

    @Column(length = 300)
    private String tags;

    @Column(nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

}
