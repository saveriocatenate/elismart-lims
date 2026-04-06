-- Add audit columns (created_at, updated_at, created_by) to every table.
-- Rows inserted before this migration receive CURRENT_TIMESTAMP / 'system' so
-- the NOT NULL constraints are satisfied immediately for pre-existing data.
-- Each column is added in a separate statement for H2 compatibility.

ALTER TABLE protocol ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE protocol ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE protocol ADD COLUMN created_by VARCHAR(100) DEFAULT 'system' NOT NULL;

ALTER TABLE reagent_catalog ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE reagent_catalog ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE reagent_catalog ADD COLUMN created_by VARCHAR(100) DEFAULT 'system' NOT NULL;

ALTER TABLE protocol_reagent_spec ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE protocol_reagent_spec ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE protocol_reagent_spec ADD COLUMN created_by VARCHAR(100) DEFAULT 'system' NOT NULL;

ALTER TABLE experiment ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE experiment ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE experiment ADD COLUMN created_by VARCHAR(100) DEFAULT 'system' NOT NULL;

ALTER TABLE used_reagent_batch ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE used_reagent_batch ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE used_reagent_batch ADD COLUMN created_by VARCHAR(100) DEFAULT 'system' NOT NULL;

ALTER TABLE measurement_pair ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE measurement_pair ADD COLUMN updated_at TIMESTAMP;
ALTER TABLE measurement_pair ADD COLUMN created_by VARCHAR(100) DEFAULT 'system' NOT NULL;
