-- Sample entity: tracks biological sample identity for full lot-traceability.
-- Only MeasurementPairs of type SAMPLE are linked to a Sample row; sample_id is nullable.

CREATE TABLE sample (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    barcode             VARCHAR(100) NOT NULL UNIQUE,
    matrix_type         VARCHAR(100),
    patient_id          VARCHAR(100),
    study_id            VARCHAR(100),
    collection_date     DATE,
    preparation_method  VARCHAR(500),
    notes               VARCHAR(2000),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by          VARCHAR(255)
);

ALTER TABLE measurement_pair ADD COLUMN sample_id BIGINT;
ALTER TABLE measurement_pair ADD CONSTRAINT fk_mp_sample
    FOREIGN KEY (sample_id) REFERENCES sample(id);

CREATE INDEX idx_mp_sample ON measurement_pair(sample_id);
