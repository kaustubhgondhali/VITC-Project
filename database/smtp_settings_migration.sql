-- ============================================================================
-- VITC — SMTP Settings storage (Part 4B)
-- Additive only. No existing table, column, or row is modified or dropped.
-- Run once against an existing vitc_db. Safe to re-run (IF NOT EXISTS guard).
--
-- Mirrors the existing single-row settings pattern used by
-- `payment_gateway_settings` (see PaymentGatewaySetting.java): one row keyed
-- by settings_key = 'DEFAULT', password stored AES-256-GCM encrypted
-- (never in plain text), never returned by any API.
-- ============================================================================

CREATE TABLE IF NOT EXISTS smtp_settings (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  settings_key        VARCHAR(40)  NOT NULL,
  host                VARCHAR(200) NULL,
  port                INT          NULL,
  username            VARCHAR(200) NULL,
  encrypted_password  VARCHAR(512) NULL,
  from_email          VARCHAR(200) NULL,
  from_name           VARCHAR(150) NULL,
  created_at          DATETIME     NULL,
  updated_at          DATETIME     NULL,
  CONSTRAINT uk_smtp_settings_key UNIQUE (settings_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
