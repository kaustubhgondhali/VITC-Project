package com.vitc.service;

import com.vitc.dto.response.MediaFileResponse;
import com.vitc.dto.response.SuccessStoryVideoUploadResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    MediaFileResponse upload(MultipartFile file, String folder, String uploadedBy);

    List<MediaFileResponse> uploadMany(MultipartFile[] files, String folder, String uploadedBy);

    /**
     * PART 9C-VIDEO: dedicated video upload used by Teacher Admin lesson videos.
     *
     * <p>Stricter than {@link #upload}: only MP4/WebM/MOV, MIME-type checked, and capped by its
     * own size limit rather than the generic multipart ceiling. Stored under {@code uploads/videos/}
     * with a generated filename (never the original filename). Returns the same
     * {@link MediaFileResponse} shape as every other upload, so the caller only needs the
     * resulting {@code url} to save onto {@code CourseLesson.videoUrl}.</p>
     */
    MediaFileResponse uploadVideo(MultipartFile file, String uploadedBy);

    MediaFileResponse uploadAudio(MultipartFile file, String uploadedBy);

    /**
     * PART 3/6 — SUCCESS STORIES TEACHER ADMIN MANAGEMENT.
     *
     * <p>Same validation/storage mechanics as {@link #uploadVideo}, but stored under a
     * separate, publicly-servable folder — success story videos are shown on the public
     * "Success Stories" page once published, unlike protected lesson videos, so they are
     * deliberately kept out of the {@code uploads/videos/} folder that
     * {@code VideoDirectAccessInterceptor} locks down to the enrolment-checked student
     * streaming endpoint only.</p>
     */
    SuccessStoryVideoUploadResponse uploadSuccessStoryVideo(MultipartFile file, String uploadedBy);

    /**
     * PART 6A/8: best-effort cleanup used by the Replace Video flow.
     *
     * <p>Deletes the physical file (and its {@code MediaFile} row, if any) behind {@code videoUrl}
     * ONLY when the url points inside the VITC-managed {@code uploads/videos/} directory. Any other
     * url (an external {@code https://...} link, or anything outside that folder) is left completely
     * untouched - this method returns silently for those. A missing/already-deleted file is not an
     * error either: by the time this runs, the lesson has already been switched to its new video, so
     * there is nothing left to roll back to.</p>
     */
    void deleteVideoIfManaged(String videoUrl);

    /**
     * PART 3/6 — SUCCESS STORIES TEACHER ADMIN MANAGEMENT.
     *
     * <p>Generalised version of {@link #deleteVideoIfManaged}: deletes the physical file (and its
     * {@code MediaFile} row, if any) behind {@code url} ONLY when it points inside the given
     * VITC-managed {@code folder}. Anything else (an external link, a file in a different folder)
     * is left completely untouched. Used to clean up a Success Story's uploaded video/thumbnail
     * when the story itself is deleted, without ever risking an unrelated file.</p>
     */
    void deleteFileIfManaged(String url, String folder);

    List<MediaFileResponse> getAll(String fileType, String folder);

    MediaFileResponse getById(Long id);

    void delete(Long id);

    void deleteMany(List<Long> ids);
}
