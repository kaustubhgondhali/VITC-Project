package com.vitc.entity.enums;

/**
 * PART 2/6 — SUCCESS STORIES DATABASE & BACKEND.
 *
 * Publication lifecycle of a {@link com.vitc.entity.SuccessStory}. Replaces the
 * plain {@code approved} boolean shipped in PART 1/6: a story is created as
 * {@code DRAFT} (not visible on the public page) and only becomes visible to
 * public visitors once a Teacher Admin explicitly {@code PUBLISHED} it, and can
 * be moved back to {@code DRAFT} (unpublished) at any time without deleting it.
 */
public enum SuccessStoryStatus {
    DRAFT,
    PUBLISHED
}
