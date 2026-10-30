# ECOBRIDGES — Backend Deployment

Two services:

| Service | Path | Public? | Purpose |
| --- | --- | --- | --- |
| NestJS API | `backend/nestjs` | yes (behind TLS) | Supabase auth bridge, JWT verification, voice intent |
| FastAPI voice | `backend/fastapi` | **no** (internal) | Whisper STT + NLP intent |

Supabase itself is managed; see `SUPABASE_SETUP.md`.

**Status:** no deployment has been performed. This document describes the
verified build/run commands plus the steps that still require infrastructure.

---

## 1. Requirements

- Node.js ≥ 20 (verified with Node 24.17.0)
- Python 3.11 (verified) — 3.11 avoids the pre-release 3.13 dependency gaps
- A TLS-terminating reverse proxy (nginx, Caddy, cloud LB)
- Network access from NestJS to Supabase and to FastAPI
- Secret storage on the host/CI (never `.env` committed to git)

---

## 2. Environment variables

### NestJS (`backend/nestjs/.env`, untracked)

| Variable | Required | Notes |
| --- | --- | --- |
| `PORT` | no (3000) | HTTP port |
| `SUPABASE_URL` | **yes** | `https://<ref>.supabase.co` |
| `SUPABASE_ANON_KEY` | **yes** | publishable key; used for user-scoped calls |
| `SUPABASE_JWKS_URL` | no | defaults to `<SUPABASE_URL>/auth/v1/.well-known/jwks.json` |
| `SUPABASE_JWT_ISSUER` | no | defaults to `<SUPABASE_URL>/auth/v1` |
| `SUPABASE_JWT_AUDIENCE` | no (authenticated) | expected `aud` |
| `SUPABASE_JWT_SECRET` | only for legacy HS256 | leave unset for asymmetric JWTs |
| `CORS_ORIGINS` | **yes in production** | comma-separated HTTPS origins; the app refuses to boot without it |
| `FASTAPI_VOICE_URL` | no | internal FastAPI base URL |
| `VOICE_API_KEY` | if FastAPI requires it | sent to FastAPI as `X-API-Key` |
| `NODE_ENV` | yes | `production` enables the CORS requirement |

`SUPABASE_SERVICE_ROLE_KEY` is **not required** by this service. Auth runs with
the caller's token under RLS. Keep a service key only in the ops environment
used for admin provisioning (SQL Editor / one-off scripts), never in the API.

### FastAPI (`backend/fastapi/.env`, untracked)

| Variable | Required | Notes |
| --- | --- | --- |
| `CORS_ORIGINS` | no | empty = no browser origins allowed |
| `MAX_AUDIO_BYTES` | no (10 MiB) | upload cap |
| `MAX_TRANSCRIPT_CHARS` | no (2000) | intent input cap |
| `VOICE_API_KEY` | recommended | when set, business endpoints require `X-API-Key` |
| `WHISPER_MODEL_SIZE` | no (base) | tiny/base/small/medium/large-v3 |
| `WHISPER_DEVICE` | no (cpu) | `cpu` or `cuda` |
| `WHISPER_COMPUTE_TYPE` | no (int8) | `int8`, `float16`, `float32` |
| `WHISPER_DISABLE_MODEL` | no | `1` = health-only mode (transcription 503) |
| `HOST` / `PORT` | no | `python main.py` defaults to 127.0.0.1:8000 |

---

## 3. Build and run — NestJS

```bash
cd backend/nestjs
npm ci                     # or: npm install
npm run build              # tsc -> dist/
NODE_ENV=production node dist/main
```

Verified locally: `npx tsc -p tsconfig.json --noEmit` exit 0 and `npm test`
20/20 pass. The service listens on `PORT` (default 3000).

Health probe:

```bash
curl -s http://127.0.0.1:3000/api/auth/health
# {"status":"ok","supabaseConfigured":true}
```

> `supabaseConfigured` is `false` when `SUPABASE_URL` is missing. Auth calls
> still return `503` rather than accepting anyone — that is intentional.

### Price validation endpoints

These are read-only validators; the actual write still goes through PostgREST
under RLS. Both require a verified Supabase bearer token.

| Endpoint | Role | Purpose |
| --- | --- | --- |
| `GET /api/prices/health` | none | policy version probe |
| `POST /api/prices/reference/validate` | `government_admin` | validate a reference-price edit; returns `valid`, `errors`, `significantChange` |
| `POST /api/prices/offers/validate` | `formal_recycler` | validate an offer; rejects an offer for a facility the caller does not own |

