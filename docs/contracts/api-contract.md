# API Contract — Hardware Service Decision Copilot

> **FROZEN after Phase 0 — changes require an explicit contract-change step by the orchestrator.**
>
> This document is the single source of truth for both the backend (`app/backend`) and
> frontend (`app/frontend`) agents. Any divergence between this contract and the implementation
> must be flagged and reconciled before merging. Backend implements; frontend consumes; QA verifies.

**Date:** 2026-06-25
**Relates to:** `docs/ADR/000-main-architecture.md`, `docs/ADR/001-backend-api.md`, `docs/ADR/002-llm-integration.md`

---

## 1. Endpoints

### 1.1 POST `/api/cases` — Submit intake form

**Purpose:** Submit the intake form, run the two-stage LLM pipeline, return the first AI decision.

**Content-Type:** `multipart/form-data`

**Form fields:**

| Field | Type | Required | Validation |
|---|---|---|---|
| `caseType` | string | Yes | One of: `COMPLAINT`, `RETURN` |
| `category` | string | Yes | One of the 12 EquipmentCategory codes (see §5) |
| `modelName` | string | Yes | Non-blank after trimming |
| `purchaseDate` | string (ISO date `yyyy-MM-dd`) | Yes | Not in the future |
| `reason` | string | Conditional | Required when `caseType=COMPLAINT`; optional for `RETURN` |
| `image` | file | Yes | JPEG / PNG / WebP only; max 10 MB (10 485 760 bytes) |

**Success response — `201 Created`:**

```json
{
  "sessionId": "string (opaque UUID)",
  "caseSummary": {
    "caseType": "COMPLAINT | RETURN",
    "caseTypeLabel": "Reklamacja | Zwrot",
    "category": "SMARTPHONES | LAPTOPS | ...",
    "categoryLabel": "Smartfony | Laptopy | ...",
    "modelName": "string",
    "purchaseDate": "yyyy-MM-dd"
  },
  "verdict": "APPROVE | REJECT | NEEDS_INFO | ESCALATE",
  "firstMessage": "string (Markdown)"
}
```

**Error responses:**

| Status | Condition | Body |
|---|---|---|
| `400` | Field validation failure | `ErrorResponse` with `fieldErrors` (Polish per-field messages) |
| `422` | Image unreadable — LLM cannot analyze it | `ErrorResponse` with `code=IMAGE_UNREADABLE`, Polish instruction to re-upload a clearer photo. **NOT a verdict.** |
| `503` | LLM unavailable after retries | `ErrorResponse` with `code=LLM_UNAVAILABLE`, Polish "spróbuj ponownie" message. Retriable. |

**Important:** An image that *contradicts* the declared case is **not** an error. The pipeline
returns `201` with verdict `NEEDS_INFO` or `ESCALATE` and the discrepancy stated in `firstMessage`
and `caseSummary` verdict field.

---

### 1.2 POST `/api/cases/{sessionId}/messages` — Follow-up chat (streaming)

**Purpose:** Send a customer follow-up message and stream the agent's reply token by token.

**Path parameter:** `sessionId` — the opaque session ID from the `POST /api/cases` response.

**Content-Type:** `application/json`

**Request body:**

```json
{
  "message": "string (non-blank)"
}
```

**Success response — `200 OK`, `Content-Type: text/event-stream`**

The response is a Server-Sent Events stream. The frontend MUST consume it via
`fetch` + `ReadableStream` (not `EventSource`, which is GET-only — ADR-001).

**SSE frame format:**

```
event: token
data: <text chunk — partial word or sentence fragment>

event: token
data: <text chunk>

...

event: done
data: [DONE]
```

- `token` events: each carries a single partial text chunk (may be a word, partial word, or whitespace). The frontend accumulates them in order to build the full reply.
- `done` event: signals the end of the stream. `data` value is the literal string `[DONE]`. The assembled assistant reply has been appended to server-side session history at this point.
- `error` event: sent instead of `done` when an error occurs mid-stream. `data` contains a Polish error message. The session and conversation history are **preserved** (the partial reply is discarded). The frontend should display an inline retry affordance.

```
event: error
data: <Polish error message>
```

**Error responses (non-streaming HTTP errors):**

| Status | Condition | Body |
|---|---|---|
| `400` | Empty or blank message | `ErrorResponse` with `code=VALIDATION_ERROR` |
| `404` | Unknown or expired `sessionId` | `ErrorResponse` with `code=SESSION_NOT_FOUND` |

