# ECOBRIDGES — Deployment & Configuration Guide

## 1. Environment Variables by Component

All environment variables use standard variable names and placeholders. Real secret values must never be checked into version control or printed in logs.

### 1.1 NestJS Backend (`backend/nestjs/.env`)
```env
PORT=3000
NODE_ENV=production
CORS_ORIGINS=https://your-frontend-domain.com,http://localhost:3000

# Supabase Connectivity (Target project: ekqatebpbvxwjohfhipf)
SUPABASE_URL=https://ekqatebpbvxwjohfhipf.supabase.co
SUPABASE_ANON_KEY=<SUPABASE_ANON_KEY>
SUPABASE_SERVICE_ROLE_KEY=<SUPABASE_SERVICE_ROLE_KEY>
SUPABASE_JWT_SECRET=<SUPABASE_JWT_SECRET>

# Demo Authentication (Hackathon Evaluation)
DEMO_LOGIN_ENABLED=true
DEMO_OTP_CODE=123456
DEMO_ENABLE_ADMIN=true
DEMO_JWT_SECRET=<32_PLUS_CHAR_RANDOM_SECRET>
DEMO_ACCOUNTS=9876543210:collector,9876543211:recycler,9876543212:admin
```

### 1.2 FastAPI Voice & Intent Engine (`backend/fastapi/.env`)
```env
PORT=8000
ENVIRONMENT=production
CORS_ORIGINS=https://your-frontend-domain.com,http://localhost:3000
WHISPER_MODEL_SIZE=base
MAX_AUDIO_BYTES=10485760
MAX_TRANSCRIPT_CHARS=2000
VOICE_API_KEY=<OPTIONAL_VOICE_API_KEY>
```

### 1.3 Android Client (`app/.env` / `local.properties`)
```env
SUPABASE_URL=https://ekqatebpbvxwjohfhipf.supabase.co
SUPABASE_ANON_KEY=<SUPABASE_ANON_KEY>
BACKEND_BASE_URL=http://<SERVER_IP_OR_DOMAIN>:3000/
VOICE_BACKEND_URL=http://<SERVER_IP_OR_DOMAIN>:8000/
DEMO_LOGIN_ENABLED=true
DEMO_OTP_CODE=123456
```

---

## 2. Server Deployment Instructions

### 2.1 NestJS Backend (Node.js 20+)
1. Install dependencies:
   ```bash
   cd backend/nestjs
   npm install --omit=dev
   ```
2. Build the TypeScript application:
   ```bash
   npm run build
   ```
3. Start in production mode:
   ```bash
   npm run start:prod
   ```
4. Verify health probes:
   - `GET /api/prices/health` → `{"status":"ok","policy":"reference-and-offer-v2"}`
   - `GET /api/auth/health` → `{"status":"ok","supabaseConfigured":true}`

### 2.2 FastAPI AI Service (Python 3.10+)
1. Install requirements:
   ```bash
   cd backend/fastapi
   pip install -r requirements.txt
   ```
2. Start using Uvicorn:
   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000 --workers 2
   ```
3. Verify health probe:
   - `GET /health` → `{"status":"ok"}`

---

## 3. Configuring the Android App for Physical Devices

By default, development configurations use `10.0.2.2`, which is the Android emulator's alias for the host workstation.

When deploying or testing on a physical Android device:
1. Ensure the backend host is reachable on the local network (or public domain).
2. Configure the server IP/URL in `BackendApiClient`:
   ```kotlin
   // In application initialization (e.g. MainActivity or App class):
   com.example.network.BackendApiClient.configureEndpoint("http://192.168.1.X:3000")
   com.example.network.BackendVoiceClient.configureEndpoints(
       nestUrl = "http://192.168.1.X:3000",
       fastApiUrl = "http://192.168.1.X:8000"
   )
   ```
3. Build the APK:
   - Debug: `.\gradlew.bat assembleDebug`
   - Release: Configure `release.keystore` and run `.\gradlew.bat assembleRelease bundleRelease`
