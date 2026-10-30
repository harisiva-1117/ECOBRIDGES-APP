# ECOBRIDGES — Demo, Price Management and Local Run Guide

**Audience:** the project owner preparing the SIH 2026 demonstration and the
manual deployment that follows it.
**Scope:** what runs locally today, how to configure it, and what still needs a
real backend/database.

> Nothing in this guide deploys anything or applies a migration. Every database
> step is manual and explicitly marked.

---

## A. Start the Android app locally

Prerequisites: JDK 17+, Android SDK (compile SDK 36), the Gradle wrapper in the
repo. `local.properties` must point at your SDK.

```powershell
cd "D:\SIH APP PROJECT\google-sih"
.\gradlew.bat :app:installDebug        # installs on a connected device/emulator
# or:
.\gradlew.bat :app:assembleDebug       # produces the APK without a device
```

Open `app/build/outputs/apk/debug/app-debug.apk` on the phone, or use Android
Studio's Run button.

Room starts empty of business records but **does** contain a fixed list of four
authorised recycler facilities (`EwasteRepository`, "fallback seed for offline /
cold-start map rendering") plus sample prices shown on the collector Home and
Earnings cards. Those are illustrative local data, not cloud records. Everything
else in Room is populated from Supabase after a real sign-in. A demo session has
no JWT, so it shows the local seed plus whatever it creates itself. That is
intentional.

---

## B. Demo login configuration

Demo login is a build-time option, not a fallback and not production auth.

| Key | Debug default | Release default | Meaning |
| --- | --- | --- | --- |
| `DEMO_LOGIN_ENABLED` | `true` | `false` | Master switch |
| `DEMO_LOGIN_ALLOW_RELEASE` | forced `false` | `false` | Must be `true` **and** `DEMO_LOGIN_ENABLED=true` for a release build |
| `DEMO_OTP_CODE` | `123456` | `123456` | Fixed on-device code (6 digits) |
| `DEMO_ENABLE_ADMIN` | `true` | `false` | Enables the admin demo portal |
| `DEMO_ACCOUNTS` | three sample numbers | same | `phone:role` pairs, comma-separated |

Put overrides in the app `.env` (untracked) or pass `-P` Gradle properties.
Do **not** put these in a committed file.

Example `.env` (safe, non-secret):

```dotenv
DEMO_LOGIN_ENABLED=true
DEMO_OTP_CODE=123456
DEMO_ENABLE_ADMIN=true
DEMO_ACCOUNTS=9876543210:collector,9876543211:recycler,9876543212:admin
```

Release builds only honour demo when **both** `DEMO_LOGIN_ENABLED=true` and
`DEMO_LOGIN_ALLOW_RELEASE=true`. Leave both unset for anything public.

---

## C. How to use the demo OTP

1. Choose a role on the login screen (Collector / Recycler / Admin).
2. Enter a 10-digit mobile number.
3. Tap **Send OTP**. No SMS is sent; the screen displays
   `Demo OTP: 123456` and a banner saying this is not identity verification.
4. Enter the code and sign in.

Wrong codes are rejected. The app never claims an SMS was delivered, and the
demo session never receives a Supabase JWT.

---

## D. Configure the three demo roles

`DEMO_ACCOUNTS` entries look like `phone:role` where role is
`collector`, `recycler` or `admin`.

- **Configured number + matching role** → opens that dashboard.
- **Configured number + different role** → rejected.
- **Unlisted number** → may open collector or recycler demo portals, never admin.
- **Admin** → requires `DEMO_ENABLE_ADMIN=true` **and** the number to be listed
  as `admin`. There is no way for a client to promote itself.

The same `DEMO_ACCOUNTS` string is documented in `app/.env.example`.

---

## E. Enable the admin demo safely

1. Set `DEMO_ENABLE_ADMIN=true`.
2. Add exactly one number to `DEMO_ACCOUNTS` with `:admin`.
3. Build a **debug** APK.
4. Do not set `DEMO_LOGIN_ALLOW_RELEASE=true` for this build.

The admin demo identity looks like `DEMO-...NOT-AUTHORISED...`, never a CPCB
number, and it cannot call privileged Supabase/NestJS endpoints because it has no
token. Admin demo is for screen walkthroughs only.

---

## F. Manage reference prices (admin)

Navigation: **Admin dashboard → Price Management**.

- Search by material; filter by category/status; sort by price or last update.
- **Add** opens the editor: material, category, ₹/kg, min, max, grade, currency,
  effective date, active flag.
- **Edit** validates non-negative values and `min <= max`.
- A change of **≥ 20%** shows a confirmation dialog.
- **Deactivate** keeps the record and its history but removes it from the
  collector board.
- **History** lists every change with the actor and timestamp.
- Validation, empty, success and error states are shown inline.

Writes are local-first. A real authenticated admin queues a versioned change for
sync; a demo admin's change is local-only and is never queued.

---

## G. Create and update recycler offers

Navigation: **Recycler portal → My Offers**.

- Create one offer per category (enforced by policy and the database).
- Set ₹/kg, material, grade, condition, quantity limits and status
  (`DRAFT`, `ACTIVE`, `PAUSED`, `EXPIRED`).
- Edit or pause only **your own** offers; another facility's offer is refused
  locally and by `current_recycler_id()` RLS server-side.
- Recyclers cannot edit government reference prices.

---

## H. Compare prices (collector)

Navigation: **Collector dashboard → Prices**.

- **Indicative reference price** is shown in a distinctly labelled card with the
  last-updated time.
- **Actual recycler offers** are listed per material with facility name, conditions
  and last-updated time; sorted by best price.
- Offers without a matching reference price appear in their own "offers only"
  group.
- **Agreed handover prices (locked)** lists completed deals; these are historic
  facts, not live prices.
- Filter pills by category; pull-to-refresh triggers a cloud pull when a real
  session exists.

---

## I. Transaction-price snapshots

At handover, `confirmRecyclerHandover` writes the **agreed** rate into the lot and
the ledger (`agreedRatePerKg`, `agreedAt`). Once set, the value is immutable:
later reference-price or offer edits never rewrite it, and receipts read from the
ledger snapshot. `recordAgreedHandoverPrice` refuses to change an existing value.

---

## J. Run the Android tests

```powershell
cd "D:\SIH APP PROJECT\google-sih"
.\gradlew.bat testDebugUnitTest
```

Expected: **63 tests, 0 failures, 1 skipped** (live-Supabase integration test).
Key suites: `DemoAuthConfigTest`, `DemoRoleAccessTest`, `PriceManagementPolicyTest`,
`PriceManagementRepositoryTest`, `Migration6To7Test`.

---

## K. Run the NestJS tests

```powershell
cd backend\nestjs
npm test
```

Expected: **20 pass, 0 fail** (9 auth, 11 price), preceded by `tsc` build.

---

## L. Run the FastAPI tests

```powershell
cd backend\fastapi
pip install -r requirements-dev.txt
python -m pytest -q
```

Expected: **10 passed**.

---

## M. Build the debug APK

```powershell
cd "D:\SIH APP PROJECT\google-sih"
.\gradlew.bat :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed).
Release (`assembleRelease`) is unsigned until you supply a keystore — see
`docs/ANDROID_RELEASE.md`.

---

## N. Environment variables

**App (`.env`, untracked):** `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`GOOGLE_WEB_CLIENT_ID`, `MAPS_API_KEY`, `GEMINI_API_KEY`, plus the `DEMO_*` keys
in §B. Never add a service-role key or database password to the app.

**NestJS (`backend/nestjs/.env`):** `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`CORS_ORIGINS`, `NODE_ENV`, optional `VOICE_API_KEY`, `FASTAPI_VOICE_URL`.
See `docs/BACKEND_DEPLOYMENT.md §2`.

**FastAPI (`backend/fastapi/.env`):** `CORS_ORIGINS`, `MAX_AUDIO_BYTES`,
`MAX_TRANSCRIPT_CHARS`, optional `VOICE_API_KEY`, `WHISPER_*`.

All templates contain placeholders only.

---

## O. Review and apply database migrations manually

Do this only in the Supabase SQL Editor, in order:

`schema.sql` → `0001_single_source_of_truth.sql` → `0002_auth_profile_trigger.sql`
→ `0003_manifest_details.sql` → `0004_require_mobile_number.sql`
→ `0005_recycler_dataset.sql` → `0006_role_validation_rpc.sql`
→ `0007_security_hardening.sql` → `0008_price_management.sql`.

Then run the verification queries in `docs/SUPABASE_SETUP.md §2`.
`0008` adds `material_price_history`, price/offer columns, `current_recycler_id()`,
ownership RLS and the agreed-price immutability trigger. Rollback notes are in
`SUPABASE_SETUP.md §7`.

**Current live status:** none of these are applied to
`ekqatebpbvxwjohfhipf`. The app has only been verified statically, by unit tests,
and against an in-memory/local Room database.

---

## P. Start the backends locally

```powershell
# NestJS (port 3000)
cd backend\nestjs
npm ci
npm run build
npm run start:dev

# FastAPI (port 8000, internal only)
cd backend\fastapi
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000
```

Health checks: `GET http://127.0.0.1:3000/api/auth/health` and
`GET http://127.0.0.1:8000/health`.

---

## Q. Configure Supabase integration

1. Apply the migrations (§O) in a project you own.
2. Put the project URL and **anon/publishable** key in the app `.env`.
3. Sign up normally (email + mobile); the trigger creates the `profiles` row.
4. Provision the first admin and an approved recycler with SQL
   (`docs/SUPABASE_SETUP.md`).
5. Verify RLS with two accounts (collector A cannot read collector B; a recycler
   sees only inbound lots; a suspended user sees nothing).

Demo login does not need any of this; it is on-device only.

---

## R. Feature classification

| Feature | Classification |
| --- | --- |
| Demo login (collector/recycler/admin) | **Demo-only**, debug builds, no JWT |
| Admin price CRUD, validation, 20% confirm, history list | **Local + demo verified**; cloud sync unverified |
| Recycler offers, ownership rules | **Local + unit verified**; RLS unverified live |
| Collector price board and comparison | **Local verified**; needs a real session to show cloud data |
| Agreed-price snapshot and immutability | **Local + unit verified**; DB trigger unverified live |
| Offline queue and conflict handling | **Unit verified**; live sync unverified |
| AI image classification / voice | **Compile verified**; model inference not executed here |
| NestJS auth + price validation endpoints | **Unit verified**; not deployed |
| FastAPI transcription | **Unit verified fail-closed**; model inference not executed here |

---

## S. Before you deploy

- [ ] Rotate every credential that ever appeared in public git history.
- [ ] Apply `schema.sql` + `0001..0008`; run the verification queries.
- [ ] Provision an admin and an approved recycler; test cross-role access.
- [ ] Set `CORS_ORIGINS` and `NODE_ENV=production` on NestJS.
- [ ] Keep FastAPI private and set `VOICE_API_KEY`.
- [ ] Set a reachable HTTPS backend URL for voice (current default is emulator
      loopback and is inactive in release).
- [ ] Create your own upload keystore; never reuse or overwrite an existing one.
- [ ] Build a release APK **without** `DEMO_LOGIN_ALLOW_RELEASE`.
- [x] ~~Install on a physical device and run the demo flow.~~ Done 2026-10-29 on a
      vivo V2336 (Android 16) with the debug APK — see §T and
      `docs/TEST_REPORT.md §7`.
- [ ] Run the remaining device steps (real Supabase sign-in, camera/ONNX, Maps,
      Google Sign-In, voice, agreed-price handover) via
      `docs/TEST_REPORT.md §8`.

---

## T. Verified on device (2026-10-29)

Run performed by the agent on a **vivo V2336**, Android 16 (SDK 36),
arm64-v8a, 1080x2400, using the debug APK
(`app/build/outputs/apk/debug/app-debug.apk`).

| Step | What was actually observed |
| --- | --- |
| Install | `adb install -r …app-debug.apk` → `Success` |
| Cold launch | `Status: ok`, `LaunchState: COLD`, ~2.3 s, no `FATAL EXCEPTION` |
| Collector demo | phone `9876543210` → demo banner, `Demo OTP: 123456`, no SMS → Collector Hub |
| Admin demo | phone `9876543212` → admin dashboard labelled `DEMO-ADMIN-NOT-AUTHORISED (MoEFCC)` |
| Recycler demo | phone `9876543211` → `Demo Recycling Facility (not CPCB verified)`, `CPCB ID: DEMO-RECYCLER-NOT-VERIFIED` |
| Create reference price | `Test PCB`, PCBs, ₹100/kg, ₹80–₹120, grade A → saved as `v1`, actor `DEMO-ADMIN-NOT-AUTHORISED` |
| Demo isolation | snackbar "Saved on this device only. Demo sessions cannot write to the server." |
| 20 % rule | ₹100 → ₹150 opened the confirmation dialog naming both prices and stating that existing handovers keep their agreed price; confirming produced `v2` |
| Price history | `CREATE ₹100 v1` and `UPDATE ₹150 v2`, each with actor and timestamp |
| Validation | `min 200 > max 120` → "Minimum price cannot be greater than the maximum price."; `min -5` → "Minimum price must be a non-negative number."; dialog stayed open both times |
| Recycler offer | `Test PCB`, ₹140/kg, 5–100 kg, grade A, "Clean boards" → Active; card shows "Indicative govt reference: ₹150/kg" separately |
| One offer per material | second PCBs offer refused: "You already have an offer for this material. Edit that offer instead." |
| Collector price board | indicative ₹150/kg (₹80–₹120, updated 29 Oct 11:40) shown separately from "Actual recycler offers (1) → Demo Recycling Facility … ₹140/kg" |
| Runtime errors | none: no app-level `E` logs, no `FATAL EXCEPTION`, nothing in dropbox for the package |

**Observed but expected:** the collector Home/Earnings cards show illustrative
local figures (₹240 / ₹380 per kg marketing cards, plus a static ₹2584 settled /
₹4084 pending headline) and four seeded recycler facilities, while the Price
board truthfully says "No published prices yet" until a price exists. This is the
Room fallback seed described in §A, not cloud data. Those headline figures do not
reconcile with the actual local ledger (one 1 kg lot settled at ₹380) and must
not be read as real earnings.

**Not covered by this run:** real Supabase auth/RLS and sync, Google Sign-In,
camera/ONNX scanning, Maps rendering, voice, multi-device conflict handling, and
a handover that freezes an agreed price. Those still require the applied schema
and a signed build — see `docs/TEST_REPORT.md §8`.