Note: `503` LLM unavailable during streaming is surfaced as an `error` SSE event, not an HTTP 503.

---

### 1.3 GET `/api/metadata` — Form option lists

**Purpose:** Supply the intake form with selectable options and their Polish labels.

**Success response — `200 OK`:**

```json
{
  "caseTypes": [
    { "code": "COMPLAINT", "label": "Reklamacja" },
    { "code": "RETURN",    "label": "Zwrot" }
  ],
  "categories": [
    { "code": "SMARTPHONES",      "label": "Smartfony" },
    { "code": "LAPTOPS",          "label": "Laptopy" },
    { "code": "TABLETS",          "label": "Tablety" },
    { "code": "TVS",              "label": "Telewizory" },
    { "code": "MONITORS",         "label": "Monitory" },
    { "code": "HEADPHONES",       "label": "Słuchawki" },
    { "code": "SMARTWATCHES",     "label": "Smartwatche / opaski" },
    { "code": "GAME_CONSOLES",    "label": "Konsole do gier" },
    { "code": "AUDIO",            "label": "Sprzęt audio" },
    { "code": "SMALL_APPLIANCES", "label": "Drobne AGD" },
    { "code": "ACCESSORIES",      "label": "Akcesoria" },
    { "code": "OTHER",            "label": "Inne" }
  ]
}
```

---

### 1.4 GET `/api/health` — Liveness check

**Purpose:** Confirm the backend is running. Does NOT call OpenRouter.

**Success response — `200 OK`:**

```json
{ "status": "UP" }
```

---

## 2. Shared Types

### 2.1 ErrorResponse

All error responses use this shape. **Never contains** stack traces, internal IDs, prompt text,
or raw provider error payloads (AC-29).

```json
{
  "code": "string (machine-readable, stable)",
  "message": "string (Polish user-facing summary)",
  "fieldErrors": [
    { "field": "string", "message": "string (Polish)" }
  ]
}
```

`fieldErrors` is present only on `400` validation errors. It is omitted (or empty) for `422`, `503`, `404`.

**Known error codes:**

| Code | HTTP Status | Polish message template |
|---|---|---|
| `VALIDATION_ERROR` | 400 | "Wystąpiły błędy walidacji formularza." |
| `IMAGE_UNREADABLE` | 422 | "Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia." |
| `LLM_UNAVAILABLE` | 503 | "Usługa AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę." |
| `SESSION_NOT_FOUND` | 404 | "Sesja nie istnieje lub wygasła. Rozpocznij nową sprawę." |

### 2.2 CaseResponse

Full shape documented in §1.1.

### 2.3 MetadataResponse

Full shape documented in §1.3.

### 2.4 ChatRequest

```json
{ "message": "string" }
```

Validation: `message` must be non-blank. Blank → `400` with `code=VALIDATION_ERROR`.

---

## 3. Internal Structured-Output Schemas

These schemas are internal to the LLM pipeline. They are never returned to the client as-is.
They are documented here for the LLM integration step (ADR-002) and for QA mock scenario setup.

### 3.1 ImageFindings

Structured output from the vision model (Call 1).

```json
{
  "readable":             "boolean  — false → 422, no verdict produced",
  "description":          "string   — concise summary (internal, never shown raw)",
  "signsOfUse":           "boolean | null — return-relevant",
  "resellable":           "boolean | null — return-relevant",
  "damagePresent":        "boolean | null — complaint-relevant",
  "damageType":           "string  | null — complaint-relevant, free text",
  "likelyCauseCategory":  "MANUFACTURING | MECHANICAL | LIQUID | WEAR | UNKNOWN",
  "matchesDeclaredCase":  "boolean  — false → verdict must be NEEDS_INFO or ESCALATE",
  "discrepancyNote":      "string  | null — required when matchesDeclaredCase=false",
  "confidence":           "LOW | MEDIUM | HIGH"
}
```

### 3.2 DecisionResult

Structured output from the reasoning model (Call 2).

```json
{
  "verdict":               "APPROVE | REJECT | NEEDS_INFO | ESCALATE",
  "justification":         "string (Polish) — references form data, findings, and policy",
  "nextSteps":             ["string", ...] — ordered list of Polish next-step strings",
  "discrepancyNoted":      "boolean",
  "discrepancyExplanation":"string | null — required when discrepancyNoted=true",
  "disclaimer":            "string (Polish) — mandatory non-binding disclaimer (AC-21)"
}
```

