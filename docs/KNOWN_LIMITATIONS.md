# ECOBRIDGES â€” Known Limitations

**Date:** 2026-10-29
**Purpose:** an honest, current list of what the product does *not* do, what is
not verified, and what must be fixed before any public release. Each item states
the evidence.

Legend: **Blocker** = must be fixed before release Â· **High** = must be fixed
before production use Â· **Medium** = acceptable for a pilot Â· **Low** = cosmetic
/ hygiene.

---

## 1. Blockers

### B1 â€” No real OTP provider, so release builds cannot sign in

- The only working second factor is the **demo** path in
  `SupabaseAuthService.kt` / `DemoAuthConfig.kt`.
- Demo is available only when `DEMO_LOGIN_ENABLED=true` **and**
  (`BuildConfig.DEBUG` or `DEMO_LOGIN_ALLOW_RELEASE=true`). The debug build sets
  the flag; release defaults to disabled and needs an explicit override.
- The demo code is a build-time constant (`DEMO_OTP_CODE`, default `123456`).
  No SMS/OTP provider is called, and a demo session never holds a Supabase JWT.
- **Impact:** a signed release build has no way to complete sign-in. Demo builds
  work, but they are explicitly not identity verification and have no backend
  access.
- **Fix required:** implement server-side OTP (Supabase phone auth with
  Twilio/MessageBird, or email OTP via a transactional provider), then replace
  `issuePendingOtp`/`verifyLoginOtp` with the server flow. Alternatively remove
  the second factor and rely on Supabase's own MFA â€” a product decision.
- **Do not** ship a release with `DEMO_LOGIN_ALLOW_RELEASE=true`, and keep
  `DEMO_ENABLE_ADMIN=false` outside the hackathon demo.

### B2 â€” Database schema is not applied to the live project

- Project `ekqatebpbvxwjohfhipf` has authentication enabled but no business
  tables; `schema.sql` and `0001..0008` have not been executed.
- No DB password or Supabase personal access token was available, and PostgREST
  cannot run DDL.
- **Impact:** every profile/lot/photo query fails; sign-up cannot create a
  `profiles` row; RLS cannot be verified.
- **Fix:** run the nine SQL files in order (`SUPABASE_SETUP.md Â§2`).

### B3 â€” Release artifacts are unsigned

- `app-release-unsigned.apk` (165,091,414 bytes) and
  `app-release.aab` (83,615,632 bytes) were built without a signing identity.
- `my-upload-key.jks` is absent; `STORE_PASSWORD` / `KEY_PASSWORD` are unset; the
  build log says `Release signing is not configured ... will be unsigned`.
- **Impact:** cannot install, cannot upload to Play, cannot update in place.
- **Fix:** create or supply the upload keystore and set the env vars
  (`ANDROID_RELEASE.md Â§4`). The keystore is a long-lived asset: back it up.

### B4 â€” Credentials in public git history are not rotated

- Both `github.com/harisiva-1117/ECOBRIDGES-APP` and
  `github.com/Surendhar-01/ECOBRIDGES` are public.
- Old `.env` values (Supabase keys, Google OAuth client ID, Gemini/Maps keys,
  xAI/Grok key) remain in history even though the files are untracked now.
- **Impact:** anyone who cloned the repo may hold live credentials.
- **Fix:** rotate every key in the provider consoles. History rewrite is optional
  and requires owner approval; rotation is mandatory either way.

---

## 2. High

### H1 â€” No rate limiting anywhere

- NestJS auth endpoints and the FastAPI voice endpoints have no per-IP or
  per-account throttling. Only body-size and field-length caps exist.
- **Impact:** credential stuffing, OTP brute force (once real OTP exists), and
  CPU exhaustion on the STT box.
- **Fix:** enforce limits at the edge (nginx/Cloudflare/API gateway) and add
  per-account lockout server-side.

### H2 â€” Google Sign-In is unverified on a device

- `loginWithGoogleAuthResult` performs Supabase `signInWithIdToken`, but no OAuth
  client/SHA-1 is configured for this package, and Google Sign-In was not
  exercised on the attached device.
- **Impact:** the flow may fail on first real use (client ID or SHA-1 mismatch).
- **Fix:** configure the OAuth client, add debug + release SHA-1, test on a
  physical device.

