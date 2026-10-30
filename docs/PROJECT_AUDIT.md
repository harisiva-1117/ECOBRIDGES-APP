# ECOBRIDGES — Project Audit

**Product:** ECOBRIDGES (SIH 2026, problem statement 26229) — e-waste collection,
recycling and compliance platform.
**Primary codebase:** `D:\SIH APP PROJECT\google-sih`
**Audit date:** 2026-10-29
**Auditor:** automated engineering audit (source inspection + executed tests)
**Rule applied:** no claim below is made unless it was verified by reading code,
running a command, or observing a build/test result.

---

## 1. Scope

| Area | Path | Inspected |
| --- | --- | --- |
| Android app | `app/src/main/java/com/example/**` | yes — auth, OTP, manifest, voice, sync, AI scanner, i18n |
| Android build | `app/build.gradle.kts`, `gradle/libs.versions.toml` | yes |
| Supabase schema | `supabase/schema.sql`, `supabase/migrations/0001..0008` | yes |
| NestJS backend | `backend/nestjs/src/**` | yes — auth, guard, voice |
| FastAPI backend | `backend/fastapi/*.py` | yes — main, whisper, NLP |
| Configuration / secrets | `.env*`, `.gitignore`, `google-services.json` | yes — values not printed |
| Git history | local repository | inspected for tracked env files only |
| Web PWA | `D:\ECOBRIDGES` | **out of scope** this pass (secondary target) |

---

## 2. Repository layout

```
google-sih/
├─ app/                      Android application (Kotlin + Compose)
├─ backend/
│  ├─ nestjs/                Auth + voice-intent API (NestJS 10)
│  └─ fastapi/               Whisper STT + NLP intent service (FastAPI)
├─ supabase/
│  ├─ schema.sql             Base schema (12 tables)
│  └─ migrations/            0001..0008 (0007 = audit hardening, 0008 = prices)
├─ docs/                     This documentation set
└─ .env / .env.example       Build-time client configuration (ignored / template)
```

Identifiers:
- Android `applicationId`: `com.aistudio.ewaste.ind` (unchanged)
- Android namespace: `com.example`
- minSdk 24, targetSdk 36, compileSdk 36.1
- AGP 9.1.1, Kotlin 2.2.0 (stdlib forced to 2.3.21), Compose BOM 2025.08.01

---

## 3. Findings

Severity: **Critical** = exploitable authentication/authorisation or secret
exposure; **High** = data leak or false security claim; **Medium** = hardening
gap; **Low** = hygiene.

### 3.1 Critical — fixed

| # | Finding | Evidence | Fix |
| --- | --- | --- | --- |
| C1 | Real `.env` files were tracked in git (app, NestJS, FastAPI + `.backup` copies) | `git ls-files -s` listed `.env`, `.env.backup`, `backend/*/.env*` | `git rm --cached` on all six; `.gitignore` rewritten; only `*.example` templates tracked |
| C2 | Any user could sign in as `government_admin` because the client wrote `role`/`account_status` to `profiles` after signup | `SupabaseAuthService.signUpWithEmail` performed a direct `profiles.upsert(... role, account_status ...)` | Upsert removed; profiles are created only by the server-side trigger; DB insert policy in `0007` forbids privileged roles |
| C3 | Signup trigger accepted `government_admin` from client metadata | `0002`/`0004` `handle_new_user()` used the requested role verbatim | `0007` demotes anything outside `informal_collector`/`formal_recycler`; recyclers start `pending_verification` |
| C4 | Android demo login (synthetic profile, no verified credential) was available in release builds | Demo path only checked `OTP_MODE` | `demoAuthAllowed = BuildConfig.DEBUG && demoOtpMode`; every demo entry point returns `PROVIDER_NOT_CONFIGURED` otherwise |
| C5 | `NestJsAuthClient` could mint/accept an unverified role lookup and was dead code | `app/.../network/NestJsAuthClient.kt` | File deleted; `SupabaseAuthService` no longer imports it |
| C6 | NestJS accepted base64-decoded JWTs without signature verification (any user/role forgeable) | old guard implementation | New `SupabaseAuthGuard`: RS256/ES256 via project JWKS (or HS256 only with an explicit secret), `iss`/`aud`/`exp`/`nbf` checked, role loaded from `profiles` under the caller's RLS |
| C7 | `OTP_MODE=demo` presented as real SMS delivery ("Govt. SMS Gateway", "SMS sent") | `OtpManager.kt`, `LanguageManager.kt`, `RoleAuthScreens.kt` | All labels changed to "Demo OTP / not sent"; `SEND_SMS`/`RECEIVE_SMS` removed from the manifest; `SmsManager` code removed |
| C8 | Hard-coded personal data used as default OTP destination (`9820144521`, `surendharkavin01@gmail.com`) | `OtpManager.dispatchMultiChannelOtp` | Removed; no default destination; no synthetic PII in demo profiles (`DEMO-*` identifiers only) |

