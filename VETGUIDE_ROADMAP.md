# VETGUIDE_ROADMAP

**Status:** DRAFT — pending owner review and approval
**Owner:** @Prudent66 · **Created:** 2026-11-05 · **Last revised:** 2026-11-06 · **Version:** 1.1-draft (owner amendments 1–6 applied)
**Scope:** This is the official development plan. No application features are implemented until phases are approved and executed in order.

---

## 1. Project Vision, Objectives & Target Users

### 1.1 Vision
VETGUIDE is an AI-powered veterinary clinical assistant for Android that helps veterinarians and veterinary students investigate animal health problems through image analysis, guided diagnostic questionnaires, and an evidence-based knowledge base — built for practical clinical support, offline access, and informed veterinary decision-making.

### 1.2 Objectives
1. Reduce time-to-differential-diagnosis for common presentations in dogs and cats (expanding to more species later).
2. Provide evidence-based guidance with traceable sources for every clinical claim.
3. Work reliably in low-connectivity environments (field work, rural clinics) via an offline-first design.
4. Produce professional clinical documentation (SOAP notes, PDF case reports) that slots into real workflows.
5. Keep operating costs at zero during development and early rollout (strict free-tier-first policy).

### 1.3 Target Users
| User | Primary use |
|---|---|
| Field/clinic veterinarians | Bedside differential diagnosis, image triage, case documentation |
| Veterinary students | Learning tool: guided questionnaires teach structured diagnostic thinking |
| Rural/mobile practitioners | Offline knowledge base and questionnaire engine without connectivity |
| (Later) Animal welfare/rescue workers | Triage-level urgency flags, non-specialist-safe UX |

### 1.4 Explicit non-goals
- VETGUIDE does **not** replace veterinary judgment; it supports it (clinically safe framing everywhere).
- No telemedicine appointments, no e-commerce, no prescription fulfillment in v1.
- No livestock/exotic-species coverage in v1 — dog/cat first, dataset-driven expansion.

---

## 2. Android Application Architecture & UI/UX

### 2.1 Stack (locked)
- **Language/UI:** Kotlin, Jetpack Compose, Material 3 (dynamic color optional, themed fallback)
- **Architecture:** MVVM + layered clean architecture (`ui` / `domain` / `data`)
- **DI:** Hilt
- **Persistence:** Room (source of truth) + DataStore for preferences
- **Minimum SDK:** 26 · **Target/compile:** latest stable
- **Async:** Kotlin Coroutines + Flow throughout; no blocking main thread

### 2.2 Module/UI structure
```
app/
  ui/        Compose screens, ViewModels, navigation (NavHost), theme
  domain/    Use cases, diagnostic scoring engine, pure Kotlin models
  data/      Repositories, Room DB & DAOs, AI clients, settings (DataStore)
  ai/        AiProvider abstraction + provider implementations + orchestrator
```
Key rule: **all AI and data access lives behind interfaces.** The hybrid online/offline
decision logic is centralized in one orchestrator class, never scattered in screens.

### 2.3 Navigation map (v1)
Home → { Diagnose (camera/questionnaire), Cases (list → detail), Knowledge Base (search → article), Settings }
Bottom navigation bar, single Activity, Compose Navigation.

### 2.4 UI/UX principles
- Patient-safety-first affordances: urgency/emergency results are unmissable (color + icon + copy).
- Every AI output shows its **provenance** (provider, model, sources; confidence handling per §10). Deterministic questionnaire outputs are labeled "rule-based results," visually distinct from AI predictions (§10).
- Offline state always visible; degraded-mode behaviors are explicit, never silent.
- One-handed use, large touch targets (clinical gloves), dark mode default-friendly for night calls.

---

## 3. Patient, Case & Clinical Record Management

### 3.1 Core entities
- **Patient:** species, breed, age/DOB, sex/neuter status, weight, owner (optional fields — clinic privacy-friendly).
- **Case:** links patient + presenting complaint + investigation artifacts + outcome.
- **Clinical record item:** images, questionnaire answers, AI analyses, SOAP note, follow-up notes, attached KB references.
- **Case status:** open → investigating → plan-set → resolved/closed (with outcome note).

