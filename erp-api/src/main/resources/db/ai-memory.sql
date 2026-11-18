CREATE TABLE IF NOT EXISTS ai_memory_lock (
 user_id BIGINT PRIMARY KEY, lease_token VARCHAR(64), lease_until BIGINT NOT NULL DEFAULT 0,
 forget_cursor BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS ai_memory_session (
 user_id BIGINT NOT NULL, session_id VARCHAR(64) NOT NULL,
 summary MEDIUMTEXT, anchors MEDIUMTEXT, summary_cursor BIGINT NOT NULL DEFAULT 0,
 extract_cursor BIGINT NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL,
 pending INT NOT NULL DEFAULT 0, archived INT NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id, session_id)
);
CREATE TABLE IF NOT EXISTS ai_memory_item (
 id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL,
 kind VARCHAR(24) NOT NULL, fact_key VARCHAR(255) NOT NULL, key_hash VARCHAR(64) NOT NULL,
 content MEDIUMTEXT NOT NULL, source_session VARCHAR(64) NOT NULL, source_id BIGINT NOT NULL,
 importance INT NOT NULL, explicit_memory INT NOT NULL DEFAULT 0,
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, last_access BIGINT NOT NULL,
 access_count BIGINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 1,
 status VARCHAR(24) NOT NULL DEFAULT 'active', embedding MEDIUMTEXT, embedding_model VARCHAR(255),
 UNIQUE KEY uq_memory_fact(user_id, kind, key_hash),
 INDEX ix_memory_user(user_id, status, updated_at)
);
CREATE TABLE IF NOT EXISTS ai_memory_revision (
 id VARCHAR(64) PRIMARY KEY, memory_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
 version BIGINT NOT NULL, content MEDIUMTEXT NOT NULL, source_session VARCHAR(64) NOT NULL,
 source_id BIGINT NOT NULL, replaced_at BIGINT NOT NULL, reason VARCHAR(64) NOT NULL,
 INDEX ix_memory_revision(user_id, memory_id)
);
CREATE TABLE IF NOT EXISTS ai_memory_work (
 id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, session_id VARCHAR(64) NOT NULL,
 run_id VARCHAR(64) NOT NULL, step_no INT NOT NULL, tool_name VARCHAR(128) NOT NULL,
 arguments MEDIUMTEXT, result MEDIUMTEXT, status VARCHAR(24) NOT NULL,
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
 INDEX ix_memory_work(user_id, session_id, created_at)
);