Constraint: if `ImageFindings.matchesDeclaredCase = false`, then `DecisionResult.verdict` MUST be
`NEEDS_INFO` or `ESCALATE` and `discrepancyNoted` MUST be `true` (ADR-002 §5).

---

## 4. Mock-LLM Scenarios (for `mock-llm` Spring profile)

The `mock-llm` profile provides a deterministic `LlmService` stub for E2E and integration
testing without a real OpenRouter key.

### 4.1 Scenario-selection mechanism

**Mechanism: derive the scenario from the uploaded image filename.**

When the `mock-llm` profile is active, the `LlmService` stub inspects
`CaseIntake.imageFilename` (the original filename of the uploaded file) for a known scenario key:

| Filename contains (case-insensitive) | Scenario key |
|---|---|
| `clean-return` | `clean-return` |
| `damaged-complaint` | `damaged-complaint` |
| `contradiction` | `contradiction` |
| `unreadable` | `unreadable` |
| `llm-fail` | `llm-fail` |
| *(anything else)* | `clean-return` (default) |

The frontend is **unaware** of this mechanism — it submits whatever file the tester uploads.
The tester selects the scenario by naming or renaming the test image file before upload.
This avoids any hidden form field, special header, or backend-specific knowledge on the
Angular side.

### 4.2 Scenario definitions

| Scenario key | ImageFindings | DecisionResult / outcome |
|---|---|---|
| `clean-return` | `readable=true`, `signsOfUse=false`, `resellable=true`, `damagePresent=false`, `matchesDeclaredCase=true`, `confidence=HIGH` | `verdict=APPROVE`, standard justification, next steps: pack + deliver |
| `damaged-complaint` | `readable=true`, `damagePresent=true`, `damageType="Pęknięty ekran"`, `likelyCauseCategory=MECHANICAL`, `matchesDeclaredCase=true`, `confidence=HIGH` | `verdict=APPROVE` (manufacturing) or `REJECT` (mechanical by user) — implementation may return `NEEDS_INFO` to be safe; key: discrepancy NOT set |
| `contradiction` | `readable=true`, `damagePresent=true`, `matchesDeclaredCase=false`, `discrepancyNote="Urządzenie wykazuje ślady uszkodzeń mechanicznych mimo zgłoszenia zwrotu"`, `confidence=MEDIUM` | `verdict=NEEDS_INFO`, `discrepancyNoted=true`, `discrepancyExplanation` populated |
| `unreadable` | `readable=false`, `confidence=LOW` | → orchestration returns `422` (no DecisionResult produced) |
| `llm-fail` | Stub throws `LlmUnavailableException` on analyzeImage | → orchestration returns `503` |

---

## 5. Equipment Category Code Reference

| Code | Polish label |
|---|---|
| `SMARTPHONES` | Smartfony |
| `LAPTOPS` | Laptopy |
| `TABLETS` | Tablety |
| `TVS` | Telewizory |
| `MONITORS` | Monitory |
| `HEADPHONES` | Słuchawki |
| `SMARTWATCHES` | Smartwatche / opaski |
| `GAME_CONSOLES` | Konsole do gier |
| `AUDIO` | Sprzęt audio |
| `SMALL_APPLIANCES` | Drobne AGD |
| `ACCESSORIES` | Akcesoria |
| `OTHER` | Inne |

---

## 6. Notes on CORS and Dev Proxy

In local development, the Angular dev server (port 4200) proxies `/api/*` to the Spring Boot
backend (port 8080) via `proxy.conf.json`. No CORS configuration is required in this setup.
For non-proxy environments (e.g. staging), the backend should be configured with an
`allowed-origins` list matching the Angular origin.

---

## 7. Change Policy

This contract is frozen after Phase 0. To change it:

1. The **orchestrator** opens a contract-change discussion with both the backend and frontend agents.
2. Both agents assess the impact.
3. The orchestrator approves the change, updates this document, and bumps the date.
4. Backend and frontend implement in parallel against the new version.

Backward-incompatible changes (field removals, type changes, endpoint renames) require a
version bump in the path (e.g. `/api/v2/cases`) unless both agents ship simultaneously.
