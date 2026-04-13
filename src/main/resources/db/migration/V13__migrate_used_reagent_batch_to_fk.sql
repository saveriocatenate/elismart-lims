-- Migrate used_reagent_batch to reference reagent_batch instead of inlining lot/expiry.
-- Data migration: unique lots are extracted into reagent_batch, then FK wired.

-- Step 0: Drop old unique constraint (references lot_number which will be removed)
ALTER TABLE used_reagent_batch DROP CONSTRAINT uq_urb_exp_reagent_lot;

-- Step 1: Populate reagent_batch with unique lots already recorded in used_reagent_batch
INSERT INTO reagent_batch (reagent_id, lot_number, expiry_date, created_at, created_by)
SELECT DISTINCT urb.reagent_id, urb.lot_number, urb.expiry_date, CURRENT_TIMESTAMP, 'migration'
FROM used_reagent_batch urb
WHERE NOT EXISTS (
    SELECT 1 FROM reagent_batch rb
    WHERE rb.reagent_id = urb.reagent_id AND rb.lot_number = urb.lot_number
);

-- Step 2: Add the new FK column (nullable initially)
ALTER TABLE used_reagent_batch ADD COLUMN reagent_batch_id BIGINT;

-- Step 3: Populate FK by matching reagent_id + lot_number
UPDATE used_reagent_batch urb
SET reagent_batch_id = (
    SELECT rb.id FROM reagent_batch rb
    WHERE rb.reagent_id = urb.reagent_id AND rb.lot_number = urb.lot_number
);

-- Step 4: Make the FK column NOT NULL and add the referential constraint
ALTER TABLE used_reagent_batch ALTER COLUMN reagent_batch_id SET NOT NULL;
ALTER TABLE used_reagent_batch ADD CONSTRAINT fk_urb_reagent_batch
    FOREIGN KEY (reagent_batch_id) REFERENCES reagent_batch(id);

-- Step 5: New unique constraint — one reagent batch per experiment
ALTER TABLE used_reagent_batch ADD CONSTRAINT uq_urb_exp_reagent_batch
    UNIQUE (experiment_id, reagent_batch_id);

-- Step 6: Drop the now-redundant inline columns
ALTER TABLE used_reagent_batch DROP COLUMN lot_number;
ALTER TABLE used_reagent_batch DROP COLUMN expiry_date;
