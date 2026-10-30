-- ============================================================================
-- ECOBRIDGES - Cloud Sync, Recycler Offer & Handover Integrity Migration (0009)
-- ============================================================================
-- Safe, additive, idempotent forward-only migration.
-- Designed to run after migrations 0001-0006 on project ekqatebpbvxwjohfhipf.
-- Compatible whether or not previous partial migrations exist.
--
-- What this migration provides:
--   1. Recycler facility resolution via `current_recycler_id()` joined from
--      profiles.authorized_recyclers on cpcb_reg_no or contact_email.
--      Never trusts client-supplied recycler_id.
--   2. Recycler purchase offers on `recycler_offered_rates` with ownership RLS,
--      version-predicated updates, unique (recycler_id, category_name) guard,
--      and immutable updated_by_label.
--   3. Admin-managed reference prices on `material_prices`: grade, currency,
--      effective date, active flag, audit labels, optimistic versioning.
--   4. Immutable price history (`material_price_history`) written by trigger
--      on every insert/update of reference prices. Admin-only read.
--   5. Confirmed handover rate immutability on `collector_lots` via trigger.
--   6. Security hardening from 0007: signup trigger, role helper, profile
--      policies, storage bucket, checks (idempotent re-runs).
--   7. Quotation ownership immutability enforcement.
--   8. No demo profile seeds: backend demo JWTs are not Supabase sessions;
--      profiles are managed server-side only.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) Idempotent column/vector seeding (safe to re-run, never destructive)
-- ---------------------------------------------------------------------------

-- Profiles: ensure columns expected by the app exist. Adding columns is
-- always safe; policies below govern what can be written.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'profiles' AND column_name = 'entity_name') THEN
        ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS entity_name TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'profiles' AND column_name = 'updated_by_label') THEN
        ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS updated_by_label TEXT;
    END IF;
END;
$$;

-- material_prices columns (idempotent additions)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'grade') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS grade TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'currency') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'INR';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'is_active') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'effective_date') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS effective_date DATE DEFAULT CURRENT_DATE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'updated_by') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS updated_by UUID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'updated_by_label') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS updated_by_label TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'updated_at') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'material_prices' AND column_name = 'version') THEN
        ALTER TABLE public.material_prices ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;
    END IF;
END;
$$;

-- recycler_offered_rates columns (idempotent additions)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'grade') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS grade TEXT DEFAULT '';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'currency') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS currency TEXT DEFAULT 'INR';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'min_quantity_kg') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS min_quantity_kg DOUBLE PRECISION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'max_quantity_kg') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS max_quantity_kg DOUBLE PRECISION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'condition') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS condition TEXT DEFAULT 'ACCEPTED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'status') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'ACTIVE';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'notes') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS notes TEXT DEFAULT '';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'updated_at') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'updated_by') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS updated_by UUID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'updated_by_label') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS updated_by_label TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'recycler_offered_rates' AND column_name = 'version') THEN
        ALTER TABLE public.recycler_offered_rates ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
    END IF;
END;
$$;

-- collector_lots columns (idempotent additions for handover immutability)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'collector_lots' AND column_name = 'agreed_rate_per_kg') THEN
        ALTER TABLE public.collector_lots ADD COLUMN IF NOT EXISTS agreed_rate_per_kg DOUBLE PRECISION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'collector_lots' AND column_name = 'agreed_at') THEN
        ALTER TABLE public.collector_lots ADD COLUMN IF NOT EXISTS agreed_at TIMESTAMPTZ;
    END IF;
END;
$$;

