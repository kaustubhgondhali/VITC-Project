package com.vitc.entity;

import com.vitc.entity.enums.SuccessStoryCategory;
import com.vitc.entity.enums.SuccessStoryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PART 1/6 — SUCCESS STORIES FOUNDATION, extended in PART 2/6 — SUCCESS
 * STORIES DATABASE &amp; BACKEND.
 *
 * Video success stories shown on the public "Success Stories" page
 * (replaces the old plain-text {@link Testimonial} tab). Kept as its own
 * table so the existing testimonials data/feature is never touched.
 *
 * <p>{@code category} is a free-form lowercase string ("student" | "teacher" |
 * "parent") — same convention as {@link GalleryItem#getCategory()} elsewhere
 * in this codebase, so new categories can be added later without a
 * migration. It is validated against {@link SuccessStoryCategory} at the
 * request/service boundary before it ever reaches this entity.</p>
 *
 * <p>PART 2/6 additions:</p>
 * <ul>
 *   <li>{@code status} — {@code DRAFT}/{@code PUBLISHED} lifecycle, replacing the
 *       PART 1/6 {@code approved} boolean (public visitors only ever see
 *       {@code PUBLISHED} rows; a Teacher Admin publishes/unpublishes explicitly).</li>
 *   <li>{@code courseId} — optional link to an existing {@link Course}. Deliberately a
 *       plain nullable column, not a JPA relationship — same pattern as
 *       {@link Course#getTeacherId()} — so no existing course row or query is touched.</li>
 *   <li>{@code createdBy} / {@code createdByRole} — audit trail of which authenticated
 *       Teacher Admin account created the story, resolved server-side only
 *       (never trusted from the request body).</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "success_stories")
public class SuccessStory extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    /** "student" | "teacher" | "parent" */
    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "course_program", length = 150)
    private String courseProgram;

    /** Optional link to {@code courses.id}. Not a JPA relationship — see class javadoc. */
    @Column(name = "course_id")
    private Long courseId;

    @Column(length = 150)
    private String designation;

    @Column(length = 500)
    private String description;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SuccessStoryStatus status = SuccessStoryStatus.DRAFT;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /** Login identifier (e.g. teacher username) of whoever created this row. Audit only. */
    @Column(name = "created_by", length = 120)
    private String createdBy;

    /** {@link com.vitc.security.Role} name of the creator ("TEACHER", "MAIN_ADMIN"). Audit only. */
    @Column(name = "created_by_role", length = 20)
    private String createdByRole;
}
