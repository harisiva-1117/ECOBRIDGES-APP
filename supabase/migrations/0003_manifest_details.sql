-- Formal recycler's completed Form-6 manifest, visible to the collector.
ALTER TABLE collector_lots ADD COLUMN IF NOT EXISTS manifest_details TEXT;
