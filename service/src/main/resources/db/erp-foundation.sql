CREATE TABLE IF NOT EXISTS ef_stock_balance (
 inventory_id BIGINT PRIMARY KEY, quantity BIGINT NOT NULL, locked_quantity BIGINT NOT NULL,
 stock_value DECIMAL(24,6), version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS ef_stock_ledger (
 id VARCHAR(100) PRIMARY KEY, inventory_id BIGINT NOT NULL, source VARCHAR(100) NOT NULL,
 quantity_delta BIGINT NOT NULL, locked_delta BIGINT NOT NULL, value_delta DECIMAL(24,6),
 quantity_after BIGINT NOT NULL, locked_after BIGINT NOT NULL, value_after DECIMAL(24,6),
 version BIGINT NOT NULL, actor VARCHAR(100) NOT NULL, reason VARCHAR(500) NOT NULL,
 created_at BIGINT NOT NULL, reversal_of VARCHAR(100), UNIQUE(inventory_id,version), UNIQUE(reversal_of)
);
CREATE TABLE IF NOT EXISTS ef_scope (
 user_id BIGINT NOT NULL, merchant_id BIGINT NOT NULL, shop_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,merchant_id,shop_id,warehouse_id)
);
CREATE TABLE IF NOT EXISTS ef_policy (
 kind VARCHAR(30) PRIMARY KEY, threshold_amount DECIMAL(18,2) NOT NULL,
 first_permission VARCHAR(100) NOT NULL, second_permission VARCHAR(100) NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS ef_document (
 id VARCHAR(36) PRIMARY KEY, kind VARCHAR(30) NOT NULL, inventory_id BIGINT NOT NULL,
 merchant_id BIGINT NOT NULL, shop_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
 state VARCHAR(30) NOT NULL, quantity BIGINT NOT NULL, unit_cost DECIMAL(18,6), extra_cost DECIMAL(18,2) NOT NULL,
 supplier_id BIGINT, target_inventory_id BIGINT, reason VARCHAR(500) NOT NULL, attributes_json TEXT NOT NULL,
 created_by BIGINT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, version BIGINT NOT NULL,
 first_permission VARCHAR(100), second_permission VARCHAR(100), first_approver BIGINT,
 purchase_id BIGINT, policy_version BIGINT, received_quantity BIGINT NOT NULL DEFAULT 0,
 request_key VARCHAR(100) NOT NULL, UNIQUE(created_by,request_key)
);
CREATE TABLE IF NOT EXISTS ef_audit (
 id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(100) NOT NULL, action VARCHAR(40) NOT NULL,
 actor_id BIGINT NOT NULL, created_at BIGINT NOT NULL, detail TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS ef_reorder (
 inventory_id BIGINT PRIMARY KEY, reorder_point BIGINT NOT NULL, target_quantity BIGINT NOT NULL,
 min_order BIGINT NOT NULL, pack_size BIGINT NOT NULL, lead_days INT NOT NULL,
 daily_demand DECIMAL(18,4) NOT NULL, supplier_id BIGINT NOT NULL, unit_cost DECIMAL(18,6) NOT NULL,
 enabled INT NOT NULL, version BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS ef_field (
 kind VARCHAR(30) NOT NULL, field_key VARCHAR(50) NOT NULL, label VARCHAR(100) NOT NULL,
 field_type VARCHAR(20) NOT NULL, required_flag INT NOT NULL, options_json TEXT NOT NULL,
 version BIGINT NOT NULL, PRIMARY KEY(kind,field_key)
);
CREATE TABLE IF NOT EXISTS ef_custom_value (
 kind VARCHAR(30) NOT NULL, record_id BIGINT NOT NULL, attributes_json TEXT NOT NULL,
 version BIGINT NOT NULL, PRIMARY KEY(kind,record_id)
);
