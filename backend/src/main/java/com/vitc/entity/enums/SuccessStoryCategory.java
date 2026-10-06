package com.vitc.entity.enums;

import com.vitc.exception.BadRequestException;

/**
 * PART 2/6 — SUCCESS STORIES DATABASE & BACKEND.
 *
 * Strongly-typed set of allowed {@link com.vitc.entity.SuccessStory} categories.
 * The {@code success_stories} table keeps storing category as a lowercase string
 * (same convention as {@code GalleryItem#getCategory()} / {@code Course#getCategory()}
 * elsewhere in this codebase, and required so the PART 1/6 public page — which
 * requests {@code /success-stories/category/student|teacher|parent} — keeps
 * working unchanged). This enum is only used at the request/service boundary to
 * validate and normalize the value before it is persisted, so an invalid category
 * can never reach the database.
 */
public enum SuccessStoryCategory {
    STUDENT,
    TEACHER,
    PARENT;

    /** Lowercase form persisted in {@code success_stories.category} and used in public URLs. */
    public String toDbValue() {
        return name().toLowerCase();
    }

    /**
     * Parses a category from a request body or path variable, case-insensitively.
     *
     * @throws BadRequestException if the value is not one of student/teacher/parent
     */
    public static SuccessStoryCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Category is required");
        }
        try {
            return SuccessStoryCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Category must be student, teacher or parent");
        }
    }
}
