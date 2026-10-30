-- ============================================================================
-- ECOBRIDGES - Single Source of Truth migration
-- ============================================================================
-- Run this AFTER supabase/schema.sql in the Supabase SQL Editor for project
-- eibqniagoqfoxjtrbnjb. It is idempotent and safe to run more than once.
--
-- What it does:
--   1. Normalises the `profiles` table (role / KYC columns) and locks it to RLS.
--   2. Adds a SECURITY DEFINER helper `current_user_role()` so cross-role RLS
--      policies can be evaluated without recursive profile lookups.
--   3. Replaces the old own-row-only policies on collector_lots /
--      collector_transactions with role-aware policies: collectors own their
--      rows, formal recyclers may read + update every inbound lot, government
--      admins may read everything (read-only).
--   4. Adds role-based VIEWS (v_collector_my_lots, v_recycler_inbound_lots,
--      v_admin_lots, v_admin_metrics) backed by those policies.
--   5. Deletes ALL mock/seed business data (lots, transactions, photos,
--      locations, connection requests, quotations, audit logs, the fake
--      recycler registry) and seeds exactly ONE authorized recycler.
--   6. Seeds exactly ONE informal-collector and ONE formal-recycler profile by
--      looking up the demo auth users you create in Authentication -> Users.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) profiles: ensure the columns the Android client expects exist.
-- ---------------------------------------------------------------------------
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS auth_user_id          UUID;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS role                  TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS account_status        TEXT DEFAULT 'active';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS display_name          TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS entity_name           TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS statutory_identifier   TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS phone_number          TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email                 TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS created_at            TIMESTAMPTZ DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS profiles_auth_user_id_uidx
    ON public.profiles(auth_user_id);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------
-- 1) Cross-role helper. SECURITY DEFINER so it can read `profiles` while the
--    caller is subject to profiles RLS without recursing.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.current_user_role()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COALESCE(
        (SELECT p.role FROM public.profiles p WHERE p.auth_user_id = auth.uid() LIMIT 1),
        'anon'
    );
$$;

GRANT EXECUTE ON FUNCTION public.current_user_role() TO anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2) profiles policies: own row, plus full read for government admins.
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS profiles_own_select ON public.profiles;
CREATE POLICY profiles_own_select ON public.profiles
    FOR SELECT
    USING (
        auth.uid() = auth_user_id
        OR public.current_user_role() = 'government_admin'
    );

DROP POLICY IF EXISTS profiles_own_insert ON public.profiles;
CREATE POLICY profiles_own_insert ON public.profiles
    FOR INSERT
    WITH CHECK (auth.uid() = auth_user_id);

DROP POLICY IF EXISTS profiles_own_update ON public.profiles;
CREATE POLICY profiles_own_update ON public.profiles
    FOR UPDATE
    USING (auth.uid() = auth_user_id)
    WITH CHECK (auth.uid() = auth_user_id);

-- ---------------------------------------------------------------------------
-- 3) collector_lots: shared lifecycle record.
--    SELECT  -> owner, any formal recycler, any government admin
--    INSERT  -> owning informal collector only
--    UPDATE  -> owning collector OR any formal recycler (weigh-in / EPR)
--    DELETE  -> owning collector only
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS collector_lots_collector_idx
    ON public.collector_lots(collector_user_id);
CREATE INDEX IF NOT EXISTS collector_lots_status_idx
    ON public.collector_lots(status_name);

ALTER TABLE public.collector_lots ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS collector_lots_own ON public.collector_lots;
DROP POLICY IF EXISTS collector_lots_select ON public.collector_lots;
DROP POLICY IF EXISTS collector_lots_insert ON public.collector_lots;
DROP POLICY IF EXISTS collector_lots_update ON public.collector_lots;
DROP POLICY IF EXISTS collector_lots_delete ON public.collector_lots;

CREATE POLICY collector_lots_select ON public.collector_lots
    FOR SELECT
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() IN ('formal_recycler', 'government_admin')
    );

CREATE POLICY collector_lots_insert ON public.collector_lots
    FOR INSERT
    WITH CHECK (
        auth.uid() = collector_user_id
        AND public.current_user_role() = 'informal_collector'
    );

