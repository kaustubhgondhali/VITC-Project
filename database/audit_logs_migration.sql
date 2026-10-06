-- ============================================================================
-- VITC — Centralized audit log foundation (Part 2C-1/7)
-- Additive only. No existing table, column, or row is modified or dropped.
-- Run once against an existing vitc_db. Safe to re-run (IF NOT EXISTS guard).
-- Hibernate (ddl-auto=update) will also create this table automatically on
-- first startup after this part - this file exists purely as documentation /
-- for anyone who prefers to apply schema changes manually.
--
-- Records a security-relevant or administrative action taken anywhere in the
-- backend. Never stores a password, token, API key, or other secret — only
-- who acted (when known), what they did, and where the request came from.
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  user_id          BIGINT        NULL,
  actor_identifier VARCHAR(60)   NULL,
  role             VARCHAR(20)   NULL,
  action           VARCHAR(80)   NOT NULL,
  entity_type      VARCHAR(60)   NULL,
  entity_id        VARCHAR(60)   NULL,
  description      VARCHAR(500)  NULL,
  ip_address       VARCHAR(64)   NULL,
  user_agent       VARCHAR(255)  NULL,
  created_at       DATETIME(6)   NULL,
  updated_at       DATETIME(6)   NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