### H3 â€” No live RLS / auth verification

- The RLS model is defined in SQL but never executed against a live database.
- The price-management policies in `0008_price_management.sql` (admin-only
  reference prices, recycler-owned offers, immutable agreed rates) are likewise
  unverified. Only the on-device/Room rules and the NestJS validators have run.
- **Impact:** a policy typo would only surface in production.
- **Fix:** after B2, run the two-account cross-user test in
  `TEST_REPORT.md Â§8` (collector cannot read another collector's lot; recycler
  sees only inbound; suspended user sees nothing).

### H4 — Device E2E is partial

- A physical vivo V2336 (Android 16) was attached on 2026-10-29; install, demo
  sign-in for all three roles, reference-price CRUD, validation, 20 %
  confirmation, price history, recycler offers, ownership, the one-offer rule and
  the collector price board were exercised successfully.
- **Still unverified on device:** camera capture, ONNX inference, maps, Google
  Sign-In, real Supabase auth/sync, the voice loop, and an agreed-price handover.
- **Fix:** run the remaining steps in `docs/TEST_REPORT.md §8`.

### H5 â€” `BackendVoiceClient` only works on the emulator loopback

- Defaults are `http://10.0.2.2:3000` / `:8000`; `configureEndpoints()` has no
  caller and there is no `BACKEND_URL` build key.
- Authentication was added (bearer token from the session), but in a release
  build there is no reachable backend URL and cleartext HTTP is blocked.
- The direct FastAPI call also bypasses NestJS and sends no `X-API-Key`, so it
  only works when `VOICE_API_KEY` is unset.
- **Impact:** backend voice is inactive in release; the local
  `SemanticIntentClassifier` handles voice navigation.
- **Fix:** add a HTTPS backend base URL to build config and route all voice
  traffic through NestJS (never FastAPI directly).

### H6 â€” Recycler verification is manual and has no admin UI

- Approval requires SQL (`profiles.account_status`, `authorized_recyclers`).
- **Impact:** an operator must run SQL for each recycler; error-prone at scale.
- **Fix:** build an admin approval screen backed by a guarded server endpoint.

### H7 â€” `GEMINI_API_KEY` and `MAPS_API_KEY` are placeholders

- `BuildConfig` shows `GEMINI_API_KEY = "MY_GEMINI_API_KEY"` and
  `MAPS_API_KEY = "DEFAULT_MAPS_API_KEY"`.
- **Impact:** Gemini-assisted capture is unavailable; it falls back to the
  on-device ONNX detector. Maps tiles may not render.
- **Fix:** supply restricted real keys at build time. Do not commit them.

### H8 — Price sync and conflicts are unit-tested, not device-tested

- `PriceManagementRepositoryTest` proves queueing, version conflicts and
  agreed-price preservation against an in-memory Room database.
- What is **not** proven: the real `CloudSyncManager` push/pull against Supabase
  (schema `0008` is unapplied), multi-device conflict handling, and cloud sync
  from the Compose price screens. The screens themselves were exercised on a
  device on 2026-10-29 (local-only demo path).
- **Impact:** sync failures would surface only at first real use.
- **Fix:** apply `0008`, then run E2E steps 11-14 in `docs/TEST_REPORT.md`
  with two accounts on two devices.

---

## 3. Medium

### M1 â€” R8/minification is disabled

- `isMinifyEnabled = false` in the release build type.
- **Impact:** larger APK, unobfuscated code, no resource shrinking.
- **Why it stays off:** enabling R8 without device regression testing risks
  breaking reflection-based serialization (kotlinx, Supabase).
- **Fix:** enable with a keep-rule pass and a full device regression run.

### M2 â€” App size is large (~168 MB debug APK / ~80 MB AAB)

- Dominated by ONNX runtime/models and Maps/Compose dependencies.
- **Impact:** slow installs, Play download limits, data cost for users.
- **Fix:** ship ABI splits / Play delivery, prune unused models, enable minify.

### M3 â€” Demo-mode text/log leftovers in code paths

- Log lines such as `[DEMO] OTP ... issued` and some strings still carry demo
  wording. They are not shown as "SMS sent" any more, and release builds fail
  before generating a code.