CREATE POLICY collector_lots_update ON public.collector_lots
    FOR UPDATE
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    )
    WITH CHECK (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    );

CREATE POLICY collector_lots_delete ON public.collector_lots
    FOR DELETE
    USING (auth.uid() = collector_user_id);

-- ---------------------------------------------------------------------------
-- 4) collector_transactions: same role model.
-- ---------------------------------------------------------------------------
ALTER TABLE public.collector_transactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS collector_transactions_own ON public.collector_transactions;
DROP POLICY IF EXISTS collector_transactions_select ON public.collector_transactions;
DROP POLICY IF EXISTS collector_transactions_insert ON public.collector_transactions;
DROP POLICY IF EXISTS collector_transactions_update ON public.collector_transactions;

CREATE POLICY collector_transactions_select ON public.collector_transactions
    FOR SELECT
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() IN ('formal_recycler', 'government_admin')
    );

CREATE POLICY collector_transactions_insert ON public.collector_transactions
    FOR INSERT
    WITH CHECK (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    );

CREATE POLICY collector_transactions_update ON public.collector_transactions
    FOR UPDATE
    USING (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    )
    WITH CHECK (
        auth.uid() = collector_user_id
        OR public.current_user_role() = 'formal_recycler'
    );

-- ---------------------------------------------------------------------------
-- 5) Role-based views (security_invoker => underlying RLS still applies).
-- ---------------------------------------------------------------------------
DROP VIEW IF EXISTS public.v_collector_my_lots;
CREATE VIEW public.v_collector_my_lots
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_lots
WHERE collector_user_id = auth.uid();

DROP VIEW IF EXISTS public.v_collector_my_transactions;
CREATE VIEW public.v_collector_my_transactions
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_transactions
WHERE collector_user_id = auth.uid();

DROP VIEW IF EXISTS public.v_recycler_inbound_lots;
CREATE VIEW public.v_recycler_inbound_lots
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_lots;

DROP VIEW IF EXISTS public.v_admin_lots;
CREATE VIEW public.v_admin_lots
    WITH (security_invoker = true) AS
SELECT * FROM public.collector_lots;

DROP VIEW IF EXISTS public.v_admin_metrics;
CREATE VIEW public.v_admin_metrics
    WITH (security_invoker = true) AS
SELECT
    (SELECT COUNT(*) FROM public.profiles WHERE role = 'informal_collector')                        AS collector_count,
    (SELECT COUNT(*) FROM public.profiles WHERE role = 'formal_recycler')                           AS recycler_count,
    (SELECT COUNT(*) FROM public.profiles WHERE role = 'government_admin')                          AS admin_count,
    (SELECT COUNT(*) FROM public.collector_lots)                                                    AS lot_count,
    (SELECT COUNT(*) FROM public.collector_lots WHERE recycler_confirmed = FALSE)                   AS pending_lot_count,
    (SELECT COUNT(*) FROM public.collector_lots WHERE recycler_confirmed = TRUE)                    AS confirmed_lot_count,
    (SELECT COALESCE(SUM(weight_kg), 0) FROM public.collector_lots)                                 AS total_weight_kg,
    (SELECT COALESCE(SUM(total_amount_inr), 0) FROM public.collector_transactions WHERE is_settled = TRUE) AS settled_value_inr;

GRANT SELECT ON public.v_collector_my_lots         TO authenticated;
GRANT SELECT ON public.v_collector_my_transactions TO authenticated;
GRANT SELECT ON public.v_recycler_inbound_lots     TO authenticated;
GRANT SELECT ON public.v_admin_lots                TO authenticated;
GRANT SELECT ON public.v_admin_metrics             TO authenticated;

-- ---------------------------------------------------------------------------
-- 5b) Reference tables. Read-only content shared by every role, replacing the
--     former on-device hardcoded seed. Writes are service-role only.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.material_prices (
    price_id            TEXT PRIMARY KEY,
    category_name       TEXT NOT NULL,
    sub_category        TEXT,
    location            TEXT,
    prevailing_buy_rate DOUBLE PRECISION,
    market_min          DOUBLE PRECISION,
    market_max          DOUBLE PRECISION,
    trend               TEXT,
    trend_percentage    DOUBLE PRECISION,
    unit                TEXT DEFAULT '₹/kg',
    date_updated        TEXT,
    key_metals_joined   TEXT
);
ALTER TABLE public.material_prices ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS material_prices_read ON public.material_prices;
CREATE POLICY material_prices_read ON public.material_prices FOR SELECT USING (TRUE);
GRANT SELECT ON public.material_prices TO anon, authenticated;

