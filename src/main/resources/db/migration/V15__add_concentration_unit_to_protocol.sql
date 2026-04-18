-- V15: Add concentration_unit column to protocol.
-- Existing protocols default to 'ng/mL'.
ALTER TABLE protocol
    ADD COLUMN concentration_unit VARCHAR(20) NOT NULL DEFAULT 'ng/mL';
