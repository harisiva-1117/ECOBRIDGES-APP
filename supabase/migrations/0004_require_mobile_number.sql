-- Require a valid mobile number for every collector, recycler and admin.
-- Run after 0002_auth_profile_trigger.sql.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.profiles
        WHERE phone_number IS NULL
           OR phone_number !~ '^\+91 [6-9][0-9]{9}$'
    ) THEN
        RAISE EXCEPTION
            'Update existing profiles.phone_number to +91 XXXXXXXXXX before enforcing this rule.';
    END IF;
END;
$$;

ALTER TABLE public.profiles ALTER COLUMN phone_number SET NOT NULL;
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_phone_number_india_format;
ALTER TABLE public.profiles ADD CONSTRAINT profiles_phone_number_india_format
    CHECK (phone_number ~ '^\+91 [6-9][0-9]{9}$');

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role TEXT := COALESCE(NULLIF(NEW.raw_user_meta_data->>'role', ''), 'informal_collector');
    v_phone_digits TEXT := regexp_replace(COALESCE(NEW.raw_user_meta_data->>'phone_number', ''), '\D', '', 'g');
    v_phone TEXT;
BEGIN
    IF v_role NOT IN ('informal_collector', 'formal_recycler', 'government_admin') THEN
        v_role := 'informal_collector';
    END IF;
    IF length(v_phone_digits) = 12 AND left(v_phone_digits, 2) = '91' THEN
        v_phone_digits := right(v_phone_digits, 10);
    END IF;
    IF v_phone_digits !~ '^[6-9][0-9]{9}$' THEN
        RAISE EXCEPTION 'A valid 10-digit Indian mobile number is required for every account';
    END IF;
    v_phone := '+91 ' || v_phone_digits;

    INSERT INTO public.profiles
        (auth_user_id, role, account_status, display_name, entity_name,
         statutory_identifier, phone_number, email)
    VALUES (
        NEW.id, v_role, 'active',
        COALESCE(NULLIF(NEW.raw_user_meta_data->>'display_name', ''), split_part(NEW.email, '@', 1)),
        COALESCE(NEW.raw_user_meta_data->>'entity_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'statutory_identifier', ''),
        v_phone, NEW.email
    ) ON CONFLICT (auth_user_id) DO NOTHING;
    RETURN NEW;
END;
$$;
