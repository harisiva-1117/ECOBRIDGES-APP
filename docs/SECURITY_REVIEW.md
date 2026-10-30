# ECOBRIDGES — Security Review

**Date:** 2026-10-29
**Scope:** Android client, Supabase schema/RLS, NestJS API, FastAPI voice service,
build-time secret handling.
**Method:** source review + executed security tests. Findings that could not be
verified against a live system are marked **unverified**.

---

## 1. Threat model

| Actor | Capability assumed | Must not be able to |
| --- | --- | --- |
| Anonymous internet user | knows the public anon key, can call any endpoint | read other users' lots/photos, call privileged RPCs, obtain a role |
| Self-registered user | can create an account, controls request bodies | self-assign `government_admin`, become a verified recycler, read another user's data |
| Suspended user | has a valid old JWT | keep privileged access after status change |
| Malicious client | can modify APK traffic, replay tokens | forge JWTs, bypass signature checks, spoof `userRole` |
| Curious APK analyst | can unzip the APK | find a service-role key, SMS gateway credential, or private AI key |
| Compromised dependency | runs in backend process | (partially mitigated) reach secrets via env; no secret is logged |

---

## 2. Authentication & session security

### Android

- Supabase GoTrue is the only identity provider. No local password store.
- Demo/local authentication is an explicit, build-time option, not a fallback:
  - available only when `DEMO_LOGIN_ENABLED=true` **and** (`BuildConfig.DEBUG`
    or `DEMO_LOGIN_ALLOW_RELEASE=true`); release defaults to disabled;
  - the code is a constant (`DEMO_OTP_CODE`), generated and compared on-device;
    no SMS is sent and no provider is called;
  - demo sessions never receive a Supabase JWT, so RLS still blocks every
    protected read/write; local price edits are not queued for sync;
  - identifiers are prefixed `DEMO-` and never resemble a real CPCB number;
  - Admin demo needs `DEMO_ENABLE_ADMIN=true` **and** a phone allow-listed as
    `admin` in `DEMO_ACCOUNTS`. The client cannot self-promote; an arbitrary
    number may only open collector/recycler demo portals;
  - the UI labels the flow "Demo sign-in" and never claims identity verification.
- The client never writes `role` / `account_status`; the server trigger does.
- A locally cached role is only used to *choose a portal* and to gate the demo
  OTP; the authoritative role for data access is the `profiles` row under RLS.
- OTPs: `SecureRandom`, 6 digits, expiry + attempt lock + one-time use, compared
  without early exit (`AuthValidation.otpMatches`).
- OTP delivery: **not sent**. The UI always says "Demo OTP" and the manifest has
  no `SEND_SMS` / `RECEIVE_SMS` permission.
- `allowBackup="false"`, `fullBackupContent="false"` — Room DB and prefs are not
  copied into cloud backups.
- Cleartext HTTP is enabled only in the debug manifest overlay
  (`app/src/debug/AndroidManifest.xml`); release keeps Android's default
  (blocked).

### NestJS

- Password and Google ID tokens are verified by Supabase; the service does not
  compare secrets itself and does not mint tokens.
- JWT verification (`supabase-auth.guard.ts`):
  - algorithm allow-list: RS256, ES256; HS256 only if `SUPABASE_JWT_SECRET` is set
  - signature verified against `<SUPABASE_URL>/auth/v1/.well-known/jwks.json`
  - `exp`, `nbf`, `iss`, `aud` validated with 30 s skew
  - unknown `kid` triggers one JWKS refresh, then fails closed
  - `alg: none`, unsigned base64 tokens and malformed tokens are rejected
- The role is read from `profiles` with the caller's own token (RLS), so a token
  cannot carry a forged application role.
- Inactive / missing profile → `403`.
- OTP endpoints return `501`; they cannot issue a session.
- CORS is an explicit allow-list; production refuses to start without
  `CORS_ORIGINS`.
- Request validation: whitelist + reject unknown properties.
- Secrets are read from the environment; supabase service key is never returned
  to clients.

### Supabase

- `current_user_role()` is `SECURITY DEFINER` with `search_path = public`, and:
  - returns `anon` for missing/inactive profiles,
  - returns `unverified_recycler` for a recycler without a matching **active**
    `authorized_recyclers` row (by CPCB number or contact email),
  - otherwise the stored role.