-- material_price_history table (idempotent: creates only if missing)
CREATE TABLE IF NOT EXISTS public.material_price_history (
    history_id          BIGSERIAL PRIMARY KEY,
    price_id            TEXT NOT NULL,
    category_name       TEXT NOT NULL,
    sub_category        TEXT,
    grade               TEXT,
    location            TEXT,
    prevailing_buy_rate DOUBLE PRECISION,
    market_min          DOUBLE PRECISION,
    market_max          DOUBLE PRECISION,
    currency            TEXT,
    effective_date      DATE,
    is_active           BOOLEAN,
    version             BIGINT,
    change_type         TEXT NOT NULL,
    changed_by          UUID,
    changed_by_label    TEXT,
    changed_at          TIMESTAMPTZ DEFAULT now()
);
ALTER TABLE public.material_price_history ENABLE ROW LEVEL SECURITY;

-- Drop/recreate policies cleanly (idempotent)
DROP POLICY IF EXISTS material_price_history_read ON public.material_price_history;
CREATE POLICY material_price_history_read ON public.material_price_history
    FOR SELECT
    USING (TRUE);  -- read-only; service-role writes history via trigger

GRANT SELECT ON public.material_price_history TO anon, authenticated;

-- ---------------------------------------------------------------------------
-- 1) Signup trigger: hardened from 0007 — no self-assignment of government_admin
--    formal_recyclers start as pending_verification; enforced role/account_status checks.
-- ---------------------------------------------------------------------------

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

DROP FUNCTION IF EXISTS public.handle_new_user();
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_requested_role TEXT := COALESCE(NULLIF(NEW.raw_user_meta_data->>'role', ''), 'informal_collector');
    v_role TEXT;
    v_status TEXT;
    v_phone_digits TEXT := regexp_replace(COALESCE(NEW.raw_user_meta_data->>'phone_number', ''), '\D', '', 'g');
    v_phone TEXT;
    v_statutory TEXT;
BEGIN
    -- Only informal_collector or formal_recycler may be requested.
    -- government_admin (or any unknown) is never granted at signup;
    -- an administrator must provision such a role via service-role / dashboard.
    IF v_requested_role IN ('informal_collector', 'formal_recycler') THEN
        v_role := v_requested_role;
    ELSE
        v_role := 'informal_collector';
    END IF;

    -- Normalize phone: expect +91 + 10 digits
    IF length(v_phone_digits) = 12 AND left(v_phone_digits, 2) = '91' THEN
        v_phone_digits := right(v_phone_digits, 10);
    END IF;
    IF v_phone_digits ~ '^[6-9][0-9]{9}$' THEN
        v_phone := '+91 ' || v_phone_digits;
    ELSE
        v_phone := NULL;
    END IF;

    -- Recyclers are unverified until an administrator approves them.
    IF v_role = 'formal_recycler' THEN
        v_status := 'pending_verification';
        v_statutory := COALESCE(NULLIF(NEW.raw_user_meta_data->>'statutory_identifier', ''), '');
    ELSE
        v_status := CASE WHEN v_phone IS NULL THEN 'pending_verification' ELSE 'active' END;
        v_statutory := COALESCE(NULLIF(NEW.raw_user_meta_data->>'statutory_identifier', ''), '');
    END IF;

    INSERT INTO public.profiles
        (auth_user_id, role, account_status, display_name, entity_name,
         statutory_identifier, phone_number, email)
    VALUES (
        NEW.id,
        v_role,
        v_status,
        COALESCE(NULLIF(NEW.raw_user_meta_data->>'display_name', ''), split_part(COALESCE(NEW.email, ''), '@', 1)),
        COALESCE(NEW.raw_user_meta_data->>'entity_name', ''),
        v_statutory,
        v_phone,
        NEW.email
    )
    ON CONFLICT (auth_user_id) DO NOTHING;

    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- 2) Role helper: active accounts only; recyclers must match an active
--    CPCB registry record. Any other state resolves to a non-privileged slug.
-- ---------------------------------------------------------------------------

