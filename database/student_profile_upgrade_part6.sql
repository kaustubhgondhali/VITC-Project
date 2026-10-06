-- ============================================================================
-- VITC — Student Profile & Account upgrade (Part 6)
-- Additive only. No existing table, column, or row is modified or dropped.
-- Run once against an existing vitc_db (after student_portal_upgrade.sql).
-- Safe to re-run on MySQL 8 (the IF NOT EXISTS guard is honoured); on older
-- MySQL, drop the guard and run manually if the column doesn't exist yet.
-- ============================================================================

-- --- users: student profile picture -----------------------------------------
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(255) NULL;
