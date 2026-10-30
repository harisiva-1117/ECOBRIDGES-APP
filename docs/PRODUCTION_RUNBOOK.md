# ECOBRIDGES — Production Runbook

**Audience:** operator / on-call engineer.
**Scope:** the Android app, Supabase project, NestJS API and FastAPI voice service.
**Rule:** every step below assumes the item is *not* done yet unless it is checked
in the go-live checklist. Nothing in this repository is currently deployed.

---

## 1. Service inventory

| Service | Runs on | State | Health signal |
| --- | --- | --- | --- |
| Android app | user devices | not published | crash-free sessions, ANR rate |
| Supabase (auth, Postgres, storage) | managed `ekqatebpbvxwjohfhipf` | **no schema applied** | `/auth/v1/health`, project dashboard |
| NestJS API | container / VM behind TLS | not deployed | `GET /api/auth/health` |
| FastAPI voice | private network only | not deployed | `GET /health` |

---

## 2. Go-live checklist

Complete in order. Do not skip a line because the next one is "just docs".

### 2.1 Credentials

- [ ] Rotate **every** credential that ever appeared in the public git history:
      Supabase anon + service-role keys, DB password, Google OAuth client,
      Maps SDK key, Gemini key, xAI/Grok key, any SMS/email provider keys.
- [ ] Confirm the new values are only in the hosting secret store and the
      operator's local `.env` (untracked).
- [ ] Run `git grep -nE 'sb_secret|service_role|gsk_|AIza'` and confirm no
      match in tracked files.
- [ ] Optional (needs owner approval): purge the old values from history with
      `git filter-repo`. Rotation is mandatory either way.

### 2.2 Database

- [ ] Apply `supabase/schema.sql` then `0001..0008` in the SQL Editor.
- [ ] Run the verification queries in `SUPABASE_SETUP.md §2`.
- [ ] Confirm `anon_can_execute = false` for `get_profile_by_identifier`.
- [ ] Confirm `lot-photos` bucket is private.
- [ ] Create the first `government_admin` manually (`SUPABASE_SETUP.md §4`).
- [ ] Enable Point-in-Time Recovery / daily backups.
- [ ] Record the restore procedure's last test date.

### 2.3 Backends

- [ ] `NODE_ENV=production`, `CORS_ORIGINS` set to real origins.
- [ ] `SUPABASE_URL` / `SUPABASE_ANON_KEY` set in the host environment.
- [ ] `VOICE_API_KEY` set and FastAPI reachable only from NestJS.
- [ ] TLS certificate valid; HTTP permanently redirects to HTTPS.
- [ ] `GET /api/auth/health` → `supabaseConfigured: true`.
- [ ] Positive/negative JWT checks from `BACKEND_DEPLOYMENT.md §6`.
- [ ] Edge rate limiting enabled (auth and voice paths).
- [ ] Log aggregation confirmed; verify no tokens/passwords/OTPs in logs.

### 2.4 Android

- [ ] `SUPABASE_URL` points at `ekqatebpbvxwjohfhipf`.
- [ ] `MAPS_API_KEY` restricted to `com.aistudio.ewaste.ind` + release SHA-1.
- [ ] Google OAuth client contains the release SHA-1.
- [ ] `OTP_MODE` decided: `demo` is **only** for supervised demos and never
      distributed publicly; `sms` is not implemented yet and fails closed.
- [ ] Release keystore created, backed up off-site, and injected via
      `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD`.
- [ ] `gradlew testDebugUnitTest assembleRelease bundleRelease` green.
- [ ] `apksigner verify --print-certs` shows the upload certificate.
- [ ] Signed APK installed on a physical device; smoke test passed
      (`TEST_REPORT.md §8`).
- [ ] `versionCode` incremented for the upload.

### 2.5 Sign-off

- [ ] All residual risks in `SECURITY_REVIEW.md §6` either closed or accepted
      in writing by the owner.
- [ ] Support contact and escalation path published.

---

## 3. Environments

| Environment | Supabase | Backends | App build |
| --- | --- | --- | --- |
| local | project (or local Supabase) | `localhost` / `10.0.2.2` | debug |
| staging | separate project recommended | staging host + staging secrets | release, signed with upload key |
| production | `ekqatebpbvxwjohfhipf` | production host | release, signed |

Never point a debug build with `OTP_MODE=demo` at production data.

---

## 4. Monitoring

### Signals that must page

| Signal | Threshold (starting point) | Why |
| --- | --- | --- |
| NestJS 5xx rate | > 2 % for 5 min | auth/voice outage |
| Auth failures (`401`/`403`) | > 20 % of auth calls for 10 min | credential stuffing or guard misconfig |
| `/api/auth/health` `supabaseConfigured=false` | any | env regression |
| FastAPI `/health` `whisperReady=false` | > 15 min | voice degradation (app still works locally) |
| FastAPI 413/429 spikes | any sustained | abuse or bad client |
| Supabase DB CPU / connections | > 80 % for 10 min | capacity |
| Supabase auth error rate | > 5 % | provider issue |
| Android crash-free sessions | < 99 % | release regression |

### Signals to watch (no page)

- `profiles` rows with `role = formal_recycler` and
  `account_status = pending_verification` older than 7 days → verification
  backlog.
- Storage growth of `lot-photos`.
- Disk usage of the Whisper model cache.

### Log rules

- Log request id, route, status, latency. **Never** log `Authorization`,
  passwords, OTPs, or full request bodies for auth endpoints.
- Keep 30 days hot, 90 days cold.

---

## 5. Deploy procedure (backends)