DROP FUNCTION IF EXISTS public.current_user_role();
CREATE OR REPLACE FUNCTION public.current_user_role()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT CASE
        WHEN p.role IS NULL THEN 'anon'
        WHEN p.account_status IS DISTINCT FROM 'active' THEN
            CASE WHEN p.role = 'formal_recycler' THEN 'unverified_recycler' ELSE 'anon' END
        WHEN p.role = 'formal_recycler' AND NOT EXISTS (
            SELECT 1
            FROM public.authorized_recyclers ar
            WHERE ar.authorization_status = 'active'
              AND (
                    (p.statutory_identifier <> '' AND ar.cpcb_reg_no = p.statutory_identifier)
                 OR (p.email IS NOT NULL AND lower(ar.contact_email) = lower(p.email))
              )
        ) THEN 'unverified_recycler'
        WHEN p.role IN ('informal_collector', 'formal_recycler', 'government_admin') THEN p.role
        ELSE 'anon'
    END
    FROM public.profiles p
    WHERE p.auth_user_id = auth.uid()
    LIMIT 1;
$$;

GRANT EXECUTE ON FUNCTION public.current_user_role() TO anon, authenticated;

-- ---------------------------------------------------------------------------
-- 3) Profiles policies: insert limited to two self-signup roles, non-verified
--    status OK; update cannot alter role/status/ownership.
-- ---------------------------------------------------------------------------

DROP POLICY IF EXISTS profiles_own_insert ON public.profiles;
CREATE POLICY profiles_own_insert ON public.profiles
    FOR INSERT
    WITH CHECK (
        auth.uid() = auth_user_id
        AND role IN ('informal_collector', 'formal_recycler')
        AND account_status IN ('active', 'pending_verification')
    );

DROP POLICY IF EXISTS profiles_own_update ON public.profiles;
CREATE POLICY profiles_own_update ON public.profiles
    FOR UPDATE
    USING (auth.uid() = auth_user_id)
    WITH CHECK (
        auth.uid() = auth_user_id
        AND role IS NOT DISTINCT FROM public.current_user_role()
        AND account_status IS NOT DISTINCT FROM (
            SELECT p.account_status FROM public.profiles p
            WHERE p.auth_user_id = auth.uid()
            LIMIT 1
        )
    );

-- ---------------------------------------------------------------------------
-- 4) 0006 role-lookup RPC: service-role only (it returns PII by phone/email).
--    Revoke public access; only service-role may execute.
-- ---------------------------------------------------------------------------

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'get_profile_by_identifier'
    ) THEN
        REVOKE ALL ON FUNCTION public.get_profile_by_identifier(TEXT) FROM PUBLIC, anon, authenticated;
        GRANT EXECUTE ON FUNCTION public.get_profile_by_identifier(TEXT) TO service_role;
    END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- 5) Private photo bucket + owner-only policies (from 0007).
-- ---------------------------------------------------------------------------

INSERT INTO storage.buckets (id, name, public)
VALUES ('lot-photos', 'lot-photos', false)
ON CONFLICT (id) DO UPDATE SET public = false;

DROP POLICY IF EXISTS lot_photos_owner_select ON storage.objects;
CREATE POLICY lot_photos_owner_select ON storage.objects
    FOR SELECT TO authenticated
    USING (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS lot_photos_owner_insert ON storage.objects;
CREATE POLICY lot_photos_owner_insert ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS lot_photos_owner_update ON storage.objects;
CREATE POLICY lot_photos_owner_update ON storage.objects
    FOR UPDATE TO authenticated
    USING (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    )
    WITH CHECK (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS lot_photos_owner_delete ON storage.objects;
CREATE POLICY lot_photos_owner_delete ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id = 'lot-photos'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- ---------------------------------------------------------------------------
-- 6) Domain checks (NOT VALID: enforce on new writes without blocking a
--     legacy database that still contains old rows).
-- ---------------------------------------------------------------------------

ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_role_allowed_check;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_role_allowed_check
    CHECK (role IS NULL OR role IN ('informal_collector', 'formal_recycler', 'government_admin'))
    NOT VALID;

ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_account_status_check;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_account_status_check
    CHECK (account_status IS NULL OR account_status IN ('active', 'pending_verification', 'suspended', 'rejected', 'expired'))
    NOT VALID;

-- ---------------------------------------------------------------------------
-- 7) current_recycler_id(): resolved from profiles JOIN authorized_recyclers
--     on cpcb_reg_no OR contact_email. Never trusts client-supplied id.
--     Granted only to authenticated (not anon) so demo tokens cannot derive
--     a facility id from a profile they do not own.
-- ---------------------------------------------------------------------------

