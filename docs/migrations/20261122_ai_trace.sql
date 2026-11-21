-- Idempotent AI execution trace tables. The API also creates them on startup unless ai.trace.initialize-schema=false.
CREATE TABLE IF NOT EXISTS ai_trace_run (
 id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, session_id VARCHAR(64) NOT NULL,
 input_text MEDIUMTEXT NOT NULL, status VARCHAR(24) NOT NULL, model VARCHAR(128), started_at BIGINT NOT NULL,
 finished_at BIGINT, error_message VARCHAR(500), output_text MEDIUMTEXT,
 INDEX ix_trace_user_session(user_id,session_id,started_at)
);
CREATE TABLE IF NOT EXISTS ai_trace_event (
 id VARCHAR(64) PRIMARY KEY, run_id VARCHAR(64) NOT NULL, step_no INT NOT NULL, type VARCHAR(32) NOT NULL,
 name VARCHAR(128), status VARCHAR(24) NOT NULL, input_text MEDIUMTEXT, output_text MEDIUMTEXT,
 started_at BIGINT NOT NULL, finished_at BIGINT, INDEX ix_trace_run(run_id,step_no)
);
CREATE TABLE IF NOT EXISTS ai_trace_link (
 id VARCHAR(64) PRIMARY KEY, run_id VARCHAR(64) NOT NULL, kind VARCHAR(32) NOT NULL,
 business_id VARCHAR(64) NOT NULL, label VARCHAR(200) NOT NULL, created_at BIGINT NOT NULL,
 UNIQUE(run_id, kind, business_id), INDEX ix_trace_link_run(run_id)
);
