-- ============================================================
--  VITC Website - PART 2/6: Course visibility diagnostic
--  ------------------------------------------------------------
--  Run the SELECTs below FIRST and read the results yourself.
--  This file does NOT run any UPDATE automatically - there is no
--  safe, generic rule that can tell "an admin deliberately hid
--  this course" apart from "this row was defaulted to inactive
--  by a schema change" from data alone. Guessing wrong in either
--  direction is worse than doing nothing, so this stays manual.
--
--  The application already fixes the one case that CAN be told
--  apart safely: on startup, CatalogSeeder (backend/src/main/java/
--  com/vitc/config/CatalogSeeder.java) re-activates a known seed
--  course ONLY if it has never been edited since creation
--  (updated_at = created_at) - i.e. no admin has ever opened and
--  saved that course. Anything an admin has ever touched, active
--  or not, is left exactly as they left it.
-- ============================================================

USE vitc_db;

-- 1. Full picture: every course and its current visibility.
SELECT id, code, title, active, created_at, updated_at
FROM courses
ORDER BY id;

-- 2. What the public "/api/v1/courses/active" endpoint returns today.
SELECT id, code, title, active
FROM courses
WHERE active = 1;

-- 3. Candidates worth a human look: inactive courses that were never
--    edited after creation (created_at = updated_at). These are the
--    ones CatalogSeeder's startup reconciliation targets automatically
--    for its own known seed codes - this query lets you see the same
--    set (and anything outside the seed list) before/without restarting
--    the app.
SELECT id, code, title, active, created_at, updated_at
FROM courses
WHERE active = 0
  AND updated_at = created_at
ORDER BY id;

-- 4. OPTIONAL, MANUAL ONLY: if you have reviewed query 3 above and
--    confirmed a specific course should in fact be public, activate it
--    by id - one row at a time, never as a blanket UPDATE:
--
--    UPDATE courses SET active = 1 WHERE id = <id>;
