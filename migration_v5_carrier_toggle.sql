-- Carrier on/off switches (v5). Plain MySQL, safe to run more than once. Take a backup first:
--   python scripts/dbtool.py backup
--   python scripts/dbtool.py run migration_v5_carrier_toggle.sql
--
-- shop_settings.ghn_enabled / ghtk_enabled: BOOLEAN NOT NULL DEFAULT TRUE (every carrier stays on until the admin
-- turns it off). The application also adds the columns on startup, so add only what is missing, then backfill and
-- tighten (a column Hibernate created first may be nullable).
SET @add_ghn = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE shop_settings ADD COLUMN ghn_enabled BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_settings' AND COLUMN_NAME = 'ghn_enabled');
PREPARE add_ghn_stmt FROM @add_ghn;
EXECUTE add_ghn_stmt;
DEALLOCATE PREPARE add_ghn_stmt;

SET @add_ghtk = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE shop_settings ADD COLUMN ghtk_enabled BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_settings' AND COLUMN_NAME = 'ghtk_enabled');
PREPARE add_ghtk_stmt FROM @add_ghtk;
EXECUTE add_ghtk_stmt;
DEALLOCATE PREPARE add_ghtk_stmt;

UPDATE shop_settings SET ghn_enabled = TRUE WHERE ghn_enabled IS NULL;
UPDATE shop_settings SET ghtk_enabled = TRUE WHERE ghtk_enabled IS NULL;
ALTER TABLE shop_settings MODIFY ghn_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE shop_settings MODIFY ghtk_enabled BOOLEAN NOT NULL DEFAULT TRUE;
