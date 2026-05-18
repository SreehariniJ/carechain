CREATE TABLE audit_chain_state (
    chain_name VARCHAR(50) PRIMARY KEY,
    latest_hash VARCHAR(64),
    entry_count BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO audit_chain_state (chain_name, latest_hash, entry_count, updated_at)
VALUES ('main', NULL, 0, CURRENT_TIMESTAMP);

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    actor_email VARCHAR(120) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(120),
    outcome VARCHAR(20) NOT NULL,
    request_method VARCHAR(10),
    request_path VARCHAR(255),
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    details_json TEXT NOT NULL,
    previous_hash VARCHAR(64),
    entry_hash VARCHAR(64) NOT NULL,
    CONSTRAINT uk_audit_log_entry_hash UNIQUE (entry_hash),
    CONSTRAINT chk_audit_log_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX idx_audit_log_action ON audit_log (action, occurred_at);
