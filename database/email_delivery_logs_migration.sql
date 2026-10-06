-- ============================================================================
-- VITC — Credential email delivery tracking (Part 6)
-- Additive only. No existing table, column, or row is modified or dropped.
-- Run once against an existing vitc_db. Safe to re-run (IF NOT EXISTS guard).
--
-- Records every attempt to send a student-credential / course-added email
-- after a verified payment. Never stores a password — only who was mailed,
-- which template, and the delivery outcome (PENDING / SENT / FAILED).
-- The dedupe_key unique constraint is what makes repeated payment callbacks,
-- webhook retries, and page refreshes send the credential email at most once.
-- ============================================================================

CREATE TABLE IF NOT EXISTS email_delivery_logs (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  dedupe_key       VARCHAR(120)  NOT NULL,
  email_type       VARCHAR(40)   NOT NULL,
  recipient_email  VARCHAR(160)  NOT NULL,
  student_login_id VARCHAR(40)   NULL,
  student_user_id  BIGINT        NULL,
  order_code       VARCHAR(40)   NULL,
  course_title     VARCHAR(200)  NULL,
  status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  failure_reason   VARCHAR(400)  NULL,
  sent_at          DATETIME(6)   NULL,
  failed_at        DATETIME(6)   NULL,
  retry_count      INT           NOT NULL DEFAULT 0,
  created_at       DATETIME(6)   NULL,
  updated_at       DATETIME(6)   NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_email_dedupe_key UNIQUE (dedupe_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_email_delivery_logs_status ON email_delivery_logs (status);
CREATE INDEX idx_email_delivery_logs_student_user_id ON email_delivery_logs (student_user_id);
