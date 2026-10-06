package com.vitc.entity.enums;

/** Types of transactional emails VITC sends to students. */
public enum EmailType {
    /** First-time student account credentials (student id + temporary password). */
    STUDENT_CREDENTIALS,
    /** A new course was added to an already existing student account. */
    COURSE_ADDED,
    /** An assignment purchase was paid: order received, project is being prepared. */
    ASSIGNMENT_ORDER_CONFIRMED,
    /** The purchased assignment's project files are ready - carries the private download link. */
    ASSIGNMENT_DELIVERED
}
