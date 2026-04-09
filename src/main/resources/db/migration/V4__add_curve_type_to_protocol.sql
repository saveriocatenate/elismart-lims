-- Add curve_type column to protocol table.
-- Existing rows default to FOUR_PARAMETER_LOGISTIC (the ELISA standard).
-- A CHECK constraint mirrors the CurveType Java enum to prevent invalid values.

ALTER TABLE protocol
    ADD COLUMN curve_type VARCHAR(50) NOT NULL DEFAULT 'FOUR_PARAMETER_LOGISTIC';

ALTER TABLE protocol
    ADD CONSTRAINT chk_protocol_curve_type
    CHECK (curve_type IN (
        'FOUR_PARAMETER_LOGISTIC',
        'FIVE_PARAMETER_LOGISTIC',
        'LOG_LOGISTIC_3P',
        'LINEAR',
        'SEMI_LOG_LINEAR',
        'POINT_TO_POINT'
    ));
