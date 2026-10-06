package com.vitc.security.upload;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * PART 2B-2/7 — FILE UPLOAD SECURITY.
 *
 * <p>Category-specific upload limits and switches, all externally tunable via
 * {@code app.upload.security.*} in application.properties — nothing hard-coded in Java.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.upload.security")
@Getter
@Setter
public class UploadSecurityProperties {

    /** Master switch. Left on in every environment; only here for emergency diagnostics. */
    private boolean enabled = true;

    /** Small on purpose: an avatar never needs more. */
    private DataSize profileImageMaxSize = DataSize.ofMegabytes(2);

    private DataSize galleryImageMaxSize = DataSize.ofMegabytes(5);

    private DataSize courseImageMaxSize = DataSize.ofMegabytes(5);

    private DataSize documentMaxSize = DataSize.ofMegabytes(15);

    /** Large but controlled — bound from the central media limit in application.properties. */
    private DataSize videoMaxSize = DataSize.ofMegabytes(500);

    private DataSize audioMaxSize = DataSize.ofMegabytes(100);

    /** A purchased assignment's project package - a zipped codebase is far bigger than a brochure. */
    private DataSize assignmentDeliveryMaxSize = DataSize.ofMegabytes(200);

    /** Job applicants' resumes - a CV never needs more. */
    private DataSize resumeMaxSize = DataSize.ofMegabytes(5);

    /** Verify real file content (magic bytes) and not just extension + declared MIME type. */
    private boolean contentInspectionEnabled = true;

    /** Allow SVG uploads. Content is scanned for scripts before it is ever stored. */
    private boolean allowSvg = true;

    public long maxBytesFor(UploadCategory category) {
        return switch (category) {
            case PROFILE_IMAGE -> profileImageMaxSize.toBytes();
            case GALLERY_IMAGE -> galleryImageMaxSize.toBytes();
            case COURSE_IMAGE -> courseImageMaxSize.toBytes();
            case DOCUMENT -> documentMaxSize.toBytes();
            case VIDEO -> videoMaxSize.toBytes();
            case AUDIO -> audioMaxSize.toBytes();
            case ASSIGNMENT_DELIVERY -> assignmentDeliveryMaxSize.toBytes();
            case RESUME -> resumeMaxSize.toBytes();
        };
    }
}
