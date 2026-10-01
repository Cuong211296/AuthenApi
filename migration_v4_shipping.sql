-- GHN shipping (v4). Plain MySQL, safe to run more than once. Take a backup first:
--   python scripts/dbtool.py backup
--   python scripts/dbtool.py run migration_v4_shipping.sql
--
-- 1. Hibernate 6 created orders.shipping_source as a native enum('GHTK','TABLE'); ddl-auto update never widens it,
--    so inserting 'GHN' would fail. Make it a VARCHAR(10) (existing values are kept as text).
ALTER TABLE orders MODIFY shipping_source VARCHAR(10) NULL;

-- 2. orders.district (Quan/Huyen). The application also adds it on startup, so only add it when it is missing.
SET @add_district = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE orders ADD COLUMN district VARCHAR(100) NULL', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'district');
PREPARE add_district_stmt FROM @add_district;
EXECUTE add_district_stmt;
DEALLOCATE PREPARE add_district_stmt;
