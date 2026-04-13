-- ReagentBatch: physical lot/batch of a reagent, registered once and reused across experiments.
-- The UNIQUE constraint on (reagent_id, lot_number) prevents duplicate lot registrations.

CREATE TABLE reagent_batch (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reagent_id  BIGINT NOT NULL,
    lot_number  VARCHAR(100) NOT NULL,
    expiry_date DATE NOT NULL,
    supplier    VARCHAR(255),
    notes       VARCHAR(2000),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by  VARCHAR(255),
    CONSTRAINT fk_rb_reagent FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id),
    CONSTRAINT uq_reagent_lot UNIQUE (reagent_id, lot_number)
);