1. Confirm `main` is green (all three suites).
2. Build the NestJS image/artifact from a tagged commit.
3. Deploy to staging; run the smoke checks:
   - health endpoint
   - sign-in with a staging user → JWT returned
   - `POST /api/voice/intent` with token → 200, without → 401
   - FastAPI `/health` reachable from NestJS network only
4. Deploy to production with a rolling restart (min 1 healthy instance).
5. Re-run the smoke checks against production.
6. Watch the dashboards for 30 minutes.

Database migrations: apply during a low-traffic window. `0007` and `0008` are
additive and idempotent. Back up first (PITR or a manual snapshot).

---

## 6. Rollback

### Backend

- Redeploy the previous tagged image. No data migration is required for the
  current release.
- If auth breaks and the cause is env: fix env and restart; do **not** disable
  the guard to "get people in".

### Database (`0007`, `0008`)

Follow `SUPABASE_SETUP.md §7`. Rolling back `0007` re-opens the
self-assigned-admin and unverified-recycler issues. Rolling back `0008` removes
price history/ownership protections; drop the new columns/table only after
exporting `material_price_history`. Prefer fixing forward.

### Android

- You cannot roll back an installed APK. Publish a higher `versionCode` with the
  fix. If the release is broken at the server level, keep the previous app
  version working by not changing the API contract.

### Voice service

- Set `WHISPER_DISABLE_MODEL=1` to stop inference while keeping health checks.
  Transcription returns 503; the app falls back to the local classifier.
- If the service is being abused, remove it from the network path entirely;
  the app remains functional offline.

---

## 7. Incident response

1. **Detect** — alert or user report.
2. **Triage** — which service? Check health endpoints and dashboards.
3. **Contain**
   - leaked credential → rotate immediately; invalidate sessions in Supabase
     (Authentication → Users → sign out all, or revoke refresh tokens).
   - abusive traffic → enable/adjust edge rate limit or block the source.
   - bad deploy → roll back (§6).
4. **Eradicate** — remove the cause (patch, config fix, key rotation).
5. **Recover** — verify with the smoke checks.
6. **Review** — write a short post-incident note: timeline, impact, root cause,
   action items with owners.

### Specific playbooks

**Suspected account takeover**
1. Supabase → Authentication → Users: revoke sessions for the account.
2. Set `profiles.account_status = 'suspended'`. RLS immediately treats the user
   as `anon` because `current_user_role()` returns `anon` for non-active rows.
3. Inspect `audit_logs` and Postgres logs for writes made by that user.

**Leaked service-role key**
1. Rotate the service-role key in the Supabase dashboard.
2. Restart every service that holds it (only ops tooling should).
3. Review database logs for unexpected `service_role` activity.
4. Confirm no client build contains it (`unzip -p app.apk | grep` on the
   `BuildConfig`/DEX is not trivially readable, but scan the repo and CI logs).

**Unverified recycler receiving data**
1. Set the account to `pending_verification` or `suspended`.
2. Verify the `authorized_recyclers` row; do not relax the registry check.
3. Review which lots were exposed and notify the data owner.

**Voice service returning wrong intents**
1. Check which path answered: `aiEngineSource` in the client result
   (`NestJS`, `FastAPI`, or local).
2. If FastAPI: check model version, language detection, and recent deploys.
3. Safe fallback: disable the backend path; the local classifier remains.

---

## 8. Backup and restore

| Data | Mechanism | Frequency | Restore test |
| --- | --- | --- | --- |
| Postgres | Supabase PITR or daily backup | continuous / daily | quarterly |
| Storage (`lot-photos`) | Supabase storage backup / external copy | daily | quarterly |
| Secrets | hosting secret store + offline vault | on change | on rotation |
| Release keystore | offline encrypted backup (≥ 2 copies) | once | verify password yearly |

Losing the upload keystore means you can never update the app under the same
identity. Treat the backup as a production asset.

Restore drill:
1. Restore the database into a **staging** project.
2. Point a staging backend at it.
3. Sign in with a staging user, list lots, upload a photo.
4. Record duration and any manual steps.

---

## 9. Routine operations

| Task | Frequency | Owner |
| --- | --- | --- |
| Review pending recycler verifications | daily | admin |
| Check error/alert dashboards | daily | on-call |
| Rotate secrets | quarterly or on suspicion | security |
| Apply dependency/security updates | monthly | eng |
| Restore drill | quarterly | ops |
| Review RLS policies after schema changes | every migration | eng |
| Check storage growth and cost | monthly | ops |

---

## 10. Capacity notes

- Whisper `base` on CPU int8: expect hundreds of ms to a few seconds per short
  clip; scale horizontally rather than raising concurrency on one box.
- FastAPI is stateless; NestJS is stateless. Both scale horizontally behind the
  proxy.
- The Android app is offline-first; a backend outage degrades voice accuracy and
  sync, not data capture.
- The debug APK is ~168 MB and the AAB ~80 MB, dominated by ONNX runtime and
  models. Play Store delivery splits by ABI; the installed size is smaller than
  the universal APK.

---

## 11. Contacts and ownership

Fill in before launch (placeholders — do not invent names):

| Role | Owner | Contact |
| --- | --- | --- |
| Product owner | TBD | TBD |
| On-call engineer | TBD | TBD |
| Supabase admin | TBD | TBD |
| Security contact | TBD | TBD |
| CPCB liaison (admin provisioning) | TBD | TBD |

---

## 12. Current status banner

> **Not production-ready yet.** The code hardening, tests and documentation are
> complete, but the live schema is not applied, credentials are not rotated, the
> release build is unsigned, and no deployment has occurred. See
> `KNOWN_LIMITATIONS.md` and `IMPLEMENTATION_STATUS.md`.
