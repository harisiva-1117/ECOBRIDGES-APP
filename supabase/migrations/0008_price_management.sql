-- ============================================================================
-- ECOBRIDGES - Editable e-waste price management (0008)
-- ============================================================================
-- Additive, idempotent, non-destructive. Requires 0007_security_hardening.sql
-- (uses public.current_user_role()). Do NOT run against production without a
-- backup and a reviewed change window.
--
-- What it adds:
--   1. Admin-managed reference prices on the EXISTING `material_prices` table:
--      grade, currency, active flag, effective date, updated-by/at, version.
--   2. Immutable price history (`material_price_history`) written by trigger on
--      every insert/update, so historical transactions keep their agreed price.
--   3. RLS: everyone may read active reference prices; only an ACTIVE
--      government_admin (server-derived role) may create/update/deactivate.
--   4. Recycler purchase offers on the EXISTING `recycler_offered_rates` table:
--      grade, quantity limits, status, timestamps, version + ownership RLS via
--      `current_recycler_id()` (resolved from the CPCB registry, never from a
--      client-supplied id).
--   5. Recyclers may read/insert/update their OWN quotations but never another
--      recycler's; ownership columns are immutable.
--   6. `collector_lots.agreed_rate_per_kg` / `agreed_at` snapshot the handover
--      price and cannot be overwritten once set.
--
-- Rollback: see docs/SUPABASE_SETUP.md (drop the new triggers/functions,
-- history table and policies; the added columns are harmless if left in place).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) Reference-price columns on the existing table.
--    `category_name` stays the canonical MaterialCategory slug and
--    `sub_category` stays the human material name. `market_min` / `market_max`
--    are the minimum / maximum reference prices.
-- ---------------------------------------------------------------------------
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS grade TEXT;
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'INR';
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS updated_by_label TEXT;
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.material_prices
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;

UPDATE public.material_prices
SET updated_at = COALESCE(updated_at, now())
WHERE updated_at IS NULL;

UPDATE public.material_prices
SET effective_date = COALESCE(effective_date, CURRENT_DATE)
WHERE effective_date IS NULL;

-- Validation. NOT VALID so an older database with legacy rows is not blocked;
-- every new write is checked.
ALTER TABLE public.material_prices
    DROP CONSTRAINT IF EXISTS material_prices_non_negative_check;
ALTER TABLE public.material_prices
    ADD CONSTRAINT material_prices_non_negative_check
    CHECK (
        (prevailing_buy_rate IS NULL OR prevailing_buy_rate >= 0)
        AND (market_min IS NULL OR market_min >= 0)
        AND (market_max IS NULL OR market_max >= 0)
        AND (trend_percentage IS NULL OR trend_percentage >= 0)
    ) NOT VALID;

ALTER TABLE public.material_prices
    DROP CONSTRAINT IF EXISTS material_prices_min_max_check;
ALTER TABLE public.material_prices
    ADD CONSTRAINT material_prices_min_max_check
    CHECK (
        market_min IS NULL OR market_max IS NULL OR market_min <= market_max
    ) NOT VALID;

-- ---------------------------------------------------------------------------
-- 2) Immutable history. Written by a SECURITY DEFINER trigger so it cannot be
--    skipped by a client that updates through PostgREST.
-- ---------------------------------------------------------------------------
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
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    snapshot            JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS material_price_history_price_idx
    ON public.material_price_history (price_id, changed_at DESC);

ALTER TABLE public.material_price_history ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS material_price_history_admin_read ON public.material_price_history;
CREATE POLICY material_price_history_admin_read ON public.material_price_history
    FOR SELECT TO authenticated
    USING (public.current_user_role() = 'government_admin');

GRANT SELECT ON public.material_price_history TO authenticated;

