# ECOBRIDGES — Supabase Setup

**Project (authoritative):** `ekqatebpbvxwjohfhipf`
**URL:** `https://ekqatebpbvxwjohfhipf.supabase.co`

> The previous project (`eibqniagoqfoxjtrbnjb`) no longer resolves (DNS NXDOMAIN).
> Do not reuse its credentials. All scripts below target the new project.

---

## 1. Prerequisites

- Access to the Supabase dashboard for `ekqatebpbvxwjohfhipf`.
- The **service role / secret key** (server-only; never in the app or git).
- No CLI is required — the SQL Editor is sufficient.

---

## 2. Apply the schema (order matters)

Run each file completely, in this exact order, in **SQL Editor → New query**:

| # | File | What it does |
| --- | --- | --- |
| 1 | `supabase/schema.sql` | 12 base tables, initial RLS, indexes |
| 2 | `supabase/migrations/0001_single_source_of_truth.sql` | profile normalisation, `current_user_role()`, role-aware policies, views, seed cleanup |
| 3 | `supabase/migrations/0002_auth_profile_trigger.sql` | auto-provision `profiles` on signup |
| 4 | `supabase/migrations/0003_manifest_details.sql` | lot manifest columns |
| 5 | `supabase/migrations/0004_require_mobile_number.sql` | phone validation in the trigger |
| 6 | `supabase/migrations/0005_recycler_dataset.sql` | CPCB registry + offered rates |
| 7 | `supabase/migrations/0006_role_validation_rpc.sql` | `get_profile_by_identifier()` (locked down by 0007) |
| 8 | `supabase/migrations/0007_security_hardening.sql` | **this audit** — trigger demotion, role tightening, storage policies, constraints |
| 9 | `supabase/migrations/0008_price_management.sql` | price columns/constraints, `material_price_history` + trigger, `current_recycler_id()`, offer ownership RLS, quotation/agreed-price immutability |

All scripts are idempotent (`IF NOT EXISTS`, `CREATE OR REPLACE`, guarded `DO`
blocks). Re-running them is safe.

### Verify

```sql
-- Tables and RLS
select tablename, rowsecurity
from pg_tables
where schemaname = 'public'
order by tablename;

-- Views
select table_name from information_schema.views
where table_schema = 'public' order by table_name;

-- Role helper exists and is security definer
select proname, prosecdef, proacl
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and proname = 'current_user_role';

-- Signup trigger attached
select tgname, tgenabled from pg_trigger
where tgrelid = 'auth.users'::regclass and not tgisinternal;

-- Private bucket
select id, name, public from storage.buckets where id = 'lot-photos';

-- Role RPC must NOT be executable by anon/authenticated
select has_function_privilege('anon', 'public.get_profile_by_identifier(text)', 'execute') as anon_can_execute,
       has_function_privilege('authenticated', 'public.get_profile_by_identifier(text)', 'execute') as auth_can_execute;

-- Price management (0008)
select column_name, data_type
from information_schema.columns
where table_name = 'material_prices'
order by ordinal_position;

select column_name from information_schema.columns
where table_name = 'recycler_offered_rates' and column_name in ('version','min_quantity_kg','max_quantity_kg');

select has_table_privilege('authenticated', 'public.material_price_history', 'insert') as auth_can_insert_history;

select polname, polcmd from pg_policy
where polrelid in ('public.material_prices'::regclass, 'public.recycler_offered_rates'::regclass);
```

Expected: every public table has `rowsecurity = true`; `v_*` views exist;
`anon_can_execute = false`; `auth_can_execute = false`; `lot-photos.public = false`.

`0007` also ends with a small row-count query for `profiles`,
`authorized_recyclers` and the bucket.

---

## 3. Auth settings

Supabase Dashboard → **Authentication → Providers → Email**:

| Setting | Recommendation |
| --- | --- |
| Enable Email provider | ON |
| Confirm email | OFF for the SIH demo; **ON** for production |
| Secure email change | ON |
| Minimum password length | ≥ 8 |
| Leaked password protection | ON (if available) |

Dashboard → **Authentication → URL Configuration**: set the site URL and any
redirect URLs you actually use. The Android app uses password and Google ID-token
flows, not magic links, so no deep-link redirect is required.

### Google sign-in

1. Create an OAuth 2.0 **Web** client in Google Cloud Console.
2. Put the client ID in the app `.env` as `GOOGLE_WEB_CLIENT_ID`.
3. Enable Google under Authentication → Providers and paste the same client ID
   (and secret, server-side only).
