CREATE TABLE IF NOT EXISTS dc_state (
 id INT PRIMARY KEY, batch_id VARCHAR(36), mapping_version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO dc_state(id, mapping_version) SELECT 1, 0 WHERE NOT EXISTS (SELECT 1 FROM dc_state WHERE id=1);
CREATE TABLE IF NOT EXISTS dc_job (
 id VARCHAR(36) PRIMARY KEY, status VARCHAR(20) NOT NULL, started_at BIGINT NOT NULL,
 finished_at BIGINT, actor_id BIGINT NOT NULL, row_count BIGINT NOT NULL DEFAULT 0,
 issue_count BIGINT NOT NULL DEFAULT 0, mapping_version BIGINT NOT NULL DEFAULT 0,
 metric_version VARCHAR(40) NOT NULL DEFAULT 'payment-cohort-v1',
 error_message VARCHAR(300)
);
CREATE TABLE IF NOT EXISTS dc_mapping (
 entity_type VARCHAR(20) NOT NULL, source_key VARCHAR(180) NOT NULL,
 canonical_code VARCHAR(100) NOT NULL, display_name VARCHAR(150) NOT NULL,
 version BIGINT NOT NULL, updated_at BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 PRIMARY KEY(entity_type, source_key)
);
CREATE TABLE IF NOT EXISTS dc_mapping_history (
 version BIGINT PRIMARY KEY, entity_type VARCHAR(20) NOT NULL, source_key VARCHAR(180) NOT NULL,
 canonical_code VARCHAR(100) NOT NULL, display_name VARCHAR(150) NOT NULL,
 updated_at BIGINT NOT NULL, actor_id BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS dc_fact (
 batch_id VARCHAR(36) NOT NULL, kind VARCHAR(20) NOT NULL, source_id BIGINT NOT NULL,
 source_key VARCHAR(240) NOT NULL, parent_key VARCHAR(240), shop_code VARCHAR(100),
 sku_code VARCHAR(100), product_code VARCHAR(100), warehouse_code VARCHAR(100), supplier_code VARCHAR(100),
 event_time BIGINT, amount_minor BIGINT NOT NULL DEFAULT 0, quantity BIGINT NOT NULL DEFAULT 0,
 eligible INT NOT NULL DEFAULT 0, payload TEXT NOT NULL,
 PRIMARY KEY(batch_id,kind,source_id),
 INDEX dc_fact_metric(batch_id,kind,eligible,event_time),
 INDEX dc_fact_key(batch_id,kind,source_key),
 INDEX dc_fact_parent(batch_id,kind,parent_key)
);
CREATE TABLE IF NOT EXISTS dc_issue (
 id VARCHAR(36) PRIMARY KEY, batch_id VARCHAR(36) NOT NULL, kind VARCHAR(20) NOT NULL,
 source_id BIGINT NOT NULL, rule_code VARCHAR(50) NOT NULL, severity VARCHAR(12) NOT NULL,
 message VARCHAR(400) NOT NULL, INDEX dc_issue_batch(batch_id,kind,source_id)
);