CREATE OR REPLACE FUNCTION public.log_material_price_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.version := GREATEST(COALESCE(NEW.version, 1), 1);
        NEW.updated_at := COALESCE(NEW.updated_at, now());
        NEW.updated_by := COALESCE(NEW.updated_by, auth.uid());
        INSERT INTO public.material_price_history (
            price_id, category_name, sub_category, grade, location,
            prevailing_buy_rate, market_min, market_max, currency,
            effective_date, is_active, version, change_type,
            changed_by, changed_by_label, snapshot
        ) VALUES (
            NEW.price_id, NEW.category_name, NEW.sub_category, NEW.grade, NEW.location,
            NEW.prevailing_buy_rate, NEW.market_min, NEW.market_max, NEW.currency,
            NEW.effective_date, NEW.is_active, NEW.version, 'CREATE',
            NEW.updated_by, NEW.updated_by_label, to_jsonb(NEW)
        );
        RETURN NEW;
    END IF;

    NEW.version := COALESCE(OLD.version, 1) + 1;
    NEW.updated_at := now();
    NEW.updated_by := COALESCE(NEW.updated_by, auth.uid());

    INSERT INTO public.material_price_history (
        price_id, category_name, sub_category, grade, location,
        prevailing_buy_rate, market_min, market_max, currency,
        effective_date, is_active, version, change_type,
        changed_by, changed_by_label, snapshot
    ) VALUES (
        NEW.price_id, NEW.category_name, NEW.sub_category, NEW.grade, NEW.location,
        NEW.prevailing_buy_rate, NEW.market_min, NEW.market_max, NEW.currency,
        NEW.effective_date, NEW.is_active, NEW.version,
        CASE WHEN NEW.is_active = FALSE AND OLD.is_active = TRUE THEN 'DEACTIVATE' ELSE 'UPDATE' END,
        NEW.updated_by, NEW.updated_by_label, to_jsonb(NEW)
    );
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS material_prices_history_trigger ON public.material_prices;
CREATE TRIGGER material_prices_history_trigger
    BEFORE INSERT OR UPDATE ON public.material_prices
    FOR EACH ROW EXECUTE FUNCTION public.log_material_price_change();

-- ---------------------------------------------------------------------------
-- 3) Reference-price RLS: public read of ACTIVE rows; admin-only writes.
-- ---------------------------------------------------------------------------
ALTER TABLE public.material_prices ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS material_prices_read ON public.material_prices;
CREATE POLICY material_prices_read ON public.material_prices
    FOR SELECT
    USING (
        is_active = TRUE
        OR public.current_user_role() = 'government_admin'
    );

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
GRANT SELECT, INSERT, UPDATE, DELETE ON public.material_prices TO authenticated;

-- ---------------------------------------------------------------------------
-- 4) Recycler offer columns + ownership helper.
-- ---------------------------------------------------------------------------
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS grade TEXT;
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS condition TEXT;
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS min_quantity_kg DOUBLE PRECISION;
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS max_quantity_kg DOUBLE PRECISION;
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS updated_by UUID;
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS updated_by_label TEXT;
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.recycler_offered_rates
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;

UPDATE public.recycler_offered_rates
SET updated_at = COALESCE(updated_at, now())
WHERE updated_at IS NULL;

ALTER TABLE public.recycler_offered_rates
    DROP CONSTRAINT IF EXISTS recycler_offered_rates_price_check;
ALTER TABLE public.recycler_offered_rates
    ADD CONSTRAINT recycler_offered_rates_price_check
    CHECK (rate_per_kg >= 0) NOT VALID;

ALTER TABLE public.recycler_offered_rates
    DROP CONSTRAINT IF EXISTS recycler_offered_rates_quantity_check;
ALTER TABLE public.recycler_offered_rates
    ADD CONSTRAINT recycler_offered_rates_quantity_check
    CHECK (
        (min_quantity_kg IS NULL OR min_quantity_kg >= 0)
        AND (max_quantity_kg IS NULL OR max_quantity_kg >= 0)
        AND (min_quantity_kg IS NULL OR max_quantity_kg IS NULL OR min_quantity_kg <= max_quantity_kg)
    ) NOT VALID;

ALTER TABLE public.recycler_offered_rates
    DROP CONSTRAINT IF EXISTS recycler_offered_rates_status_check;
ALTER TABLE public.recycler_offered_rates
    ADD CONSTRAINT recycler_offered_rates_status_check
    CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED')) NOT VALID;

