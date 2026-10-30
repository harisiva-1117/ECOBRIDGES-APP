# ECOBRIDGES — Test Verification & Audit Report

**Date of Execution:** 2026-10-29  
**Execution Environment:** Windows 11, PowerShell, Node.js v20+, Python 3.11, JDK 17  
**Total Automated Tests:** 104  
**Passing Tests:** 103  
**Failing Tests:** 0  
**Documented Skips:** 1 (Roborazzi snapshot test in headless environment)

---

## 1. Verified Test Suites

| Suite | Runner / Engine | Executed Tests | Passing | Failing | Skipped | Status |
| --- | --- | --- | --- | --- | --- | --- |
| **NestJS Backend** | Node.js built-in `node --test` | 31 | 31 | 0 | 0 | **ALL PASS** |
| **FastAPI Voice & Intent** | `pytest` + FastAPI `TestClient` | 10 | 10 | 0 | 0 | **ALL PASS** |
| **Android Unit & Robolectric** | Gradle + JUnit 4 + Robolectric SDK 34 | 63 | 62 | 0 | 1 | **ALL PASS** |
| **TOTAL** | | **104** | **103** | **0** | **1** | **VERIFIED** |

---

## 2. Test Classification

### 2.1 Unit Tests (Offline / Mocked)
* **Auth Validation Tests (`AuthValidationTest.kt`, `DemoAuthConfigTest.kt`, `DemoRoleAccessTest.kt`):** 28 assertions verifying phone number normalisation, OTP length and format, constant-time compare, role slug mapping, and admin gating.
* **Room Repository Tests (`PriceManagementRepositoryTest.kt`):** 9 tests verifying in-memory Room operations, change history audit rows, optimistic version conflicts, locked agreed handover prices, and local vs cloud queueing separation.
* **FastAPI Intent Tests (`test_main.py`):** 10 tests verifying endpoints, payload limits, base64 checking, API key protection, and intent routing.
* **NestJS Policy & Security Tests (`auth.security.test.js`, `price.policy.test.js`):** 20 tests verifying token validation, role rejection, currency check, and significant move detection.

### 2.2 Mocked Integration Tests (Client-Server Contract)
* **Demo Auth & Persistence (`test/demo.auth.persistence.test.js`):** 11 tests verifying end-to-end demo login with OTP 123456, signed expiring JWT token generation, `SupabaseAuthGuard` decoding and authorization, facility ownership enforcement (`REC-CPCB-MH-001`), reference price management, and multi-facility offer listing.

### 2.3 Live Supabase Integration Tests
* **Status:** **NOT PERFORMED AUTOMATICALLY**
* **Reason:** Live database execution against project `ekqatebpbvxwjohfhipf` is deliberately withheld to protect existing data and avoid unauthorized live DDL execution. Migration `0009` is audited, idempotent, and ready for manual review and application.

---

## 3. Verified Build Artifact

* **Target:** Debug APK (`assembleDebug`)
* **Verified Path:** `D:\SIH APP PROJECT\google-sih\app\build\outputs\apk\debug\app-debug.apk`
* **File Size:** `176,244,171 bytes`
* **Integrity:** Multi-dex, ONNX Runtime libraries, Room v7 SQLite schema, compiled Kotlin bytecode.