### 3.2 Functionality
- Patient register (create/edit/soft-delete), case list with filters (species, status, date).
- Case timeline: chronological view of every artifact added to a case.
- Case detail: signalment banner, findings, differential shortlist, plan, outcome.
- Export: PDF case summary (see §12) and share sheet (PDF or text).
- Data safety: local encrypted-at-rest where feasible (SQLCipher evaluation in Phase 2), Android backup rules defined explicitly.

**Dependencies:** Phase 0→2 (project skeleton, data layer).
**Deliverables:** Patient + case CRUD, timeline, case detail, export hook.
**Acceptance:** A user can register a patient, open a case, attach an image + questionnaire result, follow up, and resolve the case — all offline, with data intact across app restarts.

---

## 4. AI Image Analysis & Guided Diagnostic Investigations

### 4.1 Guided diagnostic questionnaire (built first — deterministic core)
- Species → signalment capture (age, sex, breed, weight) → system-based symptom checklist → structured answers.
- Data model: decision-tree/symptom-weight matrix stored in Room + seeded from versioned JSON assets (auditable, offline).
- **Scoring weight governance (mandatory):** every scoring weight is **traceable, documented, and veterinary-reviewed** before release. Weights are curated data artifacts with recorded sources — not runtime guesses.
- Scoring engine (`domain`): weighted symptom-condition match → **ranked differential list**. The AI must **never invent scoring weights**, and numerical scores must **never be presented as clinically validated diagnostic probabilities** — they are rule-based ranking signals only.
- **Urgency classification (emergency / urgent / routine) is kept strictly separate from the diagnostic ranking** — computed and displayed as an independent safety flag, never fused into the score.
- "Why this result?" transparency screen: shows which answers drove each condition's score.

### 4.2 AI image analysis (built second — AI-assisted layer)
- Capture flow: camera (CameraX) + gallery picker; image pre-processing (resize/compress before upload; never original full-res to network).
- Analysis types v1: dermatological lesions, ocular findings, wound assessment (dog/cat focus).
- Output schema: structured JSON from the provider (findings list, suspected condition classes, confidence, recommended next steps) — validated and parsed defensively.

**Dependencies:** Questionnaire engine must exist before image analysis so both streams merge (Phase 4 → Phase 6).
**Deliverables:** End-to-end flow: pick presentation → questionnaire → (optional) images → merged ranked differentials.
**Acceptance:** (1) A scripted imaging test dataset (offline fixtures) produces correct structured output through the fake provider. (2) Questionnaire-only path produces a ranked shortlist with urgency flag. (3) Merged mode = union of both signal sources with provenance labels.

---

## 5. Multi-Provider AI Orchestration & Automatic Failover

### 5.1 Design
```
domain/ai/DiagnosisUseCase
        │
   AiOrchestrator  ← connectivity monitor, rate-limit tracker, cost guard
   │
   ├── execution mode A — cloud (routed via Secure Backend, §8):
   │    ├── CloudGeminiProvider      (primary)
   │    └── CloudAlternativeProvider (independent fallback — see §6)
   │
   ├── execution mode B — on-device:
   │    └── OnDeviceFallbackProvider (TFLite/MediaPipe classifier, offline;
   │         degraded, capability-limited tier — see rules below)
   │
   └── execution mode C — deterministic fallback (NOT an AiProvider):
        Guided Questionnaire + Offline Knowledge Base (§4.1, §9)
```

**Failover execution modes (explicit, in order):**
1. **Gemini (Cloud AI)** → 2. **Alternative Cloud AI** → 3. **On-Device AI** → 4. **Deterministic Questionnaire & Offline Knowledge Base**.
- Mode 4 is a **deterministic fallback, NOT an AI provider** — it never runs through the `AiProvider` interface, never carries AI provenance, and its outputs are labeled "rule-based results" (§10).
- On-device models (mode 3) are a **degraded, capability-limited tier**: their outputs must **not** be assumed to have validated diagnostic capabilities, are always surfaced with an explicit reduced-reliability banner, and are never presented on par with cloud-AI results.

