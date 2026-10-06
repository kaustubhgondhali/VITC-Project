package com.vitc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
@Table(name = "media_files")
public class MediaFile extends BaseEntity {

    @Column(name = "file_name", nullable = false, length = 260)
    private String fileName;

    @Column(name = "original_name", nullable = false, length = 260)
    private String originalName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 400)
    private String url;

    /** IMAGE | DOCUMENT | OTHER */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(length = 60)
    private String folder;

    @Column(name = "uploaded_by", length = 60)
    private String uploadedBy;
}