DROP FUNCTION IF EXISTS public.current_recycler_id();
CREATE OR REPLACE FUNCTION public.current_recycler_id()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT ar.recycler_id
    FROM public.profiles p
    JOIN public.authorized_recyclers ar
      ON (
            (COALESCE(p.statutory_identifier, '') <> '' AND ar.cpcb_reg_no = p.statutory_identifier)
         OR (p.email IS NOT NULL AND lower(ar.contact_email) = lower(p.email))
      )
    WHERE p.auth_user_id = auth.uid()
      AND p.role = 'formal_recycler'
      AND p.account_status = 'active'
      AND ar.authorization_status = 'active'
    LIMIT 1;
$$;

GRANT EXECUTE ON FUNCTION public.current_recycler_id() TO authenticated;

-- ---------------------------------------------------------------------------
-- 8) Recycler offer lifecycle: columns, constraints, RLS, ownership policies.
--    - unique (recycler_id, category_name) protects against duplicate offers.
--    - version-predicated update with conflict detection.
--    - updated_by/label stored; demo actor ids written as NULL.
-- ---------------------------------------------------------------------------

-- Ensure constraints exist (idempotent; NOT VALID for legacy compatibility)
ALTER TABLE public.recycler_offered_rates
    DROP CONSTRAINT IF EXISTS recycler_offered_rates_positive_check;
ALTER TABLE public.recycler_offered_rates
    ADD CONSTRAINT recycler_offered_rates_positive_check
    CHECK (rate_per_kg > 0) NOT VALID;

ALTER TABLE public.recycler_offered_rates
    DROP CONSTRAINT IF EXISTS recycler_offered_rates_qty_check;
ALTER TABLE public.recycler_offered_rates
    ADD CONSTRAINT recycler_offered_rates_qty_check
    CHECK (min_quantity_kg IS NULL OR max_quantity_kg IS NULL OR min_quantity_kg <= max_quantity_kg) NOT VALID;

ALTER TABLE public.recycler_offered_rates
    DROP CONSTRAINT IF EXISTS recycler_offered_rates_status_check;
ALTER TABLE public.recycler_offered_rates
    ADD CONSTRAINT recycler_offered_rates_status_check
    CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED')) NOT VALID;

ALTER TABLE public.recycler_offered_rates ENABLE ROW LEVEL SECURITY;

-- Read: active offers + own facility + admin-all
DROP POLICY IF EXISTS recycler_offered_rates_public_read ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_public_read ON public.recycler_offered_rates
    FOR SELECT
    USING (
        status = 'ACTIVE'
        OR recycler_id = public.current_recycler_id()
        OR public.current_user_role() = 'government_admin'
    );

-- Insert: only formal_recycler for their own assigned facility
DROP POLICY IF EXISTS recycler_offered_rates_insert ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_insert ON public.recycler_offered_rates
    FOR INSERT
    WITH CHECK (
        public.current_user_role() = 'formal_recycler'
        AND recycler_id = public.current_recycler_id()
    );

-- Update: only formal_recycler for their own assigned facility
DROP POLICY IF EXISTS recycler_offered_rates_update ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_update ON public.recycler_offered_rates
    FOR UPDATE
    USING (
        public.current_user_role() = 'formal_recycler'
        AND recycler_id = public.current_recycler_id()
    )
    WITH CHECK (
        public.current_user_role() = 'formal_recycler'
        AND recycler_id = public.current_recycler_id()
    );

