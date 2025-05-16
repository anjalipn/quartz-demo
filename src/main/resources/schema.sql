CREATE TABLE INSIGHTS_STATUS_QUEUE (
    id VARCHAR2(50) PRIMARY KEY,
    invocation_id VARCHAR2(50) NOT NULL,
    invocation_status VARCHAR2(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed CHAR(1) DEFAULT 'N',
    CONSTRAINT chk_status CHECK (invocation_status IN ('IN_PROGRESS', 'SUCCESSFUL', 'FAILED', 'TIMED_OUT', 'CANCELLED')),
    CONSTRAINT chk_processed CHECK (processed IN ('Y', 'N'))
); 