-- ============================================================================
-- ECOBRIDGES - Authorized Recycler / Aggregator dataset (0005)
-- ============================================================================
-- Run this in the Supabase SQL Editor for project eibqniagoqfoxjtrbnjb,
-- AFTER supabase/schema.sql and 0001..0004. Idempotent, safe to re-run.
--
-- What it does:
--   1. Adds the mandatory dataset columns to `authorized_recyclers`:
--      authorization_status, service_area, contact_email (+ backfill).
--   2. Adds CHECK validation (status allow-list, non-empty name/reg no).
--   3. Creates `recycler_offered_rates` (per-material offered rates with
--      positive-rate validation + one row per recycler/category).
--   4. Opens public read access (anon + authenticated) to both; writes stay
--      service-role only (no INSERT/UPDATE/DELETE policies).
--   5. Seeds offered rates for the authorized demo recycler.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) Mandatory columns + backfill.
-- ---------------------------------------------------------------------------
ALTER TABLE public.authorized_recyclers
    ADD COLUMN IF NOT EXISTS authorization_status TEXT DEFAULT 'active';
ALTER TABLE public.authorized_recyclers
    ADD COLUMN IF NOT EXISTS service_area TEXT;
ALTER TABLE public.authorized_recyclers
    ADD COLUMN IF NOT EXISTS contact_email TEXT;

UPDATE public.authorized_recyclers
SET authorization_status = 'active'
WHERE authorization_status IS NULL OR authorization_status = '';

UPDATE public.authorized_recyclers
SET service_area = COALESCE(NULLIF(service_area, ''), city, 'Mumbai & Pune Region')
WHERE service_area IS NULL OR service_area = '';

-- ---------------------------------------------------------------------------
-- 2) Validation constraints.
-- ---------------------------------------------------------------------------
ALTER TABLE public.authorized_recyclers
    DROP CONSTRAINT IF EXISTS authorized_recyclers_status_check;
ALTER TABLE public.authorized_recyclers
    ADD CONSTRAINT authorized_recyclers_status_check
    CHECK (authorization_status IN ('active', 'suspended', 'expired', 'pending'));

ALTER TABLE public.authorized_recyclers
    DROP CONSTRAINT IF EXISTS authorized_recyclers_name_check;
ALTER TABLE public.authorized_recyclers
    ADD CONSTRAINT authorized_recyclers_name_check
    CHECK (name IS NOT NULL AND name <> '');

ALTER TABLE public.authorized_recyclers
    DROP CONSTRAINT IF EXISTS authorized_recyclers_regno_check;
ALTER TABLE public.authorized_recyclers
    ADD CONSTRAINT authorized_recyclers_regno_check
    CHECK (cpcb_reg_no IS NOT NULL AND cpcb_reg_no <> '');

-- ---------------------------------------------------------------------------
-- 3) Offered rates per accepted material.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.recycler_offered_rates (
    rate_id        TEXT PRIMARY KEY,
    recycler_id    TEXT NOT NULL REFERENCES public.authorized_recyclers(recycler_id)
                   ON DELETE CASCADE,
    category_name  TEXT NOT NULL,
    rate_per_kg    DOUBLE PRECISION NOT NULL,
    unit           TEXT NOT NULL DEFAULT '₹/kg',
    effective_from TIMESTAMPTZ DEFAULT now(),
    UNIQUE (recycler_id, category_name)
);

ALTER TABLE public.recycler_offered_rates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS recycler_offered_rates_public_read ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_public_read ON public.recycler_offered_rates
    FOR SELECT
    USING (true);

GRANT SELECT ON public.recycler_offered_rates TO anon, authenticated;

ALTER TABLE public.recycler_offered_rates
    DROP CONSTRAINT IF EXISTS recycler_offered_rates_positive_check;
ALTER TABLE public.recycler_offered_rates
    ADD CONSTRAINT recycler_offered_rates_positive_check
    CHECK (rate_per_kg > 0);

-- ---------------------------------------------------------------------------
-- 4) Seed offered rates for the authorized demo recycler
--    (CPCB/EPR-REC/2023/MH-0042, active).
-- ---------------------------------------------------------------------------
INSERT INTO public.recycler_offered_rates
    (rate_id, recycler_id, category_name, rate_per_kg, unit)
VALUES
    ('RATE-001-PCB', 'REC-CPCB-MH-001', 'PCB_BOARDS', 380, '₹/kg'),
    ('RATE-001-CAB', 'REC-CPCB-MH-001', 'CABLES_WIRES', 460, '₹/kg'),
    ('RATE-001-BAT', 'REC-CPCB-MH-001', 'BATTERIES', 160, '₹/kg'),
    ('RATE-001-MOT', 'REC-CPCB-MH-001', 'MOTORS_MAGNETS', 210, '₹/kg'),
    ('RATE-001-LCD', 'REC-CPCB-MH-001', 'LCD_PANELS', 120, '₹/kg')
ON CONFLICT (recycler_id, category_name) DO UPDATE SET
    rate_per_kg = EXCLUDED.rate_per_kg,
    unit = EXCLUDED.unit,
    effective_from = now();

-- ---------------------------------------------------------------------------
-- 5) Verify the dataset.
-- ---------------------------------------------------------------------------
SELECT 'authorized_recyclers' AS entity,
       COUNT(*) AS total,
       COUNT(*) FILTER (WHERE authorization_status = 'active') AS active
FROM public.authorized_recyclers;

SELECT 'recycler_offered_rates' AS entity,
       COUNT(*) AS rows
FROM public.recycler_offered_rates;