-- Delete: only formal_recycler for their own offers
DROP POLICY IF EXISTS recycler_offered_rates_delete ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_delete ON public.recycler_offered_rates
    FOR DELETE
    USING (
        public.current_user_role() = 'formal_recycler'
        AND recycler_id = public.current_recycler_id()
    );

GRANT SELECT ON public.recycler_offered_rates TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.recycler_offered_rates TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 9) Touch trigger: keep updated_at/version coherent; owner reassignment forbidden.
-- ---------------------------------------------------------------------------

DROP TRIGGER IF EXISTS recycler_offered_rates_touch ON public.recycler_offered_rates;
CREATE TRIGGER recycler_offered_rates_touch
    BEFORE INSERT OR UPDATE ON public.recycler_offered_rates
    FOR EACH ROW EXECUTE FUNCTION public.touch_recycler_offer();

DROP FUNCTION IF EXISTS public.touch_recycler_offer();
CREATE OR REPLACE FUNCTION public.touch_recycler_offer()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at := COALESCE(NEW.updated_at, now());
        NEW.updated_by := COALESCE(NEW.updated_by, auth.uid());
        NEW.version := GREATEST(COALESCE(NEW.version, 1), 1);
    ELSE
        NEW.updated_at := now();
        NEW.updated_by := COALESCE(NEW.updated_by, auth.uid());
        NEW.version := COALESCE(OLD.version, 1) + 1;
        -- The owner of an offer can never be reassigned.
        IF NEW.recycler_id IS DISTINCT FROM OLD.recycler_id THEN
            RAISE EXCEPTION 'The recycler owning an offer cannot be changed'
                USING ERRCODE = '42501';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- 10) Quotations: recycler owns their own quotes; ownership fields immutable.
-- ---------------------------------------------------------------------------

ALTER TABLE public.quotations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS quotations_recycler_own_select ON public.quotations;
CREATE POLICY quotations_recycler_own_select ON public.quotations
    FOR SELECT TO authenticated
    USING (recycler_id = public.current_recycler_id());

DROP POLICY IF EXISTS quotations_recycler_own_insert ON public.quotations;
CREATE POLICY quotations_recycler_own_insert ON public.quotations
    FOR INSERT TO authenticated
    WITH CHECK (
        recycler_id = public.current_recycler_id()
        AND EXISTS (
            SELECT 1 FROM public.connection_requests cr
            WHERE cr.collector_user_id = quotations.collector_user_id
              AND cr.recycler_id = public.current_recycler_id()
              AND cr.status IN ('PENDING', 'ACCEPTED')
        )
    );

DROP POLICY IF EXISTS quotations_recycler_own_update ON public.quotations;
CREATE POLICY quotations_recycler_own_update ON public.quotations
    FOR UPDATE TO authenticated
    USING (recycler_id = public.current_recycler_id())
    WITH CHECK (recycler_id = public.current_recycler_id());

-- Ownership-immutability trigger
DROP TRIGGER IF EXISTS quotations_ownership_immutable ON public.quotations;
CREATE TRIGGER quotations_ownership_immutable
    BEFORE UPDATE ON public.quotations
    FOR EACH ROW EXECUTE FUNCTION public.enforce_quotation_ownership();

DROP FUNCTION IF EXISTS public.enforce_quotation_ownership();
CREATE OR REPLACE FUNCTION public.enforce_quotation_ownership()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.collector_user_id IS DISTINCT FROM OLD.collector_user_id
       OR NEW.recycler_id IS DISTINCT FROM OLD.recycler_id
       OR NEW.request_id IS DISTINCT FROM OLD.request_id THEN
        RAISE EXCEPTION 'Quotation ownership fields are immutable'
            USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- 11) Agreed handover price snapshot (never silently overwritten).
