package com.vitc.security.upload;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PART 2B-2/7 — FILE UPLOAD SECURITY.
 *
 * <p>Every upload in VITC belongs to exactly one category. A category carries its own
 * size ceiling, its own extension allow-list and its own MIME allow-list, so an avatar can
 * never be as large as a lesson video and a "document" upload can never smuggle in a video
 * or a script. Deliberately NOT one global limit / one global allow-list.</p>
 */
public enum UploadCategory {

    /** Student / teacher / admin avatars — small by design. */
    PROFILE_IMAGE("profile picture", Kind.IMAGE),

    /** Public gallery images. */
    GALLERY_IMAGE("gallery image", Kind.IMAGE),

    /** Course / blog / success-story cover images and thumbnails. */
    COURSE_IMAGE("course image", Kind.IMAGE),

    /** Brochures, notes, invoices, spreadsheets. */
    DOCUMENT("document", Kind.DOCUMENT),

    /** Lesson videos (protected) and Success Story videos (public). */
    VIDEO("video", Kind.VIDEO),

    /** Lesson audio uploaded through the teacher course-content flow. */
    AUDIO("audio", Kind.AUDIO),

    /**
     * Project package a Main Admin delivers for a purchased assignment (source zip, report PDF).
     * Same formats as {@link #DOCUMENT} but its own, larger size ceiling. Never reachable from the
     * public generic upload endpoint - {@link #resolveForGenericUpload} does not return it.
     */
    ASSIGNMENT_DELIVERY("project file", Kind.DOCUMENT),

    /**
     * A job applicant's resume (Job Portal -> Apply). Narrower than {@link #DOCUMENT}: PDF or Word
     * only, with its own small size ceiling. Stored privately, never via the public upload endpoint.
     */
    RESUME("resume", Kind.DOCUMENT);

    public enum Kind { IMAGE, DOCUMENT, VIDEO, AUDIO }

    private final String label;
    private final Kind kind;

    UploadCategory(String label, Kind kind) {
        this.label = label;
        this.kind = kind;
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    /** Extensions accepted for this category. Nothing outside this list is ever stored. */
    public Set<String> allowedExtensions() {
        if (this == RESUME) {
            return Set.of("pdf", "doc", "docx");
        }
        return switch (kind) {
            case IMAGE -> Set.of("jpg", "jpeg", "png", "webp", "gif", "avif", "svg");
            case DOCUMENT -> Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip");
            // PART 2/10 — VIDEO UPLOAD: all supported containers live in VideoFormats.
            case VIDEO -> VideoFormats.allowedExtensions();
            case AUDIO -> AudioFormats.allowedExtensions();
        };
    }

    /** Content types accepted for this category (the browser-declared type is cross-checked). */
    public Set<String> allowedContentTypes() {
        if (this == RESUME) {
            return Set.of("application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/octet-stream");
        }
        return switch (kind) {
            case IMAGE -> Set.of("image/jpeg", "image/pjpeg", "image/png", "image/webp", "image/gif",
                    "image/avif", "image/svg+xml");
            case DOCUMENT -> Set.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain", "text/csv", "application/csv",
                    "application/zip", "application/x-zip-compressed", "application/octet-stream");
            case VIDEO -> VideoFormats.allowedContentTypes();
            case AUDIO -> AudioFormats.allowedContentTypes();
        };
    }

    /** User-facing hint used in rejection messages — never leaks server internals. */
    public String allowedHint() {
        if (this == RESUME) {
            return "PDF or Word (DOC, DOCX)";
        }
        return switch (kind) {
            case IMAGE -> "JPG, PNG, WebP, GIF, AVIF or SVG";
            case DOCUMENT -> "PDF, Word, Excel, PowerPoint, TXT, CSV or ZIP";
            case VIDEO -> VideoFormats.allowedHint();
            case AUDIO -> AudioFormats.allowedHint();
        };
    }

    /** MediaFile.fileType value kept identical to the pre-hardening behaviour. */
    public String fileTypeLabel() {
        return switch (kind) {
            case IMAGE -> "IMAGE";
            case DOCUMENT -> "DOCUMENT";
            case VIDEO -> "VIDEO";
            case AUDIO -> "AUDIO";
        };
    }

    /**
     * Picks the category for the generic {@code POST /api/v1/files} endpoint from the target
     * folder plus the file extension. Images land in the tightest matching image category so a
     * profile picture is not silently allowed a gallery-sized budget.
     */
    public static UploadCategory resolveForGenericUpload(String folder, String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String f = folder == null ? "" : folder.toLowerCase(Locale.ROOT);

        if (VIDEO.allowedExtensions().contains(ext)) {
            // Videos have their own dedicated, authenticated endpoints.
            return VIDEO;
        }
        if (AUDIO.allowedExtensions().contains(ext)) return AUDIO;
        if (DOCUMENT.allowedExtensions().contains(ext)) {
            return DOCUMENT;
        }
        for (String marker : List.of("profile", "avatar", "student", "teacher", "user")) {
            if (f.contains(marker)) {
                return PROFILE_IMAGE;
            }
        }
        for (String marker : List.of("course", "blog", "success", "review", "testimonial", "thumb")) {
            if (f.contains(marker)) {
                return COURSE_IMAGE;
            }
        }
        return GALLERY_IMAGE;
    }
}
