-- ============================================================================
-- VITC — Student Portal upgrade (Part 1)
-- Additive only. No existing table, column, or row is modified or dropped.
-- Run once against an existing vitc_db. Safe to re-run on MySQL 8 (the
-- IF NOT EXISTS guards are honoured); on older MySQL, drop the guards.
-- ============================================================================

-- --- users: student portal login fields -------------------------------------
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS student_login_id           VARCHAR(30)  NULL,
  ADD COLUMN IF NOT EXISTS must_change_password       TINYINT(1)   NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS student_session_token      VARCHAR(100) NULL,
  ADD COLUMN IF NOT EXISTS student_session_expires_at DATETIME     NULL,
  ADD COLUMN IF NOT EXISTS student_last_login_at      DATETIME     NULL;

-- Student IDs are unique across the institute (VITCSTU10001, VITCSTU10002, ...)
CREATE UNIQUE INDEX uk_users_student_login_id ON users (student_login_id);
CREATE INDEX idx_users_student_session_token  ON users (student_session_token);

-- --- enrollments: link an enrolment to the student account ------------------
ALTER TABLE enrollments
  ADD COLUMN IF NOT EXISTS user_id BIGINT NULL;

ALTER TABLE enrollments
  ADD CONSTRAINT fk_enrollments_user
  FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_enrollments_user_id ON enrollments (user_id);
