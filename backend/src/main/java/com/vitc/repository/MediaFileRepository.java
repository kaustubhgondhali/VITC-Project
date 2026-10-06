package com.vitc.repository;

import com.vitc.entity.MediaFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    List<MediaFile> findAllByOrderByCreatedAtDesc();

    List<MediaFile> findByFileTypeIgnoreCaseOrderByCreatedAtDesc(String fileType);

    List<MediaFile> findByFolderIgnoreCaseOrderByCreatedAtDesc(String folder);

    Optional<MediaFile> findByFileName(String fileName);
}