### 5.2 Behaviors
- **Failover ladder (execution modes, §5.1):** 1. Gemini (Cloud AI) → 2. Alternative Cloud AI → 3. On-Device AI → 4. Deterministic Questionnaire & Offline Knowledge Base. Configured as a priority list in Settings, defaulting to that order. Mode 4 is a deterministic fallback, not an AI provider (§5.1).
- **Triggers for failover:** network error, HTTP 429/5xx, provider timeout (configurable, default 20 s), invalid/failed-schema response. Exponential backoff with jitter; max one attempt per provider per request by default.
- **Every response records provenance:** provider name, model id, latency, degraded flags — persisted with the case record.
- **Circuit breaker:** after N consecutive failures (default 3), a provider is benched for M minutes (default 5) with a status banner in Settings/Diagnose.

**Deliverables:** `AiProvider` interface + orchestrator unit-tested with fake providers.
**Acceptance:** A fault-injection test suite proves: (a) primary failure → automatic fallback returns a valid result; (b) all-cloud failure → on-device result, and its exhaustion → deterministic rule-based result (questionnaire/KB), each with the correct degraded/reduced-quality banner; (c) rate-limit circuit breaker trips and recovers.

---

## 6. Gemini Primary & Independent Alternative Providers

- **Primary:** Google **Gemini** (vision-capable model, current stable free tier) for image analysis and case narrative assistance.
- **Alternatives (independent vendor, implemented via interface):** one non-Google provider as secondary failover (e.g., OpenAI-compatible endpoint) + **on-device TFLite/MediaPipe** as the offline tier.
- Contract per call: provider receives an abstract request prompt + structured-output schema; orchestrator maps provider-specific quirks internally. Adding a new provider = one new `AiProvider` implementation, zero changes to UI/domain.
- Provider/model IDs and limits live in a single `AiCatalog` config (upgradeable as free tiers change).

**Deliverables:** Two cloud providers + one on-device provider behind the same interface.
**Acceptance:** Swapping which provider is "primary" is a settings change, not a code change; each provider passes the same contract conformance test.

---

## 7. Strict Free-Tier-First Policy (No Unapproved Paid Usage)

**Policy (binds all development):**
1. All AI usage defaults to **free tiers only**. No API call may intentionally exceed a free quota without the owner's explicit written approval.
2. **Backend-enforced quota guard:** the secure backend (§8) enforces, server-side: **free-tier-only model eligibility**, **provider-specific usage limits** (hard daily caps per provider), and a strict block on **unapproved paid billing or paid-model fallback**. If free eligibility for a provider/model cannot be verified, that provider is treated as **unavailable** and failover proceeds. The app may display remaining usage for transparency, but enforcement is never purely client-side.
3. **No paid accounts, credit cards, or billing-enabled projects** are to be attached to any API key used by VETGUIDE without owner approval.
4. CI/test environments use **fake/mock providers only** — never real billable endpoints.
5. Every AI feature ships with a "cost surface" note in its task: expected calls per user action, worst-case daily volume.

**Deliverables:** backend-enforced quota guard implementation + `docs/AI_QUOTAS.md` (per-provider free limits as of build date).
**Acceptance:** A backend test proving requests are blocked past the configured daily cap and that a provider with unverifiable free eligibility is treated as unavailable; audit of `AiCatalog` shows no provider configured above its published free tier.

---

## 8. Secure Backend & API Key Management

- **Architecture (mandatory):** all AI provider calls are routed through a secure backend:

  ```
  Android Application (APK) → Secure VETGUIDE Backend → AI Orchestrator → AI Providers
  ```

  The backend (Cloudflare Workers — **preferred choice, decision locked for Phase 4**; Firebase Functions listed only as an alternative) securely manages **all private provider credentials**. API keys exist only as backend secrets; **the APK will never contain private keys**.
- **Key rules:**
  - No API keys are stored, embedded, or entered in the Android application in any form — not in the APK, not in `local.properties`, not in client-side Settings, not in `EncryptedSharedPreferences`.
  - Keys never in git. GitHub Secrets for CI are **mock/test-only credentials**, used exclusively against fake providers.
  - `.gitignore` audited in Phase 0; a secret-scanning step (gitleaks) added to CI.
  - Backend secret encryption-at-rest and a key-rotation procedure are documented in `docs/SECURITY.md`.
