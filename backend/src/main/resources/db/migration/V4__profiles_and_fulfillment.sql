ALTER TABLE users ADD COLUMN display_name VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN fulfillment_status VARCHAR(20) NOT NULL DEFAULT 'UNFULFILLED'
 CHECK (fulfillment_status IN ('UNFULFILLED','PROCESSING','SHIPPED','DELIVERED'));
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE categories ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
-- Retain historical transactions while retiring explicitly labelled test catalog records.
UPDATE products SET active=FALSE, version=version+1 WHERE sku LIKE 'VERIFY-%';
UPDATE categories SET active=FALSE WHERE name LIKE 'Verification %';
