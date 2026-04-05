-- Baseline migration: complete LIMS schema from ER diagram.

CREATE TABLE protocol (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    num_calibration_pairs INT NOT NULL,
    num_control_pairs INT NOT NULL,
    max_cv_allowed DOUBLE NOT NULL,
    max_error_allowed DOUBLE NOT NULL
);

CREATE TABLE reagent_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE protocol_reagent_spec (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    protocol_id BIGINT NOT NULL,
    reagent_id BIGINT NOT NULL,
    is_mandatory BOOLEAN NOT NULL,
    CONSTRAINT fk_prs_protocol FOREIGN KEY (protocol_id) REFERENCES protocol(id),
    CONSTRAINT fk_prs_reagent FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id),
    CONSTRAINT uq_prs_protocol_reagent UNIQUE (protocol_id, reagent_id)
);

CREATE TABLE experiment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    protocol_id BIGINT NOT NULL,
    CONSTRAINT fk_experiment_protocol FOREIGN KEY (protocol_id) REFERENCES protocol(id)
);

CREATE TABLE used_reagent_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id BIGINT NOT NULL,
    reagent_id BIGINT NOT NULL,
    lot_number VARCHAR(255) NOT NULL,
    expiry_date DATE,
    CONSTRAINT fk_urb_experiment FOREIGN KEY (experiment_id) REFERENCES experiment(id),
    CONSTRAINT fk_urb_reagent FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id),
    CONSTRAINT uq_urb_exp_reagent_lot UNIQUE (experiment_id, reagent_id, lot_number)
);

CREATE TABLE measurement_pair (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id BIGINT NOT NULL,
    measurement_id BIGINT,
    pair_type VARCHAR(50) NOT NULL,
    concentration_nominal DOUBLE,
    signal_1 DOUBLE,
    signal_2 DOUBLE,
    signal_mean DOUBLE,
    cv_pct DOUBLE,
    recovery_pct DOUBLE,
    is_outlier BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_mp_experiment FOREIGN KEY (experiment_id) REFERENCES experiment(id)
);

CREATE INDEX idx_experiment_protocol ON experiment(protocol_id);
CREATE INDEX idx_protocol_reagent_spec_protocol ON protocol_reagent_spec(protocol_id);
CREATE INDEX idx_protocol_reagent_spec_reagent ON protocol_reagent_spec(reagent_id);
CREATE INDEX idx_measurement_pair_experiment ON measurement_pair(experiment_id);
CREATE INDEX idx_used_reagent_batch_experiment ON used_reagent_batch(experiment_id);