- **Impact:** low; a code reader might mistake the demo path for production.
- **Fix:** remove the remaining demo log lines when B1 is implemented.

### M4 â€” No CI pipeline

- Tests and builds were run manually. There is no workflow file running the three
  suites on push.
- **Impact:** regressions can land unnoticed.
- **Fix:** add CI for `gradlew testDebugUnitTest`, `npm test`, `pytest`, plus
  `tsc --noEmit` and dependency audits.

### M5 â€” No dependency / vulnerability scanning

- `npm audit`, `pip-audit` and Gradle dependency checks are not part of any
  process.
- **Fix:** add them to CI and review monthly.

### M6 â€” Error reporting and analytics are absent

- No Crashlytics/Sentry-style crash reporting, no telemetry.
- **Impact:** production failures are invisible unless users report them.
- **Fix:** add crash reporting that never captures PII or tokens.

### M7 â€” Local demo registrations persist in SharedPreferences

- `rememberLocalRegistration` stores email/phone â†’ role locally. It is used only
  to choose a portal and gate demo OTP; the authoritative role is the DB row.
- **Impact:** on a shared device a previous user's identifier may be visible to
  the next user.
- **Fix:** clear on sign-out for shared-device deployments, or remove with B1.

### M8 â€” FastAPI has no request authentication by default

- `VOICE_API_KEY` is optional and unset in the shipped templates.
- **Impact:** if port 8000 is exposed, anyone can consume CPU.
- **Fix:** always set `VOICE_API_KEY` and keep the service private.

### M9 â€” Whisper model download is implicit at first start

- `WhisperSpeechService` downloads `base` into the cache dir on first run.
- **Impact:** slow cold start, needs disk + egress; offline hosts return 503.
- **Fix:** bake the model into the image or mount a pre-populated cache.

---

## 4. Low

- **No TTS/STT language coverage for Tamil in the backend.** The app has Tamil
  strings; FastAPI accepts `hi|mr|en` and detects Hinglish. Tamil audio falls
  back to auto-detection.
- **`GET /api/auth/health` discloses `supabaseConfigured`.** Intentional and
  non-secret.
- **Deprecated Google login shim** (`loginWithGoogle`) still exists, returning
  `PROVIDER_NOT_CONFIGURED`. Kept so older call sites compile.
- **Screenshot test writes into the source tree** (`app/src/test/screenshots/`).
- **Gradle build emits Kotlin deprecation warnings** (icons, lifecycle owner).
  No functional impact.
- **The React PWA at `D:\ECOBRIDGES` was not audited or changed** in this pass.
- **No accessibility/localisation audit** was performed beyond the presence of
  EN/HI/MR/TA string sets.

---

## 5. What is *not* a limitation (verified working)

To keep this list balanced, these were verified by execution:

- Android unit/Robolectric suite: 63 tests, 0 failures, 1 documented skip.
- NestJS suite: 20/20 (auth + price policy), including rejection of the old
  unsigned-token impersonation and `alg: none`.
- FastAPI suite: 10/10, including fail-closed transcription with no model and
  non-wildcard CORS.
- Debug APK and release APK/AAB build successfully.
- Release `BuildConfig` no longer contains `GROK_API_KEY` / `XAI_API_KEY`.
- `.env` files are untracked; templates contain placeholders only.
- `SEND_SMS` / `RECEIVE_SMS` permissions are gone; `allowBackup="false"`.
- `get_profile_by_identifier()` is revoked from `anon`/`authenticated` in `0007`.
- The demo code is a build-time constant shown as "Demo OTP"; it is never
  described as SMS delivery or identity verification.
- Price rules are enforced in three places: the pure Kotlin policy, Room-level
  repository checks, and NestJS role/ownership validators (all tested).

---

## 6. Release gate

A public release is **not approved** while any of these is true:

- B1 (no real OTP) â€” sign-in impossible in release
- B2 (schema not applied) â€” no data layer
- B3 (unsigned artifact) â€” cannot distribute
- B4 (unrotated credentials) â€” live secrets exposed

H1â€“H3 should be closed before handling real user data. H4 is required before
declaring a build "tested".
