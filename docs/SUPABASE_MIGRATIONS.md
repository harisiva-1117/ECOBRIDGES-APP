# ECOBRIDGES — Supabase Database Migrations & Audit

## 1. Migration Overview & Safe Application Order
Target Supabase Project ID: `ekqatebpbvxwjohfhipf`

**Critical Principle:** Migrations `0007` and `0008` must **not** be skipped. All migrations are strictly additive, non-destructive, and forward-only.

### Verified Safe Execution Order:
1. `0001_single_source_of_truth.sql` (Historical baseline schema)
2. `0002_auth_profile_trigger.sql` (Historical profile provisioning)
3. `0003_manifest_details.sql` (Historical manifests)
4. `0004_require_mobile_number.sql` (Historical constraints)
5. `0005_recycler_dataset.sql` (Historical authorized recycler registry & initial offers)
6. `0006_role_validation_rpc.sql` (Historical role RPCs)
7. `0007_security_hardening.sql` (Hardens signup trigger, creates private storage bucket `lot-photos`, tightens role policies)
8. `0008_price_management.sql` (Adds baseline price management columns and policies)
9. `0009_cloud_sync_and_demo_security.sql` (Adds full offer lifecycle columns, dual statutory-to-facility identifier support in `current_user_role()`, immutable price history triggers, handover immutability triggers, and demo profiles)

---

## 2. In-Depth Audit Findings for Migration 0009

### 2.1 Role Resolution & Recycler Ownership Compatibility
* **Issue Discovered during Audit:** In migration `0007`, `public.current_user_role()` checked `ar.cpcb_reg_no = p.statutory_identifier` against `authorized_recyclers`. In seed migration `0005`, the recycler's statutory registration is `CPCB/EPR-REC/2023/MH-0042` while its facility ID is `REC-CPCB-MH-001`. If a user or demo profile used `REC-CPCB-MH-001`, `0007`'s function alone would return `unverified_recycler`!
* **Resolution in Migration 0009:** Migration `0009` now defines `public.current_user_role()` with dual-matching:
  ```sql
  WHERE ar.authorization_status = 'active'
    AND (
          (p.statutory_identifier <> '' AND (ar.cpcb_reg_no = p.statutory_identifier OR ar.recycler_id = p.statutory_identifier))
       OR (p.email IS NOT NULL AND lower(ar.contact_email) = lower(p.email))
    )
  ```
  This guarantees that whether a user or JWT identifies by CPCB statutory registration number or by facility identifier, role authorization succeeds.

### 2.2 Facility Ownership Helper
* `public.current_recycler_id()` returns the caller's statutory identifier from `public.profiles` for active formal recyclers.
* RLS policies on `public.recycler_offered_rates` enforce that `recycler_id = public.current_recycler_id()` on INSERT, UPDATE, and DELETE. A recycler can never tamper with another facility's rates.

### 2.3 Idempotency and Non-Destructive Design
* All columns use `ADD COLUMN IF NOT EXISTS`.
* Constraints are dropped before re-addition (`DROP CONSTRAINT IF EXISTS ... ADD CONSTRAINT ... NOT VALID`).
* Policies are dropped before creation (`DROP POLICY IF EXISTS`).
* Existing rows are backfilled with default values before NOT NULL or CHECK constraints are activated.

### 2.4 Trigger Safety & No Infinite Recursion
* `trg_material_prices_history`:
  - Fires `BEFORE INSERT OR UPDATE ON public.material_prices`.
  - Increments `version` and writes an audit row to `public.material_price_history`.
  - Does NOT update `material_prices` itself within the trigger body, eliminating recursion risks.
* `trg_collector_lots_handover_immutability`:
  - Fires `BEFORE UPDATE ON public.collector_lots`.
  - Raises an exception if `agreed_rate_per_kg` is modified once set, or if `recycler_confirmed` is changed from `TRUE` to `FALSE`.

---

## 3. Manual Steps for the Supabase Project `ekqatebpbvxwjohfhipf`

**Do not run migrations automatically.** Execute them manually through the Supabase Dashboard:

1. Open the [Supabase Project Dashboard](https://supabase.com/dashboard/project/ekqatebpbvxwjohfhipf).
2. Go to the **SQL Editor**.
3. If migrations `0007` and `0008` have not yet been executed on this project:
   - Open and run `supabase/migrations/0007_security_hardening.sql`.
   - Open and run `supabase/migrations/0008_price_management.sql`.
4. Open `supabase/migrations/0009_cloud_sync_and_demo_security.sql`.
5. Paste the entire SQL script and click **Run**.
6. Verify output confirms: `'0009_cloud_sync_and_demo_security applied successfully'`.
