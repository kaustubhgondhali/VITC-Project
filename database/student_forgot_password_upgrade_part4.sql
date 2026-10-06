-- ============================================================================
-- VITC — Student "Forgot Password" upgrade (Part 4)
-- Additive only. No existing table, column, or row is modified or dropped.
-- Run once against an existing vitc_db (after student_portal_upgrade.sql and
-- student_profile_upgrade_part6.sql). Safe to re-run on MySQL 8 (the
-- IF NOT EXISTS guard is honoured); on older MySQL, drop the guard and run
-- manually if the columns don't exist yet.
--
-- These same two columns already exist on the `admins` table (see
-- vitc_db_fresh.sql: reset_token / reset_token_expires_at) and power the
-- Main Admin "forgot password" flow. This script adds the equivalent pair to
-- `users` so the Student portal can reuse the exact same pattern - one
-- single-use, time-limited, role-scoped code per account.
-- ============================================================================

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS reset_token             VARCHAR(100) NULL,
  ADD COLUMN IF NOT EXISTS reset_token_expires_at   DATETIME(6)  NULL;

CREATE INDEX idx_users_reset_token ON users (reset_token);
