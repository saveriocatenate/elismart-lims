-- Enforce valid enum values for experiment.status and measurement_pair.pair_type.
-- These constraints mirror the ExperimentStatus and PairType Java enums and prevent
-- arbitrary string values from entering the database.

ALTER TABLE experiment
    ADD CONSTRAINT chk_experiment_status
    CHECK (status IN ('PENDING', 'COMPLETED', 'OK', 'KO', 'VALIDATION_ERROR'));

ALTER TABLE measurement_pair
    ADD CONSTRAINT chk_measurement_pair_type
    CHECK (pair_type IN ('CALIBRATION', 'CONTROL', 'SAMPLE'));