- Signup trigger:
  - accepts only `informal_collector` / `formal_recycler`,
  - forces recyclers to `pending_verification`,
  - stores a self-supplied statutory number for review but never treats it as
    proof,
  - demotes `government_admin` requests to `informal_collector`.
- `profiles` INSERT policy restricts role/status; UPDATE policy forbids changing
  role or status.
- `get_profile_by_identifier()` is revoked from `anon`/`authenticated`.
- `lot-photos` bucket is private; object paths are owner-scoped by
  `storage.foldername(name)[1] = auth.uid()::text`.
- Role/status CHECK constraints are `NOT VALID` so legacy rows don't block the
  migration, while all new writes are enforced.

---

## 2b. Price-management authorization

Two separate concepts, with separate write permissions:

| Object | Who may write | Enforcement |
| --- | --- | --- |
| Government **reference price** (`material_prices`) | `government_admin` only | Android `PriceManagementPolicy.canManageReferencePrices`; NestJS `@Roles(GOVERNMENT_ADMIN)`; RLS on `material_prices` |
| Recycler **offer** (`recycler_offered_rates`) | `formal_recycler` for its own facility only | Android ownership check; NestJS `statutoryIdentifier` vs `offer.recyclerId`; `current_recycler_id()` + ownership RLS |
| Agreed handover price (`collector_lots.agreed_rate_per_kg`) | receiving recycler at handover; immutable afterwards | validation in `EwasteRepository`; DB immutability trigger |

Additional rules:

- Non-negative prices; `market_min <= market_max`; `min_quantity <= max_quantity`;
  INR only; offer status limited to `DRAFT|ACTIVE|PAUSED|EXPIRED`.
- Edits carry a `version`; a stale `baseVersion` is rejected as a conflict and
  never overwrites a newer server value.
- A move of **>= 20%** requires an explicit confirmation in the admin UI.
- Reference-price edits append immutable history rows (`material_price_history`,
  trigger in `0008`); historical prices are never deleted or rewritten.
- Reference prices are labelled **indicative**; recycler offers are labelled
  **actual**. Changing either never alters an agreed handover price.
- Demo sessions may edit locally but never queue or push a change, because they
  hold no JWT.

> Live RLS verification is still pending: `0008_price_management.sql` is written
> but not applied to the project (see `KNOWN_LIMITATIONS.md`).

---

## 3. Secrets

### Current state

- Real `.env` files were removed from the git index (`git rm --cached`).
- `.gitignore` blocks `.env`, `.env.*`, `**/.env*`, `*.jks`, `*.keystore`,
  `*.aab` (templates `*.example` are allowed).
