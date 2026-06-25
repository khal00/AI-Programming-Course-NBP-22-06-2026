# E2E Tests — Hardware Service Decision Copilot

Playwright E2E test suite covering all PRD scenarios for the Hardware Service Decision Copilot PoC.

## Prerequisites

- Node.js 18+
- Maven 3.9+ and Java 21+ (for backend)
- Angular CLI (for frontend)

## Start the full stack

### 1. Start the backend (mock-LLM profile)

```bash
cd app/backend
set SPRING_PROFILES_ACTIVE=mock-llm && set OPENROUTER_API_KEY=dummy && mvn spring-boot:run
```

Wait until you see: `Started BackendApplication` and health check passes:
```
curl http://localhost:8080/api/health
```

### 2. Start the frontend

```bash
cd app/frontend
npm start
```

Wait until Angular CLI reports: `Application bundle generation complete` and serving on `http://localhost:4200`.

### 3. Run the tests

```bash
cd app/e2e
npm install
npx playwright install chromium
npx playwright test --reporter=list
```

### Run a single spec

```bash
npx playwright test tests/happy-return.spec.ts
```

### Run headed (see the browser)

```bash
npx playwright test --headed
```

### Open Playwright UI mode

```bash
npx playwright test --ui
```

## Test scenarios

| File | Scenario | Verdict |
|---|---|---|
| `happy-return.spec.ts` | Clean return, no issues | APPROVE |
| `happy-complaint.spec.ts` | Complaint with manufacturing defect | APPROVE |
| `contradiction.spec.ts` | Contradictory info → needs clarification | NEEDS_INFO |
| `unreadable.spec.ts` | Unreadable image → HTTP 422 | — |
| `validation.spec.ts` | Client-side form validation | — |
| `llm-unavailable.spec.ts` | LLM service down → HTTP 503 | — |
| `chat-streaming.spec.ts` | Follow-up message streaming | — |
| `off-topic.spec.ts` | Off-topic question (mock limitation) | — |
| `session-unknown.spec.ts` | Direct URL without session state | — |
| `no-leakage.spec.ts` | No stack traces / internal details exposed | — |

## Mock LLM scenario selection (filename-based)

The backend's `MockLlmService` selects behavior based on the uploaded image filename (case-insensitive substring match):

- `clean-return` → APPROVE, "Zatwierdzone"
- `damaged-complaint` → APPROVE (manufacturing defect)
- `contradiction` → NEEDS_INFO (HTTP 201)
- `unreadable` → HTTP 422 error
- `llm-fail` → HTTP 503 error
- anything else → same as clean-return

## Environment variables (QA-3)

The `OPENROUTER_API_KEY` is required for real LLM calls. In the mock-llm profile, set it to `dummy`.
For real LLM integration tests, set a valid key: `set OPENROUTER_API_KEY=your-key-here`
