# ECOBRIDGES ♻️

### Kabadiwala Connect --- Bringing the Informal Collector into the Formal Recycling Chain

> **Smart India Hackathon 2026** · **PS ID:** 26229 · **Theme:** Clean &
> Green Technology · **Category:** Software

ECOBRIDGES is a digital e-waste collection and recycling platform
designed to connect **informal e-waste collectors** with **authorized
recyclers** through an accessible, transparent and traceable workflow.

## 🎯 Problem

Informal e-waste collectors can face gaps in: - Material/value
information - Local price information - Access to authorized recyclers -
Pickup and handover coordination - Proof of payment and transaction
history - Digital traceability

## 💡 Solution

ECOBRIDGES combines offline collection, AI-assisted material
identification, price intelligence, authorized-recycler matching and
transaction traceability.

``` text
E-Waste Generator
      ↓
Informal Collector
      ↓
Create E-Waste Lot
(Photo + Category + Weight)
      ↓
YOLOv8n Material Identification
      ↓
Price Intelligence
      ↓
Authorized Recycler Matching
      ↓
Quote / Pickup / Handover
      ↓
Final Weight + Payment
      ↓
Traceability & Recycling Status
```

## 🚀 Key Features

### 1. AI-Assisted Identification

Collectors can capture an e-waste image and receive an AI-assisted
material/category prediction using **YOLOv8n**.

``` text
Image → YOLOv8n → Material/Category + Confidence
                         ↓
                 Low confidence?
                    ↙       ↘
                  Yes        No
                   ↓          ↓
             Collector      Use
             confirmation   result
```

### 2. Price Intelligence

-   Location-based price information
-   Current price table
-   Historical price trends
-   Weight-based value estimation

The AI identifies the material/category; the pricing layer can use
material, weight, location and available price data to estimate value.

### 3. Authorized Recycler Matching

Collectors can be matched with suitable recyclers using: -
Material/category - Location - Authorization status - Offered rate -
Pickup availability

### 4. End-to-End Traceability

A lot can record: - Photo - Category/material - Weight - Timestamp -
Location - Recycler - Handover confirmation - Final weight - Payment
status - Recycling/transaction status

### 5. Offline-First Operation

Important collector-side information can be stored locally and
synchronized when connectivity returns.

``` text
Create Lot → Local Queue → Network Available → API Sync → Database
```

### 6. Inclusive Access

The proposed platform supports: - Android app - USSD - IVR - Pictorial
guidance - Audio guidance - Feature-phone access

### 7. Government/Admin Monitoring

Admin capabilities include: - Recycler authorization verification -
Collector/recycler management - Transaction monitoring - Traceability
monitoring - Analytics - Abnormal transaction detection - Reports -
Policy/EPR monitoring

## 🏗️ Architecture

``` text
┌─────────────────────────────────────────┐
│              USER INTERFACES            │
│ Collector App | Recycler | Admin | USSD │
│                    IVR                  │
└───────────────────┬─────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│              NestJS BACKEND             │
│ Auth | Lots | Pricing | Matching        │
│ Transactions | Traceability | Admin     │
└───────────────┬───────────────┬─────────┘
                ↓               ↓
     ┌────────────────┐   ┌────────────────┐
     │ Supabase       │   │ AI Service     │
     │ PostgreSQL     │   │ FastAPI        │
     │ Users / Lots   │   │ YOLOv8n        │
     │ Transactions   │   │ Inference      │
     └────────────────┘   └────────────────┘
```

## 🛠️ Technology Stack

  Layer          Technology
  -------------- ----------------------------
  Frontend       React.js, TypeScript
  Backend        NestJS, TypeScript
  Database       Supabase PostgreSQL
  AI Service     Python, FastAPI
  AI Model       YOLOv8n
  Development    GitHub
  Access         Android, USSD, IVR
  Traceability   QR-based handover workflow

## 👥 User Roles

### Informal Collector

``` text
Login → Create Lot → Capture Image → AI Identification
→ Price Information → Recycler Matching → Pickup
→ Handover → Payment → Transaction History
```

