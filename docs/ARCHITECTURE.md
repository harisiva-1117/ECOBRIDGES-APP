# ECOBRIDGES — Architecture

**Status:** current implementation, verified against source on 2026-10-29.

---

## 1. System overview

```
┌──────────────────────────────────────────────────────────────────────┐
│ Android app  (com.aistudio.ewaste.ind, Kotlin + Jetpack Compose)     │
│                                                                      │
│  UI (Compose) ── ViewModels ── Room (offline source of truth)        │
│         │              │                                             │
│         │              ├── CloudSyncManager ──► Supabase PostgREST  │
│         │              │                    └─► Supabase Storage    │
│         │              ├── SupabaseAuthService ─► Supabase Auth      │
│         │              ├── BackendVoiceClient ──► NestJS / FastAPI   │
│         │              └── EwasteObbDetector ───► ONNX (on-device)   │
│         └── Voice UI ── VoiceEngine (Android TTS/STT)                │
└──────────────────────────────────────────────────────────────────────┘
            │ HTTPS                         │ HTTPS (bearer JWT)
            ▼                               ▼
┌───────────────────────┐        ┌─────────────────────────────────────┐
│ Supabase              │        │ NestJS API (auth + voice intent)    │
│  Auth (GoTrue)        │◄───────│  · verifies Supabase JWT via JWKS   │
│  PostgREST + RLS      │        │  · loads role from profiles (RLS)   │
│  Storage (private)    │        │  · proxies voice to FastAPI         │
│  Postgres             │        └──────────────┬──────────────────────┘
└───────────────────────┘                       │ internal HTTP
                                                ▼
                                   ┌─────────────────────────────┐
                                   │ FastAPI voice service       │
                                   │  faster-whisper STT         │
                                   │  rule-based NLP intents     │
                                   └─────────────────────────────┘
```

Supabase is the single source of truth for identity, role and cloud data.
Room is the offline cache/queue on the device. The backends never mint
credentials.

---

## 2. Android application

### Layers

| Layer | Packages | Responsibility |
| --- | --- | --- |
| UI | `ui/**` | Compose screens for intro, role auth, collector, recycler, admin, capture, voice |
| Presentation | `ui/*/*ViewModel.kt`, `ui/IntroViewModel.kt` | State, validation, orchestration |
| Domain/model | `model/**`, `ai/**` | `RoleType`, e-waste categories, IndiaMart rates, recycler matching, ONNX detector |
| Data | `data/**` | Room entities/DAOs/database, `CloudSyncManager` |
| Integration | `auth/**`, `network/**`, `voice/**`, `notification/**` | Supabase auth, backend clients, STT/TTS, notifications |
| Cross-cutting | `i18n/LanguageManager.kt`, `util/**` | EN/HI/MR/TA strings, demo OTP, logging |

### Offline-first data flow

1. User actions create rows in Room (`collector_lots`, `collector_transactions`,
   `lot_photos`, `collector_locations`).
2. `CloudSyncManager` pushes pending rows to Supabase PostgREST when a session
   exists and connectivity allows.
3. Photo bytes are compressed and uploaded to the private `lot-photos` bucket at
   `{auth.uid()}/{photoId}.jpg`.
4. Failures stay queued and are surfaced honestly (no optimistic "synced").
5. Role dashboards read role-aware views (`v_collector_my_lots`,
   `v_recycler_inbound_lots`, `v_admin_lots`, `v_admin_metrics`).

### Authentication flow (current)

```
Sign-up (email + password + details)
   → Supabase Auth creates the identity
   → server trigger public.handle_new_user() inserts public.profiles
   → collector: active | recycler: pending_verification | admin: not self-signable
Sign-in
   → Supabase signInWithPassword (or Sign In With ID Token for Google)
   → second factor:
        OTP_MODE=demo  (debug builds only): on-device "Demo OTP", shown in-app
        OTP_MODE=sms   : not implemented → explicit PROVIDER_NOT_CONFIGURED
   → app reads its own profiles row
   → role must match the selected portal and account_status must allow access
   → dashboard opens
```

Key invariant: the client never writes `role` or `account_status`. The database
decides, and `current_user_role()` (SECURITY DEFINER) is the only role source
for RLS.

### Voice pipeline

```
mic → Android SpeechRecognizer / recorded audio
    → (optional) BackendVoiceClient → NestJS /api/voice/intent (JWT required)
    → (optional) FastAPI /api/v1/voice/transcribe or /process-audio
    → SemanticIntentClassifier (local fallback, deterministic rules)
    → UI action + localized TTS response
```

The app remains usable for navigation/voice when the backends are unreachable
because `SemanticIntentClassifier` is local. Backends add accuracy, not
authority.

### Build-time configuration

`Secrets Gradle Plugin` reads `.env` (git-ignored) → `BuildConfig` fields:

| Key | Client-safe? | Notes |
| --- | --- | --- |
| `SUPABASE_URL` | yes | project URL |
| `SUPABASE_ANON_KEY` | yes | publishable/anon key, protected by RLS |
| `GOOGLE_WEB_CLIENT_ID` | yes | OAuth web client id |
| `MAPS_API_KEY` | yes | restrict by package + SHA-1 |
| `GEMINI_API_KEY` | sensitive | optional; on-device scanner works without it |
| `OTP_MODE` | yes | `demo` or `sms` |