- **Transport:** TLS only; the backend is the single outbound caller to AI providers; responses validated (timeout, size caps, schema validation) at both backend and device to limit prompt-injection/oversized-payload exposure.

**Acceptance:** `gitleaks` CI step passes; a code-review checklist confirms no literal keys in the repo; a scan of the built release artifact confirms it contains no provider credentials; a lost-key rotation procedure is documented in `docs/SECURITY.md`.

---

## 9. Offline Veterinary Knowledge Base & Local Functionality

- **Data:** versioned JSON seed (assets) → imported into Room on first launch/migration. Structure: `Condition`, `Symptom`, `Species`, `Treatment`, `Reference` (citation: title, publisher, year, URL), junctions for many-to-many.
- **Starter dataset (v1):** ~40–60 common dog/cat conditions with symptoms, urgent red-flags, treatments, and citations. Curated from public veterinary educational sources; every entry cites sources. **Accuracy matters more than quantity:** this figure is a soft target, not a quota — if a condition cannot be supported by verified species-specific references, it is dropped rather than filled with unverified content.
- **No AI-generated clinical content (mandatory):** unverified medication doses, treatment protocols, or scientific citations must **never** be generated by AI to fill missing information. Every entry must carry **verified, species-specific references** (title, publisher, year, URL) checked against the actual source before import; entries that cannot be verified are excluded.
- **Features:** full-text search (Room FTS4/5), species-filtered browse, condition article view with citations, "red-flag symptoms" quick index for emergencies, favorites/recent.
- **Degraded AI mode:** forward/backward links from KB articles into the questionnaire engine so a fully-offline clinician still gets a useful guided flow.

**Deliverables:** KB import pipeline, search, article UI, emergency red-flag index.
**Acceptance:** Airplane-mode test: search, open, and cite an article; run a questionnaire; get a differential shortlist — zero network calls (verified by a test that asserts no network during the flow). Dataset audit: every dosage, protocol, and citation traces to a verified species-specific reference; no AI-filled gaps (sampled review against sources).

---

## 10. Veterinary Clinical Safety, Privacy & Consent

- **Disclaimers:** first-launch consent screen + persistent footer on every diagnostic screen: decision-support tool, not a diagnosis; veterinarian remains responsible.
- **Urgency safety gate:** any questionnaire/image result classified "emergency" blocks the AI narrative behind a red-flag modal directing immediate veterinary contact.
- **Model output guardrails:** structured-output schema enforced; invalid/low-confidence outputs are shown as "insufficient information — ask more questions," never as guesses.
- **AI provenance display (mandatory):** every AI output displays the **provider and model used**. Confidence percentages are displayed **only** when the provider supplies a meaningful confidence measure, and the measure's **limitations are explained** to the user in plain language; otherwise confidence is omitted, never fabricated.
- **Rule-based vs AI labeling (mandatory):** deterministic questionnaire results are clearly labeled **"rule-based results"** and **visually distinguished** from **"AI predictions"** everywhere they appear — results screen, merged view, SOAP pre-fill, and PDF reports.
- **Privacy & consent:**
  - Patient/owner data is optional and minimized (no forced personal identifiers).
  - Image consent: capture flow explains images go to a cloud AI provider when online (and that on-device mode keeps images local); consent recorded per case artifact.
  - No analytics/crash SDKs in v1 unless the owner opts in; if added later: opt-in, documented in Play Data Safety.
  - Data deletion: Settings → "Delete all local clinical data" + per-case delete (hard delete from DB).

**Deliverables:** consent flow, disclaimers, safety gates, privacy policy draft (`docs/PRIVACY.md`).
**Acceptance:** A checklist review of every diagnosis surface shows disclaimer visible; emergency path test passes; airplane-mode test confirms no image leaves the device.

---

## 11. Database Design & Synchronization