CREATE TABLE IF NOT EXISTS public.safety_guidelines (
    guideline_id             TEXT PRIMARY KEY,
    practice_title           TEXT NOT NULL,
    why_unsafe               TEXT,
    what_is_lost             TEXT,
    safe_formal_alternative  TEXT,
    icon_emoji               TEXT,
    alert_level              TEXT
);
ALTER TABLE public.safety_guidelines ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS safety_guidelines_read ON public.safety_guidelines;
CREATE POLICY safety_guidelines_read ON public.safety_guidelines FOR SELECT USING (TRUE);
GRANT SELECT ON public.safety_guidelines TO anon, authenticated;

INSERT INTO public.material_prices
    (price_id, category_name, sub_category, location, prevailing_buy_rate, market_min,
     market_max, trend, trend_percentage, unit, date_updated, key_metals_joined)
VALUES
    ('PR-PCB-01', 'PCB_BOARDS', 'High-Grade Telecom & PC Motherboards', 'Mumbai & Pune Region', 380, 340, 420, 'UP', 7.4, '₹/kg', 'Today', 'Gold (Au), Copper (Cu), Palladium (Pd), Gallium (Ga)'),
    ('PR-CAB-02', 'CABLES_WIRES', 'Stripped & Insulated Copper Harness', 'Mumbai & Pune Region', 460, 430, 490, 'UP', 5.2, '₹/kg', 'Today', 'High-Purity Electrolytic Copper (Cu)'),
    ('PR-BAT-03', 'BATTERIES', 'Lithium-Ion Phone & Laptop Packs', 'Mumbai & Pune Region', 160, 135, 185, 'UP', 11.0, '₹/kg', 'Today', 'Cobalt (Co), Lithium (Li), Nickel (Ni)'),
    ('PR-MOT-04', 'MOTORS_MAGNETS', 'Hard Disk & Speaker Neodymium Assemblies', 'Mumbai & Pune Region', 210, 190, 230, 'STABLE', 0.5, '₹/kg', 'Yesterday', 'Neodymium (Nd), Copper (Cu)'),
    ('PR-CRT-05', 'CRTS_MONITORS', 'Whole Sealed CRT Monitors (Intact)', 'Mumbai & Pune Region', 45, 35, 55, 'STABLE', 0.0, '₹/kg', '2 days ago', 'Copper Deflection Yoke, Heavy Lead Glass'),
    ('PR-LCD-06', 'LCD_PANELS', 'Flat Screen Displays & Laptops', 'Mumbai & Pune Region', 120, 100, 140, 'UP', 3.8, '₹/kg', 'Today', 'Indium Tin Oxide (ITO), Aluminum'),
    ('PR-PLS-07', 'MIXED_PLASTICS', 'Computer Housings ABS / Polycarbonate', 'Mumbai & Pune Region', 32, 28, 36, 'STABLE', 0.0, '₹/kg', '3 days ago', 'High-Grade Engineering Polymers')
ON CONFLICT (price_id) DO UPDATE SET
    category_name = EXCLUDED.category_name,
    sub_category = EXCLUDED.sub_category,
    location = EXCLUDED.location,
    prevailing_buy_rate = EXCLUDED.prevailing_buy_rate,
    market_min = EXCLUDED.market_min,
    market_max = EXCLUDED.market_max,
    trend = EXCLUDED.trend,
    trend_percentage = EXCLUDED.trend_percentage,
    unit = EXCLUDED.unit,
    date_updated = EXCLUDED.date_updated,
    key_metals_joined = EXCLUDED.key_metals_joined;

INSERT INTO public.safety_guidelines
    (guideline_id, practice_title, why_unsafe, what_is_lost, safe_formal_alternative, icon_emoji, alert_level)