4. Add the app's SHA-1 (debug and release) to the Android OAuth client if you
   also use native Google Sign-In.

---

## 4. Creating the first administrator

`government_admin` can **never** be self-assigned. Provision it manually:

1. Create the user via Dashboard → Authentication → Users (or let them sign up
   as a collector).
2. Promote the profile with the service role (SQL Editor runs as postgres, which
   bypasses RLS):

```sql
update public.profiles
set role = 'government_admin',
    account_status = 'active'
where email = 'admin@your-domain.example';
```

3. Verify:

```sql
select email, role, account_status from public.profiles where email = 'admin@your-domain.example';
```

Never ship the service-role key to the client. There is no in-app admin
elevation path by design.

---

## 5. Verifying recycler accounts

Self-registered recyclers are created with `account_status = 'pending_verification'`
and are treated as `unverified_recycler` by RLS until **both**:

- `profiles.account_status = 'active'`, and
- an `authorized_recyclers` row with `authorization_status = 'active'` matches the
  profile's `statutory_identifier` (CPCB reg. no.) or `email`.

Approval:

```sql
-- 1. Confirm the CPCB registry row exists and is active
select cpcb_reg_no, recycler_name, authorization_status, contact_email
from public.authorized_recyclers
where cpcb_reg_no = 'CPCB-XXXXXXX';

-- 2. Activate the account
update public.profiles
set account_status = 'active'
where email = 'recycler@example.com'
  and role = 'formal_recycler';
```

If the registry row is missing, insert it first (service role) rather than
loosening RLS.

---

## 6. Storage

`0007` creates the private `lot-photos` bucket and four owner-only policies.

Path convention: `{auth.uid()}/{photoId}.jpg`.

- Do **not** make the bucket public.
- Do not add a policy that lets recyclers/admins read photos yet; if that is
  needed, add a policy that checks `current_user_role()` and the lot ownership,
  not a blanket `authenticated` rule.

Verify a denied cross-user read with two test accounts before launch.

---

## 7. Rollback

`0007` is additive. To roll back:

```sql
-- 1. Restore the previous trigger behaviour
--    Re-run 0004_require_mobile_number.sql (it recreates handle_new_user()).

-- 2. Restore the previous role helper
--    Re-run 0001_single_source_of_truth.sql (it recreates current_user_role()).

-- 3. Remove the storage policies and bucket (destroys uploaded photos)
drop policy if exists lot_photos_owner_select on storage.objects;
drop policy if exists lot_photos_owner_insert on storage.objects;
drop policy if exists lot_photos_owner_update on storage.objects;
drop policy if exists lot_photos_owner_delete on storage.objects;
delete from storage.buckets where id = 'lot-photos';

-- 4. Re-grant the role RPC if you really need the old behaviour
--    (not recommended: it leaks profile PII)
-- grant execute on function public.get_profile_by_identifier(text) to anon, authenticated;

-- 5. Drop the domain constraints
alter table public.profiles drop constraint if exists profiles_role_allowed_check;
alter table public.profiles drop constraint if exists profiles_account_status_check;
```

Rolling back `0007` re-opens the self-assigned-admin and unverified-recycler
issues. Prefer fixing forward.

### Rolling back `0008`

`0008` adds columns, one table (`material_price_history`), triggers and RLS.
Before undoing it, export `material_price_history` (it is the audit trail). Then:

```sql
drop trigger if exists trg_material_prices_history on public.material_prices;
drop trigger if exists trg_recycler_rates_touch on public.recycler_offered_rates;
drop function if exists public.log_material_price_history();
drop function if exists public.current_recycler_id();
drop table if exists public.material_price_history;
alter table public.collector_lots
  drop column if exists agreed_rate_per_kg,
  drop column if exists agreed_at;
-- Remaining added columns are safe to keep; drop them only with a tested downgrade.
```

This destroys price history and removes the agreed-rate immutability guard.
Do not run it on a live database without a backup and owner approval.

---

## 8. Common problems

| Symptom | Cause | Fix |
| --- | --- | --- |
| `relation "public.profiles" does not exist` | base schema not applied | run `schema.sql` first |
| Sign-up succeeds but no `profiles` row | trigger missing | re-run `0002`, then `0007` |
| Recycler sees an empty dashboard | account pending or registry mismatch | see §5 |
| `permission denied for function get_profile_by_identifier` | expected after `0007` | call it only with the service role |
| Storage upload 403 | path not prefixed with the user's UUID | use `{auth.uid()}/...` |
| Everything 404/401 | project ref is the old dead one | use `ekqatebpbvxwjohfhipf` |
