-- ============================================================================
-- ECOBRIDGES - Role Validation & Single Role Enforcement (0006)
-- ============================================================================
-- This migration provides a secure server-side RPC function to look up a user's
-- registered role and profile by their registered email or phone number.
-- It enables strict cross-role authentication blocking on both frontend and backend.
-- ============================================================================

-- Fast lookup indexes on profiles
CREATE INDEX IF NOT EXISTS profiles_email_lower_idx ON public.profiles (lower(email));
CREATE INDEX IF NOT EXISTS profiles_phone_number_idx ON public.profiles (phone_number);

-- Secure role lookup function
CREATE OR REPLACE FUNCTION public.get_profile_by_identifier(p_identifier TEXT)
RETURNS TABLE (
    auth_user_id UUID,
    role TEXT,
    account_status TEXT,
    display_name TEXT,
    entity_name TEXT,
    statutory_identifier TEXT,
    phone_number TEXT,
    email TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_clean_digits TEXT := right(regexp_replace(COALESCE(p_identifier, ''), '\D', '', 'g'), 10);
    v_clean_email TEXT := lower(trim(COALESCE(p_identifier, '')));
BEGIN
    RETURN QUERY
    SELECT 
        p.auth_user_id,
        p.role,
        p.account_status,
        p.display_name,
        p.entity_name,
        p.statutory_identifier,
        p.phone_number,
        p.email
    FROM public.profiles p
    WHERE (v_clean_email <> '' AND lower(COALESCE(p.email, '')) = v_clean_email)
       OR (length(v_clean_digits) = 10 AND right(regexp_replace(COALESCE(p.phone_number, ''), '\D', '', 'g'), 10) = v_clean_digits)
    LIMIT 1;
END;
$$;

-- Grant execution permissions
GRANT EXECUTE ON FUNCTION public.get_profile_by_identifier(TEXT) TO anon, authenticated, service_role;
