-- ============================================================================
-- VITC — Email Configuration upgrade for `smtp_settings`
-- Additive only: adds nullable columns. No existing table, column or row is
-- modified or dropped, and existing SMTP settings keep working unchanged
-- (NULL security_mode = derived from the port as before, NULL enabled = on).
--
-- Run once against an existing vitc_db after smtp_settings_migration.sql.
-- Safe to re-run: every column is guarded through information_schema
-- (MySQL 8 does not support ADD COLUMN IF NOT EXISTS).
-- The backend (spring.jpa.hibernate.ddl-auto=update) also adds these columns
-- automatically on startup; this script is for manually managed databases.
--
-- The credential stays in the existing `encrypted_password` column
-- (AES-256-GCM, key from RAZORPAY_ENCRYPTION_KEY - never stored here).
-- "Credential configured" is derived from that column, not duplicated.
-- ============================================================================

SET @tbl := 'smtp_settings';

-- provider: MailProviderRegistry code, e.g. GMAIL / CUSTOM
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN provider VARCHAR(40) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'provider');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- security_mode: STARTTLS | SSL_TLS
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN security_mode VARCHAR(20) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'security_mode');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- reply_to_email: optional Reply-To header
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN reply_to_email VARCHAR(200) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'reply_to_email');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sending_domain: informational (VITC does not verify DNS itself)
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN sending_domain VARCHAR(190) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'sending_domain');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- dkim_selector: informational (VITC does not DKIM-sign itself)
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN dkim_selector VARCHAR(100) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'dkim_selector');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- enabled: NULL (legacy rows) is treated as enabled
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN enabled BIT NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'enabled');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- updated_by: username of the Main Admin who last saved the configuration
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN updated_by VARCHAR(120) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'updated_by');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- auth_mode: SMTP (NULL) or GMAIL_OAUTH when a Google account is connected
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN auth_mode VARCHAR(20) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'auth_mode');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- google_email: the connected Google address
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN google_email VARCHAR(200) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'google_email');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- google_refresh_token_enc: AES-256-GCM ciphertext of the Google refresh token
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN google_refresh_token_enc VARCHAR(1024) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'google_refresh_token_enc');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- google_scope: permissions Google granted (gmail.send openid email)
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN google_scope VARCHAR(500) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'google_scope');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- google_connected_at
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN google_connected_at DATETIME NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'google_connected_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- google_connected_by: Main Admin username that connected the account
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE smtp_settings ADD COLUMN google_connected_by VARCHAR(120) NULL', 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'google_connected_by');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