-- Resolve the caller's own CPCB registry row. Never trust a client-supplied
-- recycler_id: the app may only write rows that this function returns.
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

ALTER TABLE public.recycler_offered_rates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS recycler_offered_rates_public_read ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_public_read ON public.recycler_offered_rates
    FOR SELECT
    USING (
        status = 'ACTIVE'
        OR public.current_user_role() = 'government_admin'
        OR recycler_id = public.current_recycler_id()
    );

DROP POLICY IF EXISTS recycler_offered_rates_owner_insert ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_owner_insert ON public.recycler_offered_rates
    FOR INSERT TO authenticated
    WITH CHECK (
        public.current_user_role() = 'formal_recycler'
        AND recycler_id = public.current_recycler_id()
    );

DROP POLICY IF EXISTS recycler_offered_rates_owner_update ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_owner_update ON public.recycler_offered_rates
    FOR UPDATE TO authenticated
    USING (recycler_id = public.current_recycler_id())
    WITH CHECK (
        public.current_user_role() = 'formal_recycler'
        AND recycler_id = public.current_recycler_id()
    );

DROP POLICY IF EXISTS recycler_offered_rates_owner_delete ON public.recycler_offered_rates;
CREATE POLICY recycler_offered_rates_owner_delete ON public.recycler_offered_rates
    FOR DELETE TO authenticated
    USING (recycler_id = public.current_recycler_id());

GRANT SELECT ON public.recycler_offered_rates TO anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.recycler_offered_rates TO authenticated;

-- Keep updated_at / version coherent for offers too.
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

DROP TRIGGER IF EXISTS recycler_offered_rates_touch ON public.recycler_offered_rates;
CREATE TRIGGER recycler_offered_rates_touch
    BEFORE INSERT OR UPDATE ON public.recycler_offered_rates
    FOR EACH ROW EXECUTE FUNCTION public.touch_recycler_offer();

-- ---------------------------------------------------------------------------
-- 5) Quotations: recycler owns their own quotes, but cannot edit others'.
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

DROP TRIGGER IF EXISTS quotations_ownership_immutable ON public.quotations;
CREATE TRIGGER quotations_ownership_immutable
    BEFORE UPDATE ON public.quotations
    FOR EACH ROW EXECUTE FUNCTION public.enforce_quotation_ownership();

-- ---------------------------------------------------------------------------
-- 6) Agreed handover price snapshot (never silently overwritten).
-- ---------------------------------------------------------------------------
ALTER TABLE public.collector_lots
    ADD COLUMN IF NOT EXISTS agreed_rate_per_kg DOUBLE PRECISION;
ALTER TABLE public.collector_lots
    ADD COLUMN IF NOT EXISTS agreed_at TIMESTAMPTZ;

ALTER TABLE public.collector_lots
    DROP CONSTRAINT IF EXISTS collector_lots_agreed_rate_check;
ALTER TABLE public.collector_lots
    ADD CONSTRAINT collector_lots_agreed_rate_check
    CHECK (agreed_rate_per_kg IS NULL OR agreed_rate_per_kg >= 0) NOT VALID;

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

DROP TRIGGER IF EXISTS collector_lots_agreed_price_immutable ON public.collector_lots;
CREATE TRIGGER collector_lots_agreed_price_immutable
    BEFORE UPDATE ON public.collector_lots
    FOR EACH ROW EXECUTE FUNCTION public.preserve_agreed_lot_price();

-- ---------------------------------------------------------------------------
-- 7) Verification query (read-only).
-- ---------------------------------------------------------------------------
SELECT 'material_prices' AS entity, COUNT(*) AS rows,
       COUNT(*) FILTER (WHERE is_active) AS active
FROM public.material_prices
UNION ALL
SELECT 'material_price_history', COUNT(*), NULL
FROM public.material_price_history
UNION ALL
SELECT 'recycler_offered_rates', COUNT(*),
       COUNT(*) FILTER (WHERE status = 'ACTIVE')
FROM public.recycler_offered_rates;