VALUES
    ('HAZ-01', 'Open-Air Cable Burning (खुली आग में तार जलाना)',
     'Burning plastic & PVC insulation releases deadly Dioxins, Furans, and Lead fumes into your lungs and neighborhood.',
     'Burning oxidizes and degrades copper quality, lowering scrap value by 20-30% and causing chronic respiratory illness.',
     'Use low-cost mechanical wire stripper or handover intact wires to authorized recyclers for full pure electrolytic copper rates (₹460/kg).',
     '🔥', 'CRITICAL'),
    ('HAZ-02', 'Acid Leaching of Circuit Boards (एसिड में मदरबोर्ड गलाना)',
     'Using Aqua Regia and Nitric Acid creates toxic nitrogen dioxide clouds, water table poisoning, and high chemical burn risk.',
     'Backyard acid only recovers partial gold (loss of 85% palladium, neodymium, gallium, and tantalum worth thousands of rupees).',
     'Sell whole PCBs to formal recyclers who operate closed-loop hydrometallurgical recovery, paying for gold, silver, and rare earth contents.',
     '🧪', 'CRITICAL'),
    ('HAZ-03', 'Shattering CRT Monitors (सीआरटी स्क्रीन तोड़ना)',
     'CRT tubes contain high vacuum (implosion hazard) and 1.5 to 3 kg of lead and toxic barium phosphor powder that causes neurological damage.',
     'Broken glass cannot be safely processed and is rejected by formal recyclers, forfeiting your payment.',
     'Keep CRT monitors completely intact. Handover in one piece to authorized aggregators for safe glass lead-separation.',
     '📺', 'HIGH'),
    ('HAZ-04', 'Crushing or Puncturing Lithium Batteries (बैटरी फोड़ना)',
     'Lithium-ion cells catch fire instantaneously upon puncture (thermal runaway up to 600C) and release toxic hydrofluoric acid gas.',
     'Destroys valuable high-grade cobalt and nickel cathodes and creates severe personal burn risks.',
     'Store batteries in a dry, cool wooden/plastic crate without metal contact. Formal refiners recover 95% of lithium and cobalt safely.',
     '⚡', 'CRITICAL')
ON CONFLICT (guideline_id) DO UPDATE SET
    practice_title = EXCLUDED.practice_title,
    why_unsafe = EXCLUDED.why_unsafe,
    what_is_lost = EXCLUDED.what_is_lost,
    safe_formal_alternative = EXCLUDED.safe_formal_alternative,
    icon_emoji = EXCLUDED.icon_emoji,
    alert_level = EXCLUDED.alert_level;

-- ---------------------------------------------------------------------------
-- 6) Remove ALL mock / seed business data. Guarded so a partially-provisioned
--    database does not abort the migration.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.collector_transactions') IS NOT NULL THEN DELETE FROM public.collector_transactions; END IF;
    IF to_regclass('public.collector_lots')         IS NOT NULL THEN DELETE FROM public.collector_lots;         END IF;
    IF to_regclass('public.lot_photos')             IS NOT NULL THEN DELETE FROM public.lot_photos;             END IF;
    IF to_regclass('public.collector_locations')    IS NOT NULL THEN DELETE FROM public.collector_locations;    END IF;
    IF to_regclass('public.connection_requests')    IS NOT NULL THEN DELETE FROM public.connection_requests;    END IF;
    IF to_regclass('public.quotations')             IS NOT NULL THEN DELETE FROM public.quotations;             END IF;
    IF to_regclass('public.audit_logs')             IS NOT NULL THEN DELETE FROM public.audit_logs;             END IF;
END $$;

