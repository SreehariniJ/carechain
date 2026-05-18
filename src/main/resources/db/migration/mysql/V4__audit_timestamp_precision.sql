ALTER TABLE audit_chain_state
    MODIFY updated_at DATETIME(6) NOT NULL;

ALTER TABLE audit_log
    MODIFY occurred_at DATETIME(6) NOT NULL;