```bash
curl -s -X POST http://127.0.0.1:3000/api/prices/reference/validate \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"category":"PCB_BOARDS","materialName":"Motherboard","pricePerKg":120,"marketMin":100,"marketMax":150}'
# {"valid":true,"errors":[],"significantChange":false}
```

Negative checks: a collector token gets `403`; a recycler posting
`recyclerId` that does not match their profile gets `403`.

### Process supervision (example, not provided as a file)

Run it under systemd, a container orchestrator, or PM2. Restart on failure, keep
at least 1 replica, set memory limits, and capture stdout/stderr. Do not run as
root.

### Reverse proxy

Only NestJS should be internet-facing.

```nginx
server {
  listen 443 ssl http2;
  server_name api.example.org;

  location / {
    proxy_pass http://127.0.0.1:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
}
```

Set `CORS_ORIGINS` to the exact web origins that call the API (for the Android
app CORS is irrelevant). Android requires HTTPS in release builds.

---

## 4. Build and run — FastAPI

```bash
cd backend/fastapi
python -m venv .venv
# Windows: .venv\Scripts\activate    Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000
```

First start downloads/loads the Whisper model; allow time and disk space. In a
model-less environment set `WHISPER_DISABLE_MODEL=1`: `/health` works and
transcription returns 503 instead of inventing text.

Do **not** expose port 8000 publicly. NestJS calls
`POST /api/v1/voice/semantic-intent` (see `voice-intent-router.service.ts`) with a
3.5 s timeout and falls back to its own parser if FastAPI is unreachable. If you
set `VOICE_API_KEY` on FastAPI, set the same value on NestJS so its outbound
calls carry `X-API-Key`; otherwise every call returns 401 and the backend
silently uses the local parser. Also set a firewall rule so only the NestJS host
can reach 8000. The Android client has a legacy direct-call path
(`BackendVoiceClient.queryFastApiNlpIntent`) that should be removed or disabled
in any real deployment.

Test:

```bash
pip install -r requirements-dev.txt
python -m pytest -q     # 10 passed
```

---

## 5. Deployment checklist

- [ ] Supabase schema applied (`schema.sql` + `0001..0008`) and verified
- [ ] All leaked credentials rotated
- [ ] `SUPABASE_URL` / `SUPABASE_ANON_KEY` set on the host
- [ ] `CORS_ORIGINS` set to real origins; `NODE_ENV=production`
- [ ] TLS certificate valid; HTTP redirects to HTTPS
- [ ] `GET /api/auth/health` returns `supabaseConfigured: true`
- [ ] A real user can sign in and receives a Supabase JWT
- [ ] `POST /api/voice/intent` returns 401 without a token and 200 with one
- [ ] `POST /api/prices/reference/validate` is 403 for a collector and 200 for an admin
- [ ] `POST /api/prices/offers/validate` refuses an offer for another facility
- [ ] FastAPI reachable only from the intended network
- [ ] Logs do not contain tokens, passwords or OTPs
- [ ] Rate limits configured at the edge
- [ ] Backups / PITR enabled on Supabase
- [ ] Alerting on 5xx rate and auth failure spikes

---

## 6. Verifying the JWT guard end-to-end

```bash
TOKEN="<supabase access token from a real sign-in>"
curl -s -X POST https://api.example.org/api/voice/intent \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"transcript":"copper wire ka rate kya hai","context":{"currentScreen":"COLLECTOR_DASHBOARD","activeLanguage":"en"}}'
```

Negative checks:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://api.example.org/api/voice/intent \
  -H "Content-Type: application/json" -d '{"transcript":"hi"}'          # 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://api.example.org/api/voice/intent \
  -H "Authorization: Bearer not.a.jwt" -H "Content-Type: application/json" -d '{"transcript":"hi"}'  # 401
```

---

## 7. Common failures

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Authentication provider is not configured` (503) | env missing | set `SUPABASE_URL` + `SUPABASE_ANON_KEY` |
| Boot error about `CORS_ORIGINS` | production without allow-list | set `CORS_ORIGINS` |
| All tokens rejected | wrong project / issuer / JWKS URL | align env with the Supabase project |
| Every voice call 401 | client not sending the token | add `Authorization` header |
| FastAPI 503 on transcribe | model not loaded | check `WHISPER_*`, disk, network |
| FastAPI 413 | upload over `MAX_AUDIO_BYTES` | raise the cap or compress audio |