### 11.1 Room schema (v1 tables)
`patients`, `cases`, `case_artifacts` (image/questionnaire/AI/SOAP), `questionnaire_sessions`, `conditions`, `symptoms`, `species`, `treatments`, `references`, junctions, `ai_calls` (provenance log), `app_settings`.
- All entities have stable UUIDs + `updatedAt` timestamps from day one (enables future sync without migrations).
- Room migrations only (no destructive fallback); schema exported & version-controlled.

### 11.2 Synchronization philosophy
- **v1 (Phases 1–7):** fully local. No cloud accounts.
- **v1.1 (Phase 8):** Android backup/restore for device migration.
- **v2 (optional, post-v1):** per-case end-to-end sync via Firebase free tier (Firestore + Storage), field-level last-writer-wins, per-user isolation, KB seed updates delivered as versioned packs. Only built if the owner requests it — adds privacy surface and quota considerations.

**Acceptance:** schema diagram (`docs/DB_SCHEMA.md`) matches entities; migration test suite passes on every schema change; no sync code exists until Phase 8+ approval.

---

## 12. SOAP Documentation & PDF Reporting

- **SOAP note:** structured editor per case — Subjective (owner/presentation), Objective (findings, image results), Assessment (ranked differentials + rationale), Plan (treatments + follow-up). Pre-filled from investigation artifacts; editable.
- **PDF report:** generated on-device (Android `PdfDocument` or HTML→PDF via WebView — decided in implementation), branded VETGUIDE page, case summary + SOAP + images + KB citations, watermark: "Decision support — not a diagnosis."
- Share via standard Android share sheet; export copies to Downloads with patient-privacy warning.

**Deliverables:** SOAP editor, PDF generator, share/export.
**Acceptance:** Generated PDF opens correctly on device and desktop; contains case artifacts and citations; WCAG legible; snapshot test on markup.

---

## 13. Testing, Debugging & Quality Assurance

| Layer | Approach |
|---|---|
| Domain (scoring engine, orchestrator) | JUnit + fake providers; fault-injection failover matrix |
| Data (DAOs, import) | Robolectric/instrumented Room tests; seed-import idempotency test |
| UI (Compose) | Compose testing APIs for critical flows (diagnose flow, consent, emergency gate) |
| Golden files | Fixed fixture images + expected structured outputs per provider |
| Static | ktlint/detekt warnings = build failures on CI |
| Manual QA checklist | Per-phase script in `docs/QA_CHECKLIST.md`, updated each phase |

Debugging support: in-app developer screen (debug builds) showing provider status, last AI calls/provenance, quota counters, and Room browser link.

**Acceptance (project-wide, enforced at release):** diagnosis flow covered by at least one UI test; failover matrix test green; no known crash bugs open; QA checklist signed off per phase.

---

## 14. GitHub Integration & Automated APK Builds

- **Version control:** trunk-based on `main`; feature work on short-lived branches (`feat/…`, `fix/…`); PRs (even solo) for CI gate + reviewable history.
- **CI (GitHub Actions, free tier):** on every push/PR → ktlint/detekt → unit tests → assemble debug APK → upload artifact. On tag `v*` → build unsigned (then signed via keystore stored as a GitHub Secret, base64-decoded at build time) → attach APK to GitHub Release + release notes.
- **Free-tier compliance:** runner minutes within free allowance (public repo = free); no billable services in CI.
- **Project hygiene:** issues per phase-epic, PR template with checkbox against acceptance criteria, `docs/` for ADRs (short architecture decision records for non-obvious choices).
- **Commits:** conventional commits (`feat:`, `fix:`, `docs:`, `chore:`); roadmap approved then committed as repo's official planning doc.

**Deliverables:** two workflows (PR + release), secret handling documented.
**Acceptance:** a tagged push produces a GitHub Release with a buildable, installable APK artifact; PR checks block merge on test/lint failure.

---

## 15. Security & Release Preparation

- **Security review (pre-release):** secret scan clean; no exported components beyond launcher; backup rules exclude clinical DB; file permissions minimal; image cache expiry; dependency CVE check (Dependabot on).
- **Play Store readiness:** privacy policy URL (docs/PRIVACY.md hosted), Data Safety form mapped from §10 (AI-provider data flows disclosed), internal testing track first, staged rollout, crash reporting decision revisited (Play Vitals free — recommended).
- **Release artifacts:** signed AAB for Play + shareable debug-less APK for direct distribution.
- **Legal:** clinical disclaimer text finalized; third-party AI terms-of-use reviewed and reflected in-app; attribution list for KB sources.

