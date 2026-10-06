-- ============================================================================
-- VITC — Unified Java OTP Password Recovery Migration
-- Creates table password_reset_otps for Admin, Teacher, and Student portals.
-- ============================================================================

CREATE TABLE IF NOT EXISTS password_reset_otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    account_id BIGINT NOT NULL,
    account_role VARCHAR(30) NOT NULL,
    target_contact VARCHAR(150) NOT NULL,
    delivery_channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    otp_hash VARCHAR(100) NOT NULL,
    recovery_token VARCHAR(64) NOT NULL UNIQUE,
    reset_token VARCHAR(64) NULL,
    expires_at DATETIME(6) NOT NULL,
    last_sent_at DATETIME(6) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    verified_at DATETIME(6) NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_otp_recovery_token ON password_reset_otps (recovery_token);
CREATE INDEX IF NOT EXISTS idx_otp_reset_token ON password_reset_otps (reset_token);
CREATE INDEX IF NOT EXISTS idx_otp_account ON password_reset_otps (account_id, account_role);

