-- Backfill any pre-existing NULLs before enforcing NOT NULL constraints.
-- In a fresh database these UPDATE statements are no-ops.
UPDATE measurement_pair SET signal_1    = 0.0 WHERE signal_1    IS NULL;
UPDATE measurement_pair SET signal_2    = 0.0 WHERE signal_2    IS NULL;
UPDATE measurement_pair SET signal_mean = 0.0 WHERE signal_mean IS NULL;

ALTER TABLE measurement_pair ALTER COLUMN signal_1    SET NOT NULL;
ALTER TABLE measurement_pair ALTER COLUMN signal_2    SET NOT NULL;
ALTER TABLE measurement_pair ALTER COLUMN signal_mean SET NOT NULL;