- All three `.env.example` files contain placeholders only.
- The APK packages only `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
  `GOOGLE_WEB_CLIENT_ID`, `MAPS_API_KEY`, `GEMINI_API_KEY`, `OTP_MODE`.
  `GROK_API_KEY`, `XAI_API_KEY`, `XAI_BASE_URL`, `GROK_MODEL` are on the
  Secrets-plugin ignore list **and** were removed from the app `.env`.
- Service-role / database URLs exist only in `backend/nestjs/.env` (untracked).

### Required rotation (cannot be done by code)

The repositories are public and the old values remain in git history. Treat all
of the following as compromised and rotate them in the provider console:

| Credential | Where to rotate |
| --- | --- |
| Supabase anon/publishable key | Supabase → Project Settings → API |
| Supabase service-role / secret key | Supabase → Project Settings → API |
| Supabase database password | Supabase → Project Settings → Database |
| Google OAuth web client ID (if a secret exists for it) | Google Cloud Console → Credentials |
| Google Maps SDK key | Google Cloud Console → Credentials (restrict to package + SHA-1) |
| Gemini API key | Google AI Studio |
| xAI / Grok key (`gsk_…`) | xAI console |
| Any SMS/email provider credential | provider console |

> Do not paste secrets into chat, tickets or commits. Set them in the platform's
> secret manager / CI secret store.

### Handling rules going forward

1. Never commit `.env`; commit only `*.env.example`.
2. Client builds may contain only publishable keys.
3. Server secrets live in the hosting provider's secret store, injected as env.
4. Rotate a secret immediately if it appears in a build artifact or log.
5. Do not log request bodies containing credentials or OTPs.

---

## 4. Input validation & abuse resistance

| Surface | Control |
| --- | --- |
| Android forms | role/phone/email validation before network calls; OTP format check |
| NestJS DTOs | `class-validator` + whitelist + `forbidNonWhitelisted` |
| NestJS body limits | NestJS/Express defaults (no upload endpoint) |
| FastAPI upload | streamed read capped at `MAX_AUDIO_BYTES` (default 10 MiB), 413 on exceed |
| FastAPI base64 | `validate=True`, empty/size checks |
| FastAPI transcript | `max_length` (default 2000 chars) |
| FastAPI errors | generic 500, details only in server logs |
| CORS | explicit allow-list on both backends |

No rate limiting is implemented on the backends. If they are exposed publicly,
add a gateway/edge rate limit (see `KNOWN_LIMITATIONS.md`).

---

## 5. Automated security tests

### NestJS (`backend/nestjs/test/auth.security.test.js` + `test/price.policy.test.js`, 20 tests)

- role slug mapping rejects unknown values
- missing / non-Bearer token rejected
- malformed token rejected
- **forged unsigned base64 token rejected** (the regression that matters)
- `alg: none` rejected
- roles guard denies insufficient role, allows matching role
- OTP endpoints cannot mint sessions (501)
- email login fails closed without Supabase configuration
- reference-price endpoints are admin-only; offer endpoints are recycler-only
- a recycler cannot validate an offer for another facility
- invalid ranges/currencies/statuses are rejected
- stale versions and an existing agreed price are never overwritten
- >= 20% moves are reported for confirmation

Run: `cd backend/nestjs && npm test`

### FastAPI (`backend/fastapi/test_main.py`, 10 tests)

- health reports model state
- transcription returns **503** without a model (never fabricated text)
- oversized/empty uploads rejected
- invalid base64 rejected
- oversized decoded payload rejected
- transcript length bounded
- CORS is not a wildcard
- optional API-key gate (401 without/with wrong key, 200 with key)

Run: `cd backend/fastapi && python -m pytest -q`

### Android (`app/src/test/...`)

- `AuthValidationTest` (8): role mapping, rejection of unknown roles, Indian
  mobile normalisation, OTP format, OTP comparison
- `DemoAuthConfigTest` (6): debug/release gating, admin flag, account parsing,
  otp fallback, cross-role denial
- `DemoRoleAccessTest` (4): collector/recycler/admin demo sessions, admin cannot
  be self-selected, wrong code rejected
- `OtpManagerTest` (4): six digits, numeric range, variation, `isSmsDispatched`
  is always false
- `PriceManagementPolicyTest` (13): role permission, ownership, validation,
  significant change, stale version, agreed-price immutability
- `PriceManagementRepositoryTest` (11): Room-level authorisation, history,
  offline queue, optimistic conflict, agreed-price preservation
- `ExampleRobolectricTest` (14, 1 skipped): demo role gating, cross-role blocked,
  unregistered email never gets a session, legacy Google path fails closed
- Full suite: 63 tests, 0 failures, 1 skipped integration test

Run: `gradlew testDebugUnitTest`

---

## 6. Residual risk register

| Risk | Severity | Status | Owner action |
| --- | --- | --- | --- |
| Old credentials in public git history | Critical | **Open** | rotate all keys; optionally purge history with owner approval |
| Migrations not applied to the live project | Critical | **Open** | run `schema.sql` + `0001..0008` |
| Price RLS/history trigger unverified live | High | **Open** | apply `0008`, then test cross-role price writes with real JWTs |
| Demo login is not identity verification (debug builds) | Medium | Accepted by design | keep release demo disabled; remove or replace before any public build |
| No real SMS/email OTP | High | **Open** | choose a provider and implement server-side verification |
| Unsigned release artifact | High | **Open** | create upload keystore, set `KEYSTORE_PATH`/passwords, sign |
| No rate limiting | Medium | **Open** | add edge/gateway limits |
| R8/minify disabled | Low | Accepted | enable after device regression testing |
| `verificationStatus` of self-supplied recycler data | Medium | Mitigated | admin verification is required before `formal_recycler` is honoured |
| Debug builds allow cleartext HTTP | Low | Accepted | debug-only manifest |
| No dependency auditing in CI | Medium | **Open** | add `npm audit`, `pip-audit`, Gradle dependency checks |

---

## 7. Reporting a vulnerability

Do not open a public issue with exploit details. Contact the maintainers
privately and include: affected component, reproduction steps, impact, and any
suggested fix.
