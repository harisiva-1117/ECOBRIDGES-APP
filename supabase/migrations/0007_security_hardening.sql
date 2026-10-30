-- ============================================================================
-- ECOBRIDGES - Security hardening (0007)
-- ============================================================================
-- Additive + idempotent. Safe to re-run. No tables or rows are dropped.
--
-- Fixes:
--   1. Signup trigger can no longer self-assign `government_admin`, and
--      formal recyclers start as `pending_verification` (never `active`).
--   2. `profiles` INSERT/UPDATE policies can no longer change role or
--      account status; only an administrator / service role can verify.
--   3. `current_user_role()` only returns a privileged role for ACTIVE
--      accounts, and only returns `formal_recycler` when an active CPCB
--      registry record matches the profile. Every existing RLS policy that
--      uses this helper is therefore tightened in one place.
--   4. 0006's `get_profile_by_identifier()` is no longer executable by
--      `anon`/`authenticated` (it leaked profile/role data by phone/email).
--   5. Private `lot-photos` storage bucket + owner-only object policies.
--   6. CHECK constraints for role / account_status (NOT VALID so existing
--      databases are not blocked; new writes are enforced).
--
-- ROLLBACK: see docs/SUPABASE_SETUP.md (re-apply 0001/0002/0004 definitions,
-- drop the lot-photos policies and the two helper functions).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) Allow profiles without a phone number to exist in `pending_verification`
--    (e.g. Google sign-up). The app must complete the profile before use.
-- ---------------------------------------------------------------------------
ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_phone_number_india_format;
ALTER TABLE public.profiles
    DROP CONSTRAINT IF EXISTS profiles_phone_number_check;
ALTER TABLE public.profiles
    ALTER COLUMN phone_number DROP NOT NULL;
ALTER TABLE public.profiles
    ADD CONSTRAINT profiles_phone_number_india_format
    CHECK (phone_number IS NULL OR phone_number ~ '^\+91 [6-9][0-9]{9}$');

-- ---------------------------------------------------------------------------
-- 1) Hardened signup trigger.
--    - informal_collector  -> active
--    - formal_recycler     -> pending_verification (admin must verify)
--    - anything else       -> demoted to informal_collector
--    - missing/invalid phone -> pending_verification, phone_number NULL
-- ---------------------------------------------------------------------------
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
    -- Only these two roles may be requested by a client. `government_admin`
    -- (or any unknown value) is never granted at signup: an administrator must
    -- be provisioned with the service-role key / Supabase dashboard.
    IF v_requested_role IN ('informal_collector', 'formal_recycler') THEN
        v_role := v_requested_role;
    ELSE
        v_role := 'informal_collector';
    END IF;

    IF length(v_phone_digits) = 12 AND left(v_phone_digits, 2) = '91' THEN
        v_phone_digits := right(v_phone_digits, 10);
    END IF;
    IF v_phone_digits ~ '^[6-9][0-9]{9}$' THEN
        v_phone := '+91 ' || v_phone_digits;
    ELSE
        v_phone := NULL;
    END IF;

    -- Recyclers are unverified until an administrator approves them. A
    -- self-supplied CPCB number is stored for review but never treated as
    -- proof of authorization.
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

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ---------------------------------------------------------------------------
-- 2) Role helper: active accounts only; recyclers must match an active
--    CPCB registry record. Any other state resolves to a non-privileged slug.
-- ---------------------------------------------------------------------------
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
-- 3) Profiles policies: insert is limited to the two self-signup roles and a
--    non-verified status; update cannot alter role/status/ownership.
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
-- 5) Private photo bucket + owner-only policies.
--    Object path convention: {auth.uid()}/{photoId}.jpg
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
--    legacy database that still contains old rows).
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
-- 7) Verification query (read-only).
-- ---------------------------------------------------------------------------
SELECT 'profiles' AS entity, count(*) AS rows FROM public.profiles
UNION ALL
SELECT 'authorized_recyclers', count(*) FROM public.authorized_recyclers
UNION ALL
SELECT 'lot_photos_bucket', count(*) FROM storage.buckets WHERE id = 'lot-photos';