### Formal Recycler

``` text
Registration → Submit Authorization → Admin Verification
→ Receive Lot Request → Quote → Accept/Pickup
→ Verify Material & Weight → Handover → Payment
→ Update Recycling Status
```

### Government/Admin

``` text
Login → Verify Recyclers → Manage Users
→ Monitor Transactions → Track Traceability
→ Analytics → Anomaly Detection → Reports
```

## 📊 Impact Measurement

The pilot should measure actual outcomes rather than relying only on
general claims.

  Area            KPI
  --------------- ---------------------------------------------------
  Collector       Change in realized selling price vs baseline
  Collector       \% lots receiving a verified recycler quote
  Formalization   \% lots routed to authorized recyclers
  Traceability    \% transactions with complete records
  Operations      Median lot-to-pickup time
  Offline         \% offline-created lots successfully synchronized
  Environment     Kg of e-waste routed to authorized recycling

## 🧪 Pilot Validation

The SIH deck proposes field validation with **≥2 collectors and 1
recycler**.

A useful pilot flow is:

``` text
Baseline → Create Lot → AI Identification → Price Discovery
→ Recycler Match → Handover → Traceability → Measure KPIs
```

Record the actual results, task completion, time taken and traceability
completion instead of only reporting that testing occurred.

## ⏱️ 36-Hour MVP

### Build Now

-   Collector authentication
-   E-waste lot creation
-   Photo + category + weight
-   YOLOv8n identification
-   Confidence-based confirmation
-   Offline storage and synchronization
-   Price table/history
-   Authorized recycler matching
-   Recycler verification
-   Handover tracking
-   Payment status
-   Basic admin monitoring

### Post-MVP

-   Production USSD
-   Production IVR
-   Larger recycler network
-   Advanced anomaly detection
-   Automated payment integrations
-   Expanded AI training
-   Large-scale deployment

## 📁 Repository Structure

``` text
ECOBRIDGES/
├── frontend/
├── backend/
├── ai-service/
├── supabase/
├── docs/
└── README.md
```

## ⚙️ Local Development

> Update commands/entry points below if the repository scripts differ.

### Clone

``` bash
git clone https://github.com/harisiva-1117/ECOBRIDGE.git
cd ECOBRIDGE
```

### Frontend

``` bash
cd frontend
npm install
npm run dev
```

### Backend

``` bash
cd backend
npm install
npm run start:dev
```

### AI Service

``` bash
cd ai-service
python -m venv .venv
```

Windows:

``` bash
.venv\Scripts\activate
```

Linux/macOS:

``` bash
source .venv/bin/activate
```

``` bash
pip install -r requirements.txt
```

## 🔐 Environment Variables

Never commit real credentials.

Example:

``` env
# Frontend
VITE_API_URL=

# Backend
DATABASE_URL=
SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=

# AI Service
AI_SERVICE_URL=
```

## 🔗 Project

**GitHub:** https://github.com/harisiva-1117/ECOBRIDGE

**Demo:** Add the final working demo URL here.

## 🏆 Smart India Hackathon

**Problem Statement:** 26229 --- Kabadiwala Connect\
**Theme:** Clean & Green Technology\
**Category:** Software\
**Team:** ECOBRIDGES

ECOBRIDGES aims to create a practical digital bridge between informal
e-waste collection and the formal recycling ecosystem through digital
inclusion, AI-assisted identification, price intelligence and traceable
transactions.

## 📌 Development Priorities

1.  Complete the collector lot workflow.
2.  Integrate YOLOv8n inference.
3.  Implement confidence-based confirmation.
4.  Complete offline synchronization.
5.  Implement recycler authorization verification.
6.  Complete recycler matching.
7.  Complete handover and payment-status tracking.
8.  Validate with collectors and a recycler.
9.  Measure pilot KPIs.
10. Keep the hackathon MVP focused.

------------------------------------------------------------------------

**ECOBRIDGES --- Connecting informal e-waste collection with the formal
recycling chain.**