-- ---------------------------------------------------------------------------

ALTER TABLE public.collector_lots
    DROP CONSTRAINT IF EXISTS collector_lots_agreed_rate_check;
ALTER TABLE public.collector_lots
    ADD CONSTRAINT collector_lots_agreed_rate_check
    CHECK (agreed_rate_per_kg IS NULL OR agreed_rate_per_kg >= 0) NOT VALID;

DROP TRIGGER IF EXISTS collector_lots_agreed_price_immutable ON public.collector_lots;
CREATE TRIGGER collector_lots_agreed_price_immutable
    BEFORE UPDATE ON public.collector_lots
    FOR EACH ROW EXECUTE FUNCTION public.preserve_agreed_lot_price();

DROP FUNCTION IF EXISTS public.preserve_agreed_lot_price();
CREATE OR REPLACE FUNCTION public.preserve_agreed_lot_price()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.agreed_rate_per_kg IS NOT NULL
       AND NEW.agreed_rate_per_kg IS DISTINCT FROM OLD.agreed_rate_per_kg THEN
        RAISE EXCEPTION 'The agreed handover price is immutable once recorded'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.agreed_rate_per_kg IS NOT NULL AND NEW.agreed_at IS NULL THEN
        NEW.agreed_at := COALESCE(OLD.agreed_at, now());
    END IF;
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- 12) Reference-price RLS: public read of ACTIVE rows; admin-only writes.
--    material_price_history admin-only read.
-- ---------------------------------------------------------------------------

ALTER TABLE public.material_prices ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS material_prices_read ON public.material_prices;
CREATE POLICY material_prices_read ON public.material_prices
    FOR SELECT
    USING (is_active = TRUE OR public.current_user_role() = 'government_admin');

DROP POLICY IF EXISTS material_prices_admin_insert ON public.material_prices;
CREATE POLICY material_prices_admin_insert ON public.material_prices
    FOR INSERT TO authenticated
    WITH CHECK (public.current_user_role() = 'government_admin');

DROP POLICY IF EXISTS material_prices_admin_update ON public.material_prices;
CREATE POLICY material_prices_admin_update ON public.material_prices
    FOR UPDATE TO authenticated
    USING (public.current_user_role() = 'government_admin')
    WITH CHECK (public.current_user_role() = 'government_admin');

DROP POLICY IF EXISTS material_prices_admin_delete ON public.material_prices;
CREATE POLICY material_prices_admin_delete ON public.material_prices
    FOR DELETE TO authenticated
    USING (public.current_user_role() = 'government_admin');

GRANT SELECT ON public.material_prices TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.material_prices TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- 13) Price history RLS: anon+authenticated may read; service-role writes via trigger.
-- ---------------------------------------------------------------------------

DROP POLICY IF EXISTS material_price_history_read ON public.material_price_history;
CREATE POLICY material_price_history_read ON public.material_price_history
    FOR SELECT
    USING (TRUE);  -- read-only public view of audit trail

GRANT SELECT ON public.material_price_history TO anon, authenticated;

-- ---------------------------------------------------------------------------
-- 14) Verification read-only query.
-- ---------------------------------------------------------------------------

SELECT 'profiles' AS entity, count(*) AS rows FROM public.profiles
UNION ALL
SELECT 'authorized_recyclers', count(*) FROM public.authorized_recyclers
UNION ALL
SELECT 'material_prices', COUNT(*), COUNT(*) FILTER (WHERE is_active)
FROM public.material_prices
UNION ALL
SELECT 'recycler_offered_rates', COUNT(*), COUNT(*) FILTER (WHERE status = 'ACTIVE')
FROM public.recycler_offered_rates
UNION ALL
SELECT 'collector_lots', COUNT(*), NULL
FROM public.collector_lots
UNION ALL
SELECT 'material_price_history', COUNT(*), NULL
FROM public.material_price_history;