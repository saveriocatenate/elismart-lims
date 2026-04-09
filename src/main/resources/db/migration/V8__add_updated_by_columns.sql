-- Add updated_by audit column to every auditable table.
-- Mirrors the @LastModifiedBy field added to the Auditable superclass.
-- Pre-existing rows receive NULL (the column is nullable by design).

ALTER TABLE protocol           ADD COLUMN updated_by VARCHAR(255);
ALTER TABLE reagent_catalog    ADD COLUMN updated_by VARCHAR(255);
ALTER TABLE protocol_reagent_spec ADD COLUMN updated_by VARCHAR(255);
ALTER TABLE experiment         ADD COLUMN updated_by VARCHAR(255);
ALTER TABLE used_reagent_batch ADD COLUMN updated_by VARCHAR(255);
ALTER TABLE measurement_pair   ADD COLUMN updated_by VARCHAR(255);
ALTER TABLE app_user           ADD COLUMN updated_by VARCHAR(255);
