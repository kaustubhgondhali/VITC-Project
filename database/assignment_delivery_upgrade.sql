-- ============================================================================
-- VITC — Purchased assignment delivery
-- Additive only. No existing row is modified or dropped.
--
-- WHO NEEDS THIS: environments running with spring.jpa.hibernate.ddl-auto=validate
-- (the prod profile), which refuse to start until these columns exist. With the
-- default ddl-auto=update the backend adds the columns itself, and it also widens
-- email_delivery_logs.email_type on startup (see EmailDeliveryLogSchemaGuard).
--
-- Run ONCE against an existing vitc_db. MySQL has no "ADD COLUMN IF NOT EXISTS",
-- so re-running the ALTER below fails harmlessly with "Duplicate column name".
-- ============================================================================

-- --- assignment_orders: the delivered project package + private download link ---
ALTER TABLE assignment_orders
  ADD COLUMN delivery_file_path    VARCHAR(255)  NULL,
  ADD COLUMN delivery_file_name    VARCHAR(160)  NULL,
  ADD COLUMN delivery_content_type VARCHAR(120)  NULL,
  ADD COLUMN delivery_size_bytes   BIGINT        NULL,
  ADD COLUMN delivery_note         VARCHAR(1000) NULL,
  ADD COLUMN delivered_at          DATETIME(6)   NULL,
  ADD COLUMN download_token_hash   VARCHAR(64)   NULL,  -- SHA-256 of the emailed token, never the token
  ADD COLUMN download_expires_at   DATETIME(6)   NULL,
  ADD COLUMN download_count        INT           NULL,
  ADD COLUMN last_downloaded_at    DATETIME(6)   NULL;

CREATE INDEX idx_assignment_orders_download_token ON assignment_orders (download_token_hash);

-- --- assignments: ready-made project file, sent to every buyer automatically ---
ALTER TABLE assignments
  ADD COLUMN project_file_path    VARCHAR(255) NULL,
  ADD COLUMN project_file_name    VARCHAR(160) NULL,
  ADD COLUMN project_content_type VARCHAR(120) NULL,
  ADD COLUMN project_size_bytes   BIGINT       NULL,
  ADD COLUMN project_uploaded_at  DATETIME(6)  NULL;

-- --- email_delivery_logs: allow the two new assignment email types -------------
-- email_delivery_logs_migration.sql already creates this column as VARCHAR(40);
-- this only matters where Hibernate created it as a native ENUM instead.
-- Safe to re-run.
ALTER TABLE email_delivery_logs
  MODIFY COLUMN email_type VARCHAR(40) NOT NULL;
