-- Append-only audit trail for every field-level change across all auditable entities.
-- No UPDATE or DELETE is ever performed on this table.

CREATE TABLE audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(100)  NOT NULL,
    entity_id   BIGINT        NOT NULL,
    field_name  VARCHAR(100)  NOT NULL,
    old_value   VARCHAR(4000),
    new_value   VARCHAR(4000),
    changed_by  VARCHAR(255)  NOT NULL,
    changed_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason      VARCHAR(1000)
);

CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