`GROK_API_KEY` / `XAI_API_KEY` are on the ignore list and are **not** packaged.

---

## 3. Supabase

### Tables (13)

`profiles`, `collector_lots`, `collector_transactions`, `lot_photos`,
`collector_locations`, `connection_requests`, `quotations`, `audit_logs`,
`authorized_recyclers`, `recycler_offered_rates`, `material_prices`,
`material_price_history`, `safety_guidelines`.

### Views (5)

`v_collector_my_lots`, `v_collector_my_transactions`, `v_recycler_inbound_lots`,
`v_admin_lots`, `v_admin_metrics`.

### Storage

Bucket `lot-photos` — **private**. Object path `{auth.uid()}/{photoId}.jpg`.
Policies allow only the owning user to select/insert/update/delete.

### RLS model

Every table has RLS enabled. Policies are written in terms of
`auth.uid()` and `public.current_user_role()`:

- collectors: own rows only
- formal recyclers (verified): inbound lots, read/update
- government admins (active): read everything
- anonymous/inactive: `anon` → denied
- price objects: reference prices writable only by an active `government_admin`;
  recycler offers writable only by the owning verified recycler
  (`current_recycler_id()`); an agreed handover rate is immutable once set;
  price history is append-only and written by a trigger

### Migration history

| File | Purpose |
| --- | --- |
| `schema.sql` | base tables + initial RLS |
| `0001_single_source_of_truth.sql` | profile normalisation, role helper, role views, seed cleanup |
| `0002_auth_profile_trigger.sql` | auto-provision profiles |
| `0003_manifest_details.sql` | lot manifest fields |
| `0004_require_mobile_number.sql` | phone validation in trigger |
| `0005_recycler_dataset.sql` | CPCB registry + offered rates |
| `0006_role_validation_rpc.sql` | role lookup RPC (now service-role only) |
| `0007_security_hardening.sql` | trigger demotion, role tightening, storage, constraints |
| `0008_price_management.sql` | price/offer columns, history table + trigger, recycler ownership RLS, agreed-price immutability |

Apply order is `schema.sql` then `0001..0008` — see `SUPABASE_SETUP.md`.

---

## 4. NestJS backend

- Module: `AuthModule` (controller + service + `SupabaseAuthGuard` + `RolesGuard`)
- Module: `PricesModule` (`PricesController` + `PricePolicyService`, imports `AuthModule`)
- Module: `VoiceModule` (controller + `VoiceIntentRouterService`, imports `AuthModule`)
- Auth endpoints:
  - `GET /api/auth/health` — configuration probe (no secrets)
  - `POST /api/auth/email/login` — real GoTrue password sign-in
  - `POST /api/auth/google` — Supabase `signInWithIdToken`
  - `POST /api/auth/otp/send|verify` — **501** until a provider exists
- Price endpoints (validation only; writes still go through PostgREST/RLS):
  - `GET /api/prices/health`
  - `POST /api/prices/reference/validate` — `government_admin`
  - `POST /api/prices/offers/validate` — `formal_recycler`, own facility only
- Voice: `POST /api/voice/intent` — requires a valid bearer token; the role comes
  from the verified profile, never from the body.
- Tokens returned are the real Supabase JWTs (access + refresh + expiry). The
  service never signs tokens.

### Guard chain

```
Authorization: Bearer <jwt>
  → parse header/payload
  → alg allow-list (RS256/ES256/HS256-with-secret)
  → verify signature (JWKS, cached 5 min, one rotation retry)
  → validate exp / nbf / iss / aud
  → load profiles row with the caller's token (RLS)
  → reject missing profile, unknown role, non-active status
  → attach request.user
```

---

## 5. FastAPI backend

- `POST /api/v1/voice/transcribe` — multipart upload, size-limited
- `POST /api/v1/voice/semantic-intent` — transcript → structured intent
- `POST /api/v1/voice/process-audio` — base64 audio → STT → intent
- `GET /health`
- STT: `faster-whisper` (default `base`, CPU int8). Languages: hi, mr, en,
  Hinglish detection heuristic.
- If the model is unavailable the API returns **503**; it never fabricates a
  transcript.
- Optional `VOICE_API_KEY` gate (`X-API-Key`) for private deployments.
- CORS deny-by-default via `CORS_ORIGINS`.

---

## 6. Trust boundaries

| Boundary | Trust decision |
| --- | --- |
| Client → Supabase | client is untrusted; RLS + trigger enforce roles |
| Client → NestJS | JWT signature + DB role lookup |
| NestJS → FastAPI | private network; optional shared secret; FastAPI does not authorize business actions by itself |
| FastAPI → client | results are advisory; the app applies local validation |
| Build → APK | only publishable keys; service-role keys never packaged |

---

## 7. Deployment topology (target)

```
Internet ──► CDN/reverse proxy (TLS) ──► NestJS (container)
                                          └─► FastAPI (internal only)
Android ──► Supabase (managed)
```

See `BACKEND_DEPLOYMENT.md` for concrete steps.
