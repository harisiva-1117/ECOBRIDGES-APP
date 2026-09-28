-- ============================================================================
-- ECOBRIDGES - auto-provision profiles on signup (0002)
-- ============================================================================
-- Run this in the Supabase SQL Editor for project eibqniagoqfoxjtrbnjb,
-- AFTER supabase/schema.sql and 0001_single_source_of_truth.sql.
-- It is idempotent and safe to run more than once.
--
-- What it does:
--   The Android app registers users with Supabase Auth (email + password),
--   passing display_name / phone_number / role / entity_name /
--   statutory_identifier as user_metadata. This trigger inserts the matching
--   public.profiles row server-side in the same transaction, so signup works
--   whether or not "Confirm email" is enabled, RLS is never bypassed by the
--   client, and the app can never self-assign a role outside the allowed set.
--
-- Allowed roles: informal_collector | formal_recycler | government_admin.
-- Anything else falls back to informal_collector (the app validates first).
-- ============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role TEXT := COALESCE(NULLIF(NEW.raw_user_meta_data->>'role', ''), 'informal_collector');
BEGIN
    IF v_role NOT IN ('informal_collector', 'formal_recycler', 'government_admin') THEN
        v_role := 'informal_collector';
    END IF;

    INSERT INTO public.profiles
        (auth_user_id, role, account_status, display_name, entity_name,
         statutory_identifier, phone_number, email)
    VALUES (
        NEW.id,
        v_role,
        'active',
        COALESCE(NULLIF(NEW.raw_user_meta_data->>'display_name', ''), split_part(NEW.email, '@', 1)),
        COALESCE(NEW.raw_user_meta_data->>'entity_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'statutory_identifier', ''),
        COALESCE(NEW.raw_user_meta_data->>'phone_number', ''),
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
-- Verify: function + trigger exist.
-- ---------------------------------------------------------------------------
SELECT 'handle_new_user' AS entity,
       COUNT(*) AS functions
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.proname = 'handle_new_user';

SELECT 'on_auth_user_created' AS entity,
       COUNT(*) AS triggers
FROM pg_trigger
WHERE tgname = 'on_auth_user_created';
