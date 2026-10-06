package com.vitc.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "courses")
public class Course extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 120)
    private String meta;

    @Column(length = 60)
    private String level;

    @Column(length = 60)
    private String category;

    @Column(name = "duration_months", length = 40)
    private String durationMonths;

    @Column(length = 20)
    private String icon;

    /**
     * PART 2/6 - VITC course-visibility fix.
     *
     * {@code nullable = false} alone only validates values Java sends; it
     * does NOT put a DEFAULT on the underlying column. With
     * {@code spring.jpa.hibernate.ddl-auto=update} (see application.properties),
     * Hibernate generates a bare {@code ALTER TABLE courses ADD COLUMN active BIT NOT NULL}
     * for any environment where this column did not already exist - and on a
     * table that already has rows, MySQL silently backfills that NOT NULL
     * column with its implicit type default (0 / false), not the value this
     * class initializes new Java objects with. That is how already-published
     * courses can end up hidden from GET /api/v1/courses/active without
     * anyone touching the Course Management screen.
     *
     * {@code @ColumnDefault("1")} makes Hibernate emit
     * {@code DEFAULT 1} on that same ALTER TABLE, so any future
     * environment that (re)creates this column keeps existing rows active
     * by default - matching {@code active BIT(1) NOT NULL DEFAULT b'1'} in
     * database/vitc_db_fresh.sql. It does not change behavior for anything
     * that already sets {@code active} explicitly (seeder, admin create/edit).
     */
    @Column(nullable = false)
    @ColumnDefault("1")
    @Builder.Default
    private Boolean active = true;

    /**
     * Owning Teacher Admin account (a {@code users} row with
     * {@code role = TEACHER}), used to scope the Teacher Dashboard to only
     * the courses assigned to the logged-in teacher. Deliberately a plain
     * nullable column - not a JPA relationship - so every existing course
     * row, query and API response keeps working unchanged for courses that
     * have not been assigned to a teacher yet. Assignment is managed
     * server-side only; nothing in this project currently lets a course be
     * created without one (Main Admin course management is unchanged by
     * this field).
     */
    @Column(name = "teacher_id")
    private Long teacherId;

}
