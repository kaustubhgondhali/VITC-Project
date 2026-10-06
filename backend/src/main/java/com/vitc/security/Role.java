package com.vitc.security;

/**
 * PART 11B-1 - the single, unified set of application roles used for backend
 * authorization decisions, independent of how each role happens to be
 * authenticated today.
 *
 * <p>The existing authentication systems are untouched: {@code Admin} rows
 * (with their own {@link com.vitc.entity.enums.AdminRole}) authenticate the
 * Main Admin panel, and {@code User} rows with
 * {@link com.vitc.entity.enums.UserRole#TEACHER} /
 * {@link com.vitc.entity.enums.UserRole#STUDENT} authenticate the Teacher and
 * Student portals. This enum is the authorization-side vocabulary every
 * protected controller is checked against, regardless of which table the
 * caller's account lives in.</p>
 */
public enum Role {
    MAIN_ADMIN,
    TEACHER,
    STUDENT
}
