-- Editing a material transaction reverses its stock movements and inserts replacement items. Items that already have
-- stock movements are never deleted; they are marked superseded instead. Every read and total uses superseded_at IS NULL.
ALTER TABLE material_transaction_items ADD COLUMN superseded_at TIMESTAMPTZ;
CREATE INDEX ix_mti_active ON material_transaction_items(transaction_id) WHERE superseded_at IS NULL;
