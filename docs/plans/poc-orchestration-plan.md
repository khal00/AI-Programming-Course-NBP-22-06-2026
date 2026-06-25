# Implementation Plan — Hardware Service Decision Copilot PoC (Orchestration)

## Context

Building a **fully working Proof of Concept** of the *Hardware Service Decision Copilot* — a Polish-language, self-service web app where a customer files an electronics **complaint (reklamacja)** or **return (zwrot)**, uploads one device photo, and receives an immediate, policy-grounded **preliminary recommendation** from an AI agent, then continues in a streaming chat that holds full case context. The final decision is always made by a human; every recommendation carries a non-binding disclaimer.

Spec sources: `docs/PRD-Product-Requirements-Document.md` (AC-01..AC-29), `docs/ADR/000..004`, `docs/policies/*.md`, `docs/design-guidelines.md` + `assets/` (Hacker News flat design).

**I am orchestrator only.** All implementation is delegated to `be-developer`, `fe-developer`, `qa-engineer`, each given a self-contained per-step brief containing only what that step needs.

### Locked decisions
1. **E2E** against real FE+BE with a deterministic **`mock-llm` Spring profile**. One manual **real-OpenRouter smoke** at the end.
2. **Model defaults**: `OPENROUTER_MODEL_VISION=openai/gpt-4o`, `OPENROUTER_MODEL_REASONING=anthropic/claude-sonnet-4`, env-overridable.
3. Network available → fe-developer runs `ng new` / `ng add @angular/material` / `npm install ngx-markdown marked`.
4. Git: commit each green step; format `Area: summary`; **no push**. (Work happens in worktree branch `poc-impl`, merged to `electronics-complaints` at the end.)
5. Parallel after contract freeze.
6. Done bar: all suites green under `mock-llm`, `mvn test` + `ng build` + `ng test` pass, both apps start, plus one real-OpenRouter happy-path run (return + complaint).
7. Policies copied into `app/backend/src/main/resources/policies/` at build; PolicyProvider reads classpath.

### Environment note
Background-job isolation requires a worktree, but the project baseline was untracked at session start; it is now committed on `electronics-complaints` (HEAD `97eab30`). Work proceeds in worktree branch `poc-impl` (created from that HEAD, contains all files) and is merged back to `electronics-complaints` at completion — honoring both isolation and the user's branch choice.

## Working agreements (every step)
- Strict TDD: spec → failing test (confirm red for the right reason) → minimal code → green → refactor. Agent reports red→green.
- Verify before commit: BE `mvn -q test` + boot; FE `ng test --watch=false` + `ng build`.
- Commit one logical change; `Area: summary`; no push.
- Context7 for any library; if key invalid, fall back to ADR-pinned versions + SDK docs and report.
- Polish for all user-facing + model-facing text (AC-28); no leakage of prompts/IDs/traces (AC-29).
- Directory ownership: be→`app/backend/**`, fe→`app/frontend/**`, qa→`app/e2e/**`. `docs/contracts/api-contract.md` authored once in Phase 0, read-only after.

## Phases & gates
- **G0** contract frozen + both scaffolds build.
- **G1** backend complete & integration-green.
- **G2** frontend complete & unit-green + builds.
- **G3** E2E green under mock-llm.
- **DONE** real-LLM smoke + final verification.

Critical path: BE-0.1 → BE-1..BE-8 → QA-1..QA-2 → QA-3. FE track runs parallel to BE, must finish before QA-1.

## Dependency matrix
| Task | Agent | Depends on |
|---|---|---|
| BE-0.1 domain model + frozen API contract | be | scaffold |
| BE-0.2 config, policy copy, mock-llm skeleton | be | BE-0.1 |
| FE-0.3 Angular scaffold + HN theme | fe | — (parallel w/ BE-0.x) |
| BE-1 PolicyProvider | be | BE-0.2 |
| BE-2 PromptTemplateProvider | be | BE-1 |
| BE-3 ImageService | be | BE-0.2 |
| BE-4 SessionStore + eviction | be | BE-0.1 |
| BE-5 LlmService (real) + client factory | be | BE-2 |
| BE-6 mock-llm LlmService | be | BE-5 |
| BE-7 CaseOrchestration + MessageAssembler | be | BE-1,2,3,4,5 |
| BE-8 controllers + advice + metadata + integration tests | be | BE-7,6 |
| FE-1 models | fe | G0 |
| FE-2 CaseService | fe | FE-1 |
| FE-3 IntakeFormComponent | fe | FE-2 |
| FE-4 ChatStreamService | fe | FE-1 |
| FE-5 ChatComponent | fe | FE-4,2 |
| FE-6 app shell/routing/theme | fe | FE-3,5 |
| QA-1 full-stack stand-up + Playwright harness | qa | G1+G2 |
| QA-2 E2E scenarios | qa | QA-1 |
| QA-3 real-LLM smoke + verification | qa | G3 |

(Full per-step briefs, AC/TAC mappings, and scenario list as approved in session.)
