-- Stores the serialised JSON of fitted calibration curve parameters for an experiment.
-- The column is nullable: it is populated only after the validation endpoint is invoked.

ALTER TABLE experiment ADD COLUMN curve_parameters CLOB;