**Acceptance:** pre-release checklist in `docs/RELEASE_CHECKLIST.md` fully signed; internal-track slow-rollout health gate defined (crash-free ≥ 99%, ANR < 0.5%).

---

# Development Phases (Sequential Execution Plan)

Each phase: **Objectives → Tasks → Dependencies → Deliverables → Acceptance criteria**. Owner reviews and approves outputs at each phase boundary before the next begins.

## Phase 0 — Foundation & CI (first session)
**Objectives:** repo hygiene, project skeleton, automated builds.
**Tasks:** Gradle/version-catalog Compose project (min 26), Hilt, theme + fonts, NavHost with 5 stub screens + Home cards, `.gitignore` audit, ktlint/detekt + gitleaks config, unit-test setup, GitHub Actions PR workflow (lint + tests + debug APK artifact), app icon/splash, `docs/SECURITY.md` skeleton.
**Dependencies:** none (repo exists, LICENSE preserved).
**Deliverables:** installable APK with navigable screens; CI green.
**Acceptance:** `./gradlew build` passes locally and on CI; PR workflow uploads an APK artifact; no secrets in history (`gitleaks` clean); screens navigable.

## Phase 1 — Data Layer & Patient/Case Management (§3, §11)
**Objectives:** local persistence + core records.
**Tasks:** full Room schema + migrations + schema export; repositories + DAOs + tests; patient & case CRUD screens; case list filters/timeline; DataStore settings; backup rules.
**Dependencies:** Phase 0.
**Deliverables:** working patient/case management, 100% offline.
**Acceptance:** instrumented DAO tests green; create→edit→resolve case survives app restart; empty/loaded states designed; no sync code present.

## Phase 2 — Offline Knowledge Base (§9)
**Objectives:** evidence-based KB, offline-first.
**Tasks:** seed JSON schema + versioning; importer (idempotent, migration-safe) + FTS tables; article UI with citations; search + species filter + red-flag index; favorites/recent; starter dataset (40–60 dog/cat conditions, all cited).
**Dependencies:** Phase 1.
**Deliverables:** KB + emergency index.
**Acceptance:** airplane-mode test (no-network assertion) passes for search/browse/questionnaire entry; import idempotency test green; every dataset entry has ≥1 non-`null` citation; audit of dataset vs claimed count passes.

## Phase 3 — Guided Diagnostic Questionnaire Engine (§4.1)
**Objectives:** deterministic diagnostic core, no AI needed.
**Tasks:** presentation selector + signalment capture; symptom checklist UI (system-grouped, progressive disclosure); scoring engine (weights, confidence normalization, urgency classifier); result screen (ranked cards + urgency banner) + "Why this result?"; questionnaire data seeded into Room from assets; domain tests.
**Dependencies:** Phase 2 (symptom/condition data).
**Deliverables:** end-to-end questionnaire → ranked differentials.
**Acceptance:** engine unit tests on scripted cases (e.g., parvo presentation → parvovirus in top-3, urgent/emergency flag) pass; no AI/network touched; results merge-ready with provenance field.

## Phase 4 — AI Infrastructure: Orchestration & Failover (§5, §7, §8)
**Objectives:** provider abstraction + failover + free-tier guards — no feature UI yet.
**Tasks:** `AiProvider` interface + result/error models; `AiOrchestrator` (failover execution modes per §5.1, backoff, circuit breaker, provenance log → `ai_calls`); connectivity monitor; minimal secure backend skeleton (§8: provider-secret storage + server-side free-tier eligibility/quota enforcement per §7); settings screen (provider priority, developer screen — **no client-side key entry**, per §8); gitleaks + secret-hygiene rules + release-artifact credential scan; `AiCatalog`; `docs/AI_QUOTAS.md`.
**Dependencies:** Phase 3. **Note:** providers accept *contract conformance tests with fakes* only — no real billable calls in CI (§7). The secure backend is mandatory from this phase onward; cloud calls never go direct from the APK (§8).
**Deliverables:** orchestrator production-ready, verifiable with fakes; minimal secure backend enforcing free-tier eligibility and usage caps.
**Acceptance:** fault-injection matrix green (Gemini fails → alternative cloud → on-device → deterministic rule-based mode, each with correct banners); backend-enforced quota/eligibility blocking test green (§7); circuit breaker trip/recover test green; provenance persisted to `ai_calls`; artifact scan proves the APK holds no provider credentials.

