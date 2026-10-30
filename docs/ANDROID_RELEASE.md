# ECOBRIDGES â€” Android Release

**Application ID:** `com.aistudio.ewaste.ind`
**Version:** `versionCode 1`, `versionName "1.0"`
**minSdk 24 Â· targetSdk 36 Â· compileSdk 36.1**
**Build system:** Gradle (AGP 9.1.1, Kotlin 2.2.0)

---

## 1. Build-time configuration

The app reads `.env` in the repository root through the Secrets Gradle Plugin.

```bash
cp .env.example .env
# edit .env with real values
```

Required/known keys:

| Key | Required | Notes |
| --- | --- | --- |
| `SUPABASE_URL` | yes | `https://ekqatebpbvxwjohfhipf.supabase.co` |
| `SUPABASE_ANON_KEY` | yes | publishable/anon key only |
| `GOOGLE_WEB_CLIENT_ID` | for Google sign-in | web client id |
| `MAPS_API_KEY` | for maps | restrict in Cloud Console |
| `GEMINI_API_KEY` | optional | on-device scanner works without it |
| `OTP_MODE` | yes | `demo` for the SIH demo, `sms` only after a provider exists |

Rules:
- Never put a service-role key in `.env` â€” it is packaged into the APK.
- `GROK_API_KEY` / `XAI_API_KEY` are on the Secrets-plugin ignore list and are
  not packaged, even if present.
- Keep `.env` to `KEY=VALUE` lines plus `#` comments; the plugin mis-parses
  `# KEY: text` lines and generates invalid Java.

---

## 2. Build commands

```bash
# debug (includes the clearly-labelled Demo OTP flow)
./gradlew assembleDebug

# unit tests
./gradlew testDebugUnitTest

# release artifacts
./gradlew assembleRelease bundleRelease
```

Windows: `gradlew.bat ...`.

Verified on 2026-10-29:

| Task | Result |
| --- | --- |
| `testDebugUnitTest` | 63 tests, 0 failures, 1 skipped |
| `assembleDebug` | `BUILD SUCCESSFUL` |
| `assembleRelease` + `bundleRelease` | `BUILD SUCCESSFUL` (unsigned) |

---

## 3. Artifacts (this build)

| Artifact | Path | Size | Signed |
| --- | --- | --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 176,736,892 bytes (168.55 MiB) | debug key |
| Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | 165,091,414 bytes (157.44 MB) | **no** |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` | 83,615,632 bytes (79.74 MB) | **no** |

The large size is dominated by the bundled ONNX runtime/models and Maps/Compose
libraries.

The release APK filename (`-unsigned`) and the build log line
`Release signing is not configured ... Release artifacts will be unsigned.`
confirm that no signing identity was used. **This artifact cannot be installed
or uploaded as-is.**

---

## 4. Signing (operator action â€” not performed)

A signing identity must not be generated or shared without the owner's approval.

1. Create an upload keystore once, on a trusted machine:

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

2. Store it outside the repository and back it up securely.
3. Set the environment variables for the build (never commit them):

```bash
KEYSTORE_PATH=/secure/path/my-upload-key.jks
KEY_ALIAS=upload
STORE_PASSWORD=***        # from the secret store
KEY_PASSWORD=***          # from the secret store
```

4. Rebuild:

```bash
./gradlew clean assembleRelease bundleRelease
```

5. Verify the signature (Android SDK build-tools):

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Expected: `Verified using v2 scheme ...` and the upload certificate.

`*.jks` and `*.keystore` are git-ignored. `debug.keystore` is explicitly
allowed for debug builds only.

---

## 5. Release checklist

- [ ] `OTP_MODE` is appropriate for the audience; demo builds must not be
      distributed publicly
- [ ] `SUPABASE_URL` points at `ekqatebpbvxwjohfhipf`
- [ ] schema + migrations applied and RLS verified
- [ ] release keystore configured and backed up
- [ ] `versionCode` incremented for every upload
- [ ] Maps key restricted to `com.aistudio.ewaste.ind` + the release SHA-1
- [ ] Google OAuth client includes the release SHA-1
- [ ] `gradlew testDebugUnitTest` green
- [ ] install the signed APK on a physical device and run the auth + offline sync
      smoke test
- [ ] confirm cleartext HTTP is blocked in release (only the debug manifest
      enables it)
- [ ] store the mapping/proguard files if minify is ever enabled (currently
      disabled)

---

## 6. Install (debug / internal)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Installed and launched on a physical **vivo V2336 (Android 16, arm64-v8a,
1080x2400)** on 2026-10-29. Install succeeded and the app started with no
crash; the demo collector/recycler/admin flows were exercised on the device (see
`docs/TEST_REPORT.md §7`).

For an unsigned release APK, sign it first:

```bash
apksigner sign --ks my-upload-key.jks --out app-release-signed.apk app-release-unsigned.apk
adb install -r app-release-signed.apk
```

---

## 7. Behaviour differences by build type

| Behaviour | Debug | Release |
| --- | --- | --- |
| Demo OTP sign-in | allowed (labelled "Demo OTP") | **disabled** |
| Cleartext HTTP to local backends | allowed (debug manifest) | blocked |
| Logging verbosity | verbose | normal |
| R8/minify | off | off (kept off pending device regression testing) |
| `android:debuggable` | true | false |

A release build with `OTP_MODE=demo` refuses demo sign-in and reports the
provider as not configured. This is intentional: there is no verified credential
behind the demo flow.

---

## 8. Known release blockers

1. No signing identity â†’ artifacts are unsigned.
2. Supabase schema not applied to the live project â†’ real sign-in cannot be
   completed end-to-end.
3. No real OTP/SMS provider.
4. Device E2E is partial: demo auth, price CRUD, validation, 20% confirmation,
   price history, offers, ownership, one-offer rule and the collector board were
   run on a physical device; real Supabase sign-in, multi-device sync and
   handover snapshots were not (see `docs/TEST_REPORT.md §7`).
5. `GEMINI_API_KEY` / `MAPS_API_KEY` are placeholders in the current `.env`; AI
   capture falls back to the on-device ONNX detector and the map may not render.
