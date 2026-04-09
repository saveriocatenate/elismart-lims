-- Add unique constraint on (name, manufacturer) for reagent_catalog.
-- The service layer already checks for duplicates at application level, but relying
-- on an application-level check alone leaves a race condition window under concurrent
-- requests. This constraint enforces uniqueness at the database level as well.

ALTER TABLE reagent_catalog
    ADD CONSTRAINT uq_rc_name_manufacturer UNIQUE (name, manufacturer);