## Phase 5 — Gemini Primary + Alternative + On-Device Providers (§6)
**Objectives:** real provider implementations behind the Phase 4 contract.
**Tasks:** Gemini provider (vision, structured-output schema enforcement, timeout/retry mapping); one independent cloud provider (OpenAI-compatible) + conformance tests; on-device TFLite/MediaPipe baseline classifier (dog/cat skin/ocular classes, bundled lightweight model); degraded-mode UX banners; fake providers finalized as CI stand-ins.
**Dependencies:** Phase 4.
**Deliverables:** three providers sharing one interface; `AiCatalog` updated.
**Acceptance:** every provider passes the same conformance suite; primary/alternative switch is a Settings change (no code change); on-device path verified without network; no free-tier cap is configurable above the provider's published free limit.

## Phase 6 — AI Image Analysis & Merged Diagnosis (§4.2)
**Objectives:** images into the pipeline, combined with questionnaires.
**Tasks:** CameraX + gallery flow with pre-processing; consent step for cloud image dispatch (§10) with per-artifact record; image → provider structured output (validated); merge engine (image findings ⊕ questionnaire scores → one ranked list with per-item provenance); annotation/notes on images; connectivity-aware dispatch (offline → on-device or queued).
**Dependencies:** Phases 3–5.
**Deliverables:** full diagnose flow: questionnaire ± images → merged differential.
**Acceptance:** golden-file tests for image pipeline; airplane-mode image flow never transmits (test-verified); merged results carry provenance per contributing source; emergency gate (§10) fires on flagged output.

## Phase 7 — SOAP Documentation & PDF Reporting (§12)
**Objectives:** clinical output polish.
**Tasks:** SOAP editor scaffolded from artifacts; PDF generator + branding + watermark; share/export flows; case-resolution wizard (outcome note closes loop).
**Dependencies:** Phases 1–6.
**Deliverables:** PDF case reports from any case.
**Acceptance:** PDF opens on device + desktop, contains SOAP/images/citations and watermark; snapshot test for markup; export path audit (patient-privacy warning shown).

## Phase 8 — Hardening, QA & Pre-Release (§13, §15)
**Objectives:** quality gate + release readiness.
**Tasks:** full MA checklist pass on device matrix (perf: cold start, memory during image flow); accessibility pass ( TalkBack, contrast); data-deletion/backup-rule verification; security review (§15); `docs/PRIVACY.md` + Play Data Safety mapping; release workflow (tag → signed AAB/APK → GitHub Release); `docs/RELEASE_CHECKLIST.md` signed.
**Dependencies:** Phases 0–7.
**Deliverables:** signed release build + release notes; checklist sign-off.
**Acceptance:** crash-free ≥ 99% on internal track or 20-device manual matrix; release workflow produces installable artifact from a tag; Play Data Safety form draft reflects actual data flows (AI provider disclosure).

## (Post-v1, optional — owner approval required before starting)
- **Phase 9 — Sync & accounts (§11.2):** Firebase-backed per-case backup, multi-device, versioned KB packs. *Adds privacy/quota surface — separate decision.*
- **Phase 10 — Species & features expansion:** livestock/exotics dataset packs, dosage/contra calculator, user-contributed case templates.

---

# Approval & Change Control

- This document is **planning only** — no features are built until the owner approves the roadmap.
- On approval: committed to `main` as the official roadmap (`VETGUIDE_ROADMAP.md`); any change re-reviewed before commit.
- The earlier draft at `docs/ROADMAP.md` (previous session) will be **removed at commit time** to avoid two competing roadmaps, unless the owner wants it kept as a history note.
