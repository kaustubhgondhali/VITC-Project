-- ============================================================================
-- VITC — Success Stories DATABASE & BACKEND upgrade (Part 2/6)
-- Additive only. No existing table, column, or row is dropped or destroyed.
-- Run ONCE against an existing vitc_db that already has the PART 1/6
-- `success_stories` table (name, category, course_program, designation,
-- description, video_url, thumbnail_url, approved, display_order).
--
-- If your database was created fresh from the updated vitc_db_fresh.sql
-- (which already includes the PART 2/6 shape), do NOT run this file.
--
-- Safe to re-run on MySQL 8 (the IF NOT EXISTS guard is honoured); on older
-- MySQL, drop the guards and run manually, checking each column first.
-- ============================================================================

-- --- success_stories: DRAFT/PUBLISHED lifecycle, replacing `approved` -------
ALTER TABLE success_stories
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

-- Backfill from the PART 1/6 `approved` boolean so nothing already live goes
-- missing from the public page after upgrading.
UPDATE success_stories SET status = 'PUBLISHED' WHERE approved = TRUE;
UPDATE success_stories SET status = 'DRAFT'     WHERE approved = FALSE;

-- --- success_stories: optional link to an existing course -------------------
-- Plain nullable column, not a foreign key — same convention already used by
-- courses.teacher_id elsewhere in this schema — so no existing row is affected.
ALTER TABLE success_stories
  ADD COLUMN IF NOT EXISTS course_id BIGINT NULL;

-- --- success_stories: audit trail of who created each story -----------------
ALTER TABLE success_stories
  ADD COLUMN IF NOT EXISTS created_by VARCHAR(120) NULL;

ALTER TABLE success_stories
  ADD COLUMN IF NOT EXISTS created_by_role VARCHAR(20) NULL;

-- NOTE: the old `approved` column is intentionally left in place (not dropped)
-- so this migration is non-destructive and reversible. The application no
-- longer reads or writes it as of Part 2/6 — `status` is now the single
-- source of truth for publication state.