-- Exactly ONE authorized formal recycler.
DO $$
BEGIN
    IF to_regclass('public.authorized_recyclers') IS NOT NULL THEN
        DELETE FROM public.authorized_recyclers;
        INSERT INTO public.authorized_recyclers
            (recycler_id, name, facility_location, city, distance_km, cpcb_reg_no,
             authorization_validity, phone, accepted_categories, doorstep_pickup,
             min_weight_for_pickup_kg, rating, latitude, longitude)
        VALUES
            ('REC-CPCB-MH-001', 'EcoReclaim Green Refineries Pvt Ltd',
             'Plot C-14, MIDC Turbhe, Navi Mumbai', 'Mumbai', 4.2,
             'CPCB/EPR-REC/2023/MH-0042', 'Valid until Dec 2028', '+91 98201 44521',
             'PCB_BOARDS,CABLES_WIRES,BATTERIES,MOTORS_MAGNETS,LCD_PANELS',
             TRUE, 25.0, 4.9, 19.0688, 73.0189);
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 7) Seed exactly ONE informal collector + ONE formal recycler + ONE admin
--    profile, keyed off the demo auth users you create first.
--
--    Create these users in Supabase Dashboard -> Authentication -> Users
--    (tick "Auto Confirm User"), then run this file:
--
--      collector@ecobridges.demo   EcoBridge#2026
--      recycler@ecobridges.demo    EcoBridge#2026
--      admin@ecobridges.demo       EcoBridge#2026
--
--    The Information Collector's registered demo mobile is +91 7708609156.
--    That is the only collector number accepted by OTP_MODE=demo.
-- ---------------------------------------------------------------------------
INSERT INTO public.profiles
    (auth_user_id, role, account_status, display_name, entity_name,
     statutory_identifier, phone_number, email)
SELECT u.id, 'informal_collector', 'active', 'Demo Informal Collector', '',
       'COL-MH-0001', '+917708609156', u.email
FROM auth.users u
WHERE u.email = 'collector@ecobridges.demo'
ON CONFLICT (auth_user_id) DO UPDATE SET
    role = EXCLUDED.role,
    account_status = EXCLUDED.account_status,
    display_name = EXCLUDED.display_name,
    entity_name = EXCLUDED.entity_name,
    statutory_identifier = EXCLUDED.statutory_identifier,
    phone_number = EXCLUDED.phone_number,
    email = EXCLUDED.email;

INSERT INTO public.profiles
    (auth_user_id, role, account_status, display_name, entity_name,
     statutory_identifier, phone_number, email)
SELECT u.id, 'formal_recycler', 'active', 'EcoReclaim Green Refineries Pvt Ltd',
       'EcoReclaim Green Refineries Pvt Ltd', 'CPCB/EPR-REC/2023/MH-0042',
       '+919820144521', u.email
FROM auth.users u
WHERE u.email = 'recycler@ecobridges.demo'
ON CONFLICT (auth_user_id) DO UPDATE SET
    role = EXCLUDED.role,
    account_status = EXCLUDED.account_status,
    display_name = EXCLUDED.display_name,
    entity_name = EXCLUDED.entity_name,
    statutory_identifier = EXCLUDED.statutory_identifier,
    phone_number = EXCLUDED.phone_number,
    email = EXCLUDED.email;

INSERT INTO public.profiles
    (auth_user_id, role, account_status, display_name, entity_name,
     statutory_identifier, phone_number, email)
SELECT u.id, 'government_admin', 'active', 'Demo CPCB Administrator', 'CPCB',
       'ADM-MoEFCC-0001', '+919000000001', u.email
FROM auth.users u
WHERE u.email = 'admin@ecobridges.demo'
ON CONFLICT (auth_user_id) DO UPDATE SET
    role = EXCLUDED.role,
    account_status = EXCLUDED.account_status,
    display_name = EXCLUDED.display_name,
    entity_name = EXCLUDED.entity_name,
    statutory_identifier = EXCLUDED.statutory_identifier,
    phone_number = EXCLUDED.phone_number,
    email = EXCLUDED.email;

-- ---------------------------------------------------------------------------
-- 8) Verify the initial state.
-- ---------------------------------------------------------------------------
SELECT 'profiles' AS entity,
       COUNT(*) FILTER (WHERE role = 'informal_collector') AS collectors,
       COUNT(*) FILTER (WHERE role = 'formal_recycler')    AS recyclers,
       COUNT(*) FILTER (WHERE role = 'government_admin')   AS admins
FROM public.profiles;

SELECT 'collector_lots' AS entity, COUNT(*) AS rows FROM public.collector_lots
UNION ALL
SELECT 'collector_transactions', COUNT(*) FROM public.collector_transactions
UNION ALL
SELECT 'authorized_recyclers', COUNT(*) FROM public.authorized_recyclers
UNION ALL
SELECT 'connection_requests', COUNT(*) FROM public.connection_requests
UNION ALL
SELECT 'quotations', COUNT(*) FROM public.quotations
UNION ALL
SELECT 'audit_logs', COUNT(*) FROM public.audit_logs;
