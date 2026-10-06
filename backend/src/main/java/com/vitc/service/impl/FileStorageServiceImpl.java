package com.vitc.service.impl;

import com.vitc.dto.response.MediaFileResponse;
import com.vitc.dto.response.SuccessStoryVideoUploadResponse;
import com.vitc.entity.MediaFile;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.MediaFileMapper;
import com.vitc.repository.MediaFileRepository;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.security.upload.UploadCategory;
import com.vitc.security.upload.UploadFileValidator;
import com.vitc.service.FileStorageService;
import com.vitc.service.MediaCompatibilityService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private static final List<String> ALLOWED = List.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg", "avif",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip");

    /** PART 2/10 — VIDEO UPLOAD: the accepted containers (MP4, WebM, MOV, AVI, MKV, MPEG, MPG,
     *  M4V, 3GP, FLV, OGV) and their signatures live in
     *  {@link com.vitc.security.upload.VideoFormats}, shared with the validator so there is exactly
     *  one list. The generic {@link #upload} endpoint still refuses videos on purpose. */
    private static final List<String> ALLOWED_VIDEO_EXT =
            List.copyOf(com.vitc.security.upload.VideoFormats.allowedExtensions());

    private static final String VIDEO_FOLDER = "videos";
    private static final String AUDIO_FOLDER = "audio";

    /**
     * PART 3/6 — SUCCESS STORIES TEACHER ADMIN MANAGEMENT.
     *
     * <p>Deliberately a DIFFERENT folder than {@link #VIDEO_FOLDER}: lesson videos in
     * {@code uploads/videos/} are intentionally blocked from direct/static access by
     * {@code VideoDirectAccessInterceptor} (paid course content, only playable through the
     * enrolment-checked student streaming endpoint). A Success Story video is the opposite —
     * once published it is meant to be publicly playable on the public "Success Stories" page,
     * so it is stored under its own folder that the interceptor never touches, and served the
     * same way as any other public upload (gallery images, etc.).</p>
     */
    private static final String SUCCESS_STORY_VIDEO_FOLDER = "success-story-videos";
    private static final String SUCCESS_STORY_THUMBNAIL_FOLDER = "success-stories";

    private final MediaFileRepository repository;
    private final CourseLessonRepository lessonRepository;

    /** PART 2B-2/7 — every upload passes this single validation choke point. */
    private final UploadFileValidator uploadValidator;
    private final MediaCompatibilityService mediaCompatibilityService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    /** PART 5/10: configurable, not hard-coded - see app.media.max-file-size in application.properties.
     *  Parsed once at startup with Spring's {@link DataSize} so "500MB" style values just work. */
    @Value("${app.media.max-file-size:500MB}")
    private String videoMaxFileSizeConfig;

    @Value("${app.media.transcode-timeout-seconds:900}")
    private long mediaProcessTimeoutSeconds;

    @Value("${app.media.ffmpeg-binary:ffmpeg}")
    private String ffmpegBinary;

    private Path root;
    private long videoMaxBytes;

    @PostConstruct
    void init() {
        try {
            root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create upload directory: " + uploadDir, e);
        }
        videoMaxBytes = DataSize.parse(videoMaxFileSizeConfig).toBytes();
    }

    @Override
    @Transactional
    public MediaFileResponse upload(MultipartFile file, String folder, String uploadedBy) {
        String safeFolder = sanitizeFolder(folder);
        // PART 2B-2/7: the category (and therefore the size ceiling + allow-lists) is derived from
        // the destination folder and the real extension, never from one global limit.
        String rawExt = UploadFileValidator.extensionOf(
                UploadFileValidator.safeOriginalName(file == null ? null : file.getOriginalFilename(), null));
        UploadCategory category = UploadCategory.resolveForGenericUpload(safeFolder, rawExt);
        if (category == UploadCategory.VIDEO) {
            // Videos only go through their own authorised endpoints (lesson / success story).
            throw new BadRequestException("Video files must be uploaded from the video upload screen.");
        }
        UploadFileValidator.ValidatedUpload validated = uploadValidator.validate(file, category);
        String stored = validated.storedName();

        try {
            Path target = root.resolve(safeFolder).normalize();
            if (!target.startsWith(root)) {
                throw new BadRequestException("Invalid upload location");
            }
            Files.createDirectories(target);
            Path destination = target.resolve(stored).normalize();
            if (!destination.startsWith(target)) {
                throw new BadRequestException("Invalid file name");
            }
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            hardenPermissions(destination);

            MediaFile entity = MediaFile.builder()
                    .fileName(safeFolder + "/" + stored)
                    .originalName(validated.originalName())
                    .contentType(validated.contentType())
                    .sizeBytes(validated.sizeBytes())
                    .url(publicPath + "/" + safeFolder + "/" + stored)
                    .fileType(category.fileTypeLabel())
                    .folder(safeFolder)
                    .uploadedBy(uploadedBy)
                    .build();
            return MediaFileMapper.toResponse(repository.save(entity));
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            log.error("Upload failed while writing '{}' to folder '{}': {}", stored, safeFolder, e.getMessage(), e);
            throw new BadRequestException("The file could not be saved. Please try again.");
        } catch (RuntimeException e) {
            // FIX - REVIEWER PHOTO UPLOAD: any unexpected failure past this point (e.g. a
            // database problem while saving the MediaFile record) used to fall straight
            // through to the generic 500 handler and show "Unexpected error occurred" with
            // no way to tell what actually went wrong. The real exception is now always
            // logged here for the server operator, and the caller gets a clear, actionable
            // message instead of an opaque failure.
            log.error("Unexpected failure uploading '{}' to folder '{}'", stored, safeFolder, e);
            throw new BadRequestException("Upload failed. Please try again with a valid image "
                    + "(" + category.allowedHint() + ") under the size limit.");
        }
    }

    @Override
    @Transactional
    public MediaFileResponse uploadVideo(MultipartFile file, String uploadedBy) {
        var normalized = mediaCompatibilityService.normalize(file, UploadCategory.VIDEO);
        try { return storeVideo(normalized.file(), uploadedBy, VIDEO_FOLDER); }
        finally { normalized.close(); }
    }

    @Override
    @Transactional
    public MediaFileResponse uploadAudio(MultipartFile file, String uploadedBy) {
        var normalized = mediaCompatibilityService.normalize(file, UploadCategory.AUDIO);
        try { return storeAudio(normalized.file(), uploadedBy); }
        finally { normalized.close(); }
    }

    private MediaFileResponse storeAudio(MultipartFile file, String uploadedBy) {
        UploadFileValidator.ValidatedUpload validated = uploadValidator.validate(file, UploadCategory.AUDIO);
        String stored = validated.storedName();
        try {
            Path target = root.resolve(AUDIO_FOLDER).normalize();
            if (!target.startsWith(root)) throw new BadRequestException("Invalid upload location");
            Files.createDirectories(target);
            Path destination = target.resolve(stored).normalize();
            if (!destination.startsWith(target)) throw new BadRequestException("Invalid file name");
            try (java.io.InputStream in = file.getInputStream()) { Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING); }
            hardenPermissions(destination);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Audio storage failure: the file could not be saved.");
        }
        MediaFile entity = MediaFile.builder().fileName(AUDIO_FOLDER + "/" + stored)
                .originalName(validated.originalName()).contentType(validated.contentType())
                .sizeBytes(validated.sizeBytes()).url(publicPath + "/" + AUDIO_FOLDER + "/" + stored)
                .fileType("AUDIO").folder(AUDIO_FOLDER).uploadedBy(uploadedBy).build();
        return MediaFileMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public SuccessStoryVideoUploadResponse uploadSuccessStoryVideo(MultipartFile file, String uploadedBy) {
        var normalized = mediaCompatibilityService.normalize(file, UploadCategory.VIDEO);
        try {
            MediaFileResponse video = storeVideo(normalized.file(), uploadedBy, SUCCESS_STORY_VIDEO_FOLDER);
            String thumbnailUrl = generateSuccessStoryThumbnail(video, uploadedBy);
            return new SuccessStoryVideoUploadResponse(video.url(), thumbnailUrl);
        }
        finally { normalized.close(); }
    }

    private String generateSuccessStoryThumbnail(MediaFileResponse video, String uploadedBy) {
        String videoPrefix = publicPath + "/" + SUCCESS_STORY_VIDEO_FOLDER + "/";
        if (video == null || video.url() == null || !video.url().startsWith(videoPrefix)) return null;
        String relativeVideo = video.url().substring(publicPath.length() + 1);
        Path source = root.resolve(relativeVideo).normalize();
        if (!source.startsWith(root) || !Files.isRegularFile(source)) return null;

        String stored = LocalDate.now() + "-" + UUID.randomUUID() + ".jpg";
        Path targetDir = root.resolve(SUCCESS_STORY_THUMBNAIL_FOLDER).normalize();
        Path target = targetDir.resolve(stored).normalize();
        if (!targetDir.startsWith(root) || !target.startsWith(targetDir)) return null;
        try {
            Files.createDirectories(targetDir);
            Process process = new ProcessBuilder(ffmpegBinary, "-y", "-ss", "00:00:00.5", "-i",
                    source.toString(), "-frames:v", "1", "-q:v", "2", target.toString())
                    .redirectErrorStream(true).start();
            if (!process.waitFor(Math.max(1, mediaProcessTimeoutSeconds), java.util.concurrent.TimeUnit.SECONDS)
                    || process.exitValue() != 0 || !Files.isRegularFile(target)) {
                process.destroyForcibly();
                Files.deleteIfExists(target);
                return null;
            }
            hardenPermissions(target);
            String url = publicPath + "/" + SUCCESS_STORY_THUMBNAIL_FOLDER + "/" + stored;
            MediaFile entity = MediaFile.builder()
                    .fileName(SUCCESS_STORY_THUMBNAIL_FOLDER + "/" + stored)
                    .originalName("success-story-video-frame.jpg")
                    .contentType("image/jpeg")
                    .sizeBytes(Files.size(target))
                    .url(url)
                    .fileType("IMAGE")
                    .folder(SUCCESS_STORY_THUMBNAIL_FOLDER)
                    .uploadedBy(uploadedBy)
                    .build();
            repository.save(entity);
            return url;
        } catch (IOException | InterruptedException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Shared by {@link #uploadVideo} and {@link #uploadSuccessStoryVideo} — identical
     *  validation (format/size) and storage mechanics, only the destination folder differs. */
    private MediaFileResponse storeVideo(MultipartFile file, String uploadedBy, String folder) {
        // PART 2B-2/7: size (video ceiling), extension allow-list, declared MIME type, real
        // container signature and file-name safety are all enforced by the shared validator.
        UploadFileValidator.ValidatedUpload validated = uploadValidator.validate(file, UploadCategory.VIDEO);
        if (file.getSize() > videoMaxBytes) {
            throw new BadRequestException("Video file is too large. Maximum allowed size is "
                    + (videoMaxBytes / (1024 * 1024)) + "MB.");
        }

        // Never trust the original filename as a filesystem path: the stored name is fully generated.
        String stored = validated.storedName();
        try {
            Path target = root.resolve(folder).normalize();
            if (!target.startsWith(root)) {
                throw new BadRequestException("Invalid upload location");
            }
            Files.createDirectories(target);
            Path destination = target.resolve(stored).normalize();
            if (!destination.startsWith(target)) {
                throw new BadRequestException("Invalid file name");
            }
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            hardenPermissions(destination);
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Video storage failure: the file could not be saved.");
        }

        MediaFile entity = MediaFile.builder()
                .fileName(folder + "/" + stored)
                .originalName(validated.originalName())
                .contentType(validated.contentType())
                .sizeBytes(validated.sizeBytes())
                .url(publicPath + "/" + folder + "/" + stored)
                .fileType("VIDEO")
                .folder(folder)
                .uploadedBy(uploadedBy)
                .build();
        return MediaFileMapper.toResponse(repository.save(entity));
    }

    /**
     * PART 2B-2/7 — stored uploads are data, never programs: the executable bit is cleared on
     * POSIX filesystems so even a mis-configured web server cannot run an uploaded file.
     */
    private static void hardenPermissions(Path path) {
        try {
            java.nio.file.attribute.PosixFileAttributeView view =
                    Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (Windows) - nothing to do, uploads are not executable there.
        }
    }

    @Override
    @Transactional
    public void deleteVideoIfManaged(String videoUrl) {
        deleteFileIfManaged(videoUrl, VIDEO_FOLDER);
    }

    @Override
    @Transactional
    public void deleteFileIfManaged(String url, String folder) {
        if (url == null || url.isBlank() || folder == null || folder.isBlank()) {
            return;
        }
        String managedPrefix = publicPath + "/" + folder + "/";
        if (!url.startsWith(managedPrefix)) {
            // External URL, or anything outside the given VITC-managed folder, is never touched -
            // e.g. a Success Story that links to a YouTube URL instead of an uploaded file, or a
            // thumbnail/video that was never actually uploaded through this service.
            return;
        }
        // "<folder>/<generated-name>.<ext>" - the same relative form stored as MediaFile.fileName.
        String relative = url.substring(publicPath.length() + 1);
        if (repository.findByFileName(relative).isPresent()
                && (lessonRepository.existsByVideoUrl(url) || lessonRepository.existsByAudioUrl(url))) {
            // A shared reference may belong to another lesson. Never remove its physical file
            // from a replacement/lesson-delete cleanup path.
            return;
        }
        try {
            Path path = root.resolve(relative).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Best-effort only: the parent record (lesson/success story) has already been
            // updated/deleted by the time this runs, so a failed disk cleanup must never surface
            // as an error to the caller.
        }
        repository.findByFileName(relative).ifPresent(repository::delete);
    }

    @Override
    @Transactional
    public List<MediaFileResponse> uploadMany(MultipartFile[] files, String folder, String uploadedBy) {
        if (files == null || files.length == 0) {
            throw new BadRequestException("No files provided");
        }
        return java.util.Arrays.stream(files).map(f -> upload(f, folder, uploadedBy)).toList();
    }

    @Override
    public List<MediaFileResponse> getAll(String fileType, String folder) {
        List<MediaFile> items;
        if (fileType != null && !fileType.isBlank()) {
            items = repository.findByFileTypeIgnoreCaseOrderByCreatedAtDesc(fileType);
        } else if (folder != null && !folder.isBlank()) {
            items = repository.findByFolderIgnoreCaseOrderByCreatedAtDesc(folder);
        } else {
            items = repository.findAllByOrderByCreatedAtDesc();
        }
        return items.stream().map(MediaFileMapper::toResponse).toList();
    }

    @Override
    public MediaFileResponse getById(Long id) {
        return MediaFileMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        MediaFile entity = find(id);
        if (lessonRepository.existsByVideoUrl(entity.getUrl()) || lessonRepository.existsByAudioUrl(entity.getUrl())) {
            throw new BadRequestException("Media is still attached to a lesson. Remove the lesson reference first.");
        }
        try {
            Path path = root.resolve(entity.getFileName()).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // The database record is removed even if the physical file is already gone.
        }
        repository.delete(entity);
    }

    @Override
    @Transactional
    public void deleteMany(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("No ids provided");
        }
        ids.forEach(this::delete);
    }

    private MediaFile find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MediaFile", id));
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String typeOf(String ext) {
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "webp", "svg", "avif" -> "IMAGE";
            case "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv" -> "DOCUMENT";
            default -> "OTHER";
        };
    }

    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "general";
        }
        String clean = folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-_]", "");
        return clean.isBlank() ? "general" : clean;
    }
}
