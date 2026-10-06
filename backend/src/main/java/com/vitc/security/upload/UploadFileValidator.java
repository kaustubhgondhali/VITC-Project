package com.vitc.security.upload;

import com.vitc.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * PART 2B-2/7 — FILE UPLOAD SECURITY.
 *
 * <p>Single choke point every VITC upload passes through, whatever the endpoint:
 * generic {@code /api/v1/files}, bulk upload, lesson video, success-story video.</p>
 *
 * <p>Order of checks: presence → file-name safety (traversal / null byte) → dangerous type
 * deny-list → per-category extension allow-list → declared MIME allow-list → category size
 * ceiling → real content inspection. Only then is a fully generated, collision-free storage
 * name returned; the original name is never used to build a path.</p>
 */
@Component
@RequiredArgsConstructor
public class UploadFileValidator {

    private static final Logger log = LoggerFactory.getLogger(UploadFileValidator.class);

    private final UploadSecurityProperties properties;

    /** Result of a successful validation. {@code storedName} is safe to place on disk as-is. */
    public record ValidatedUpload(UploadCategory category, String extension, String originalName,
                                  String storedName, String contentType, long sizeBytes) {
    }

    public UploadSecurityProperties properties() {
        return properties;
    }

    public ValidatedUpload validate(MultipartFile file, UploadCategory category) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(category.kind() == UploadCategory.Kind.VIDEO ? "Please select a video file."
                    : category.kind() == UploadCategory.Kind.AUDIO ? "Please select an audio file."
                    : "Please select a file to upload.");
        }

        String rawName = file.getOriginalFilename();
        String original = safeOriginalName(rawName, category);
        String ext = extensionOf(original);

        if (!properties.isEnabled()) {
            // Emergency switch only: still never trust the incoming name for the stored path.
            return new ValidatedUpload(category, ext, original, generatedName(ext),
                    file.getContentType(), file.getSize());
        }

        if (ext.isBlank()) {
            throw reject(category, "The file must have a valid extension.");
        }
        if (DangerousFileTypes.isBlockedExtension(ext) || DangerousFileTypes.containsBlockedSegment(original)) {
            throw reject(category, "This file type is not allowed for security reasons.");
        }
        if (!category.allowedExtensions().contains(ext)
                || ("svg".equals(ext) && !properties.isAllowSvg())) {
            throw reject(category, "Unsupported " + category.label() + " format. Allowed: "
                    + category.allowedHint() + ".");
        }

        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        int semi = declared.indexOf(';');
        if (semi > -1) {
            declared = declared.substring(0, semi).trim();
        }
        boolean media = category.kind() == UploadCategory.Kind.VIDEO || category.kind() == UploadCategory.Kind.AUDIO;
        if (media) {
            // PART 2/10 — VIDEO UPLOAD: the browser-declared type is advisory for videos. Browsers
            // send nothing / application/octet-stream for MKV, FLV, OGV and 3GP, so a valid video
            // must never be rejected on the declared type alone. The real container signature
            // below is the authoritative check and is mandatory for every video.
            boolean declaredOkay = category.kind() == UploadCategory.Kind.VIDEO
                    ? VideoFormats.isAcceptableDeclaredType(declared)
                    : AudioFormats.isAcceptableDeclaredType(declared);
            if (!declaredOkay) {
                throw reject(category, "Unsupported " + category.label() + " format. Allowed: "
                        + category.allowedHint() + ".");
            }
            if (declared.isBlank()) {
                declared = category.kind() == UploadCategory.Kind.VIDEO
                        ? VideoFormats.contentTypeFor(ext) : AudioFormats.contentTypeFor(ext);
            }
        } else if (declared.isBlank() || !category.allowedContentTypes().contains(declared)) {
            throw reject(category, "Unsupported " + category.label() + " format. Allowed: "
                    + category.allowedHint() + ".");
        }

        long max = properties.maxBytesFor(category);
        if (file.getSize() > max) {
            throw reject(category, "This " + category.label() + " is too large. Maximum allowed size is "
                    + humanSize(max) + ".");
        }

        // Videos are ALWAYS content-inspected: the extension and the declared MIME type are both
        // attacker-controlled, so the container signature is the only trustworthy signal.
        if (properties.isContentInspectionEnabled() || media) {
            byte[] head = head(file, category);
            if (category.kind() == UploadCategory.Kind.VIDEO && !VideoFormats.matchesSignature(ext, head)) {
                log.warn("Rejected video upload: bytes are not a valid '{}' container", ext);
                throw reject(category, "This file is not a valid " + ext.toUpperCase(Locale.ROOT)
                        + " video. Please upload a real video file.");
            }
            if (media && !FileContentInspector.matchesExtension(ext, head)) {
                log.warn("Rejected upload: content does not match declared '{}' type (category={})", ext, category);
                throw reject(category, "The file content does not match its type. Please upload a valid "
                        + category.label() + ".");
            }
            if (isTextLike(ext) && FileContentInspector.containsScript(head)) {
                throw reject(category, "This file contains active content and cannot be uploaded.");
            }
        }

        return new ValidatedUpload(category, ext, original, generatedName(ext), declared, file.getSize());
    }

    /* ------------------------------------------------------------------ helpers */

    /**
     * Strips every path component, null bytes and control characters from the browser-supplied
     * name. The result is stored for display only ({@code MediaFile.originalName}) and is never
     * used to build a filesystem path.
     */
    public static String safeOriginalName(String rawName, UploadCategory category) {
        String fallback = category != null && category.kind() == UploadCategory.Kind.VIDEO ? "video" : "file";
        if (rawName == null || rawName.isBlank()) {
            return fallback;
        }
        String cleaned = rawName
                .replace("\u0000", "")          // null-byte truncation attacks
                .replace('\\', '/')             // Windows-style traversal
                .replaceAll("[\\p{Cntrl}]", "");
        // Keep only the final path segment, then re-strip any residual separators/traversal.
        int slash = cleaned.lastIndexOf('/');
        if (slash > -1) {
            cleaned = cleaned.substring(slash + 1);
        }
        // No Paths.get() here on purpose: some names (unusual code points, reserved Windows
        // characters) make it throw InvalidPathException, which would surface as a 500 instead
        // of a clean upload. Plain string cleaning is enough because the stored filename is
        // fully server-generated anyway.
        cleaned = cleaned.replace("..", "").replace(":", "").trim();
        if (cleaned.isBlank() || ".".equals(cleaned)) {
            return fallback;
        }
        return cleaned.length() > 150 ? cleaned.substring(cleaned.length() - 150) : cleaned;
    }

    /** Extension of the LAST dot only, lower-cased, letters/digits only. */
    public static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Fully server-generated storage name — no user input whatsoever. */
    public static String generatedName(String extension) {
        String ext = extension == null ? "" : extension.replaceAll("[^a-z0-9]", "");
        String base = java.time.LocalDate.now() + "-" + UUID.randomUUID().toString().replace("-", "");
        return ext.isBlank() ? base : base + "." + ext;
    }

    private static boolean isTextLike(String ext) {
        return "svg".equals(ext) || "txt".equals(ext) || "csv".equals(ext);
    }

    private byte[] head(MultipartFile file, UploadCategory category) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(FileContentInspector.SNIFF_BYTES);
        } catch (IOException e) {
            throw reject(category, "The file could not be read. Please try again.");
        }
    }

    private static BadRequestException reject(UploadCategory category, String message) {
        return new BadRequestException(message);
    }

    private static String humanSize(long bytes) {
        long mb = bytes / (1024 * 1024);
        return mb >= 1 ? mb + "MB" : Math.max(1, bytes / 1024) + "KB";
    }
}