### 3.2 High — fixed

| # | Finding | Fix |
| --- | --- | --- |
| H1 | `get_profile_by_identifier(text)` was executable by `anon`, leaking profile PII by phone/email | `0007` revokes it from `PUBLIC`, `anon`, `authenticated`; grants `service_role` only |
| H2 | Recycler accounts were `active` immediately after self-registration with a self-supplied CPCB number | `0007`: recyclers start `pending_verification`; `current_user_role()` returns `unverified_recycler` unless an active `authorized_recyclers` row matches |
| H3 | Inactive/suspended accounts kept their role under RLS | `current_user_role()` returns `anon` for any non-active status; all role-aware policies inherit the tightening |
| H4 | FastAPI CORS was `*` **with** `allow_credentials=True` | Env-driven `CORS_ORIGINS`, credentials disabled |
| H5 | FastAPI fabricated a transcript when Whisper was unavailable ("Open informal collector login", confidence 0.98) | Fallback deleted; `TranscriptionUnavailableError` → HTTP 503 |
| H6 | FastAPI returned `str(e)` to clients | Generic 500 bodies; details logged server-side only |
| H7 | No upload limits on the voice API (memory exhaustion) | `MAX_AUDIO_BYTES` enforced while streaming; base64 validated and size-checked |
| H8 | Voice intent endpoint trusted a client-supplied `userRole` | `SupabaseAuthGuard` added; server derives the role from the token + profile; body role ignored |
| H9 | OTPs generated with `kotlin.random.Random` | `SecureRandom` everywhere; OTP comparison is length-checked and non-short-circuiting |

### 3.3 Medium — fixed

| # | Finding | Fix |
| --- | --- | --- |
| M1 | `allowBackup="true"` could exfiltrate local Room DB / prefs via cloud backup | `allowBackup="false"`, `fullBackupContent="false"`, `dataExtractionRules` removed from the manifest |
| M2 | NestJS CORS `*` in every environment | `CORS_ORIGINS` allow-list; production refuses to boot without it |
| M3 | NestJS DTO validation allowed unknown properties | `forbidNonWhitelisted: true` |
| M4 | OTP endpoints returned fake sessions | Now `501 Not Implemented` with an honest message |
| M5 | `lot-photos` storage bucket had no guaranteed private policy | `0007` creates/forces a private bucket with owner-only SELECT/INSERT/UPDATE/DELETE |
| M6 | No CHECK constraints on `profiles.role` / `account_status` | `0007` adds `NOT VALID` constraints (new writes enforced, legacy rows untouched) |

### 3.4 Residual / accepted (see `KNOWN_LIMITATIONS.md`)

- Real OTP delivery is **not implemented**. `OTP_MODE=sms` fails explicitly.
- The new Supabase project has **no schema applied yet**; nothing was verified
  against live RLS.
- Release artifacts are **unsigned** (no keystore, no passwords supplied).
- Both GitHub repositories are public and the old env files remain in history →
  every credential that ever appeared there must be rotated.
- No device/emulator was attached, so no on-device E2E was executed.
- No Play Store / hosting deployment was performed.

---

## 4. Verification evidence

| Check | Command | Result |
| --- | --- | --- |
| Android debug assemble | `gradlew assembleDebug` | `BUILD SUCCESSFUL` |
| Android unit tests | `gradlew testDebugUnitTest` | 63 tests, 0 failures, 1 skipped (documented integration test) |
| Android release assemble | `gradlew assembleRelease bundleRelease` | `BUILD SUCCESSFUL`, unsigned artifacts |
| NestJS typecheck | `npx tsc -p tsconfig.json --noEmit` | exit 0 |
| NestJS security + price tests | `npm test` | 20/20 pass |
| FastAPI tests | `python -m pytest -q` | 10/10 pass |

Logs are referenced in `TEST_REPORT.md`.

---

## 5. What this audit did not do

- It did not apply migrations to the Supabase project (no DB credentials).
- It did not rotate keys (requires provider consoles).
- It did not rewrite git history (destructive; needs owner approval).
- It did not sign or publish the app.
- It did not touch the React PWA at `D:\ECOBRIDGES`.
