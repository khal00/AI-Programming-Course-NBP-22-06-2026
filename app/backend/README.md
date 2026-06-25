# Backend — Hardware Service Decision Copilot

Spring Boot 3.5.x · Java 21 · Maven. Implements the API in
[`docs/ADR/001-backend-api.md`](../../docs/ADR/001-backend-api.md) and the LLM pipeline in
[`docs/ADR/002-llm-integration.md`](../../docs/ADR/002-llm-integration.md).

## Status

Scaffold only: application bootstrap, configuration binding (`application.yml`), and `GET /api/health`.
The case/chat controllers, image processing, LLM integration and in-memory session store are implemented
per the ADRs (TDD — write tests first, see ADR §10).

## Prerequisites

- JDK 21 (the dev machine currently has JDK 25; the project targets `release 21`. Install JDK 21 for runtime parity).
- Maven 3.9+ **or** generate the Maven Wrapper once with `mvn -N wrapper:wrapper` so `./mvnw` is available.
  > Maven is not currently on PATH in this environment; install it (or use your IDE's bundled Maven) before building.

## Configuration

Set the OpenRouter key before running (see repo `.env.example`):

```bash
export OPENROUTER_API_KEY=sk-or-v1-...      # or OPENAI_API_KEY as fallback
# optional overrides:
export OPENROUTER_MODEL_VISION=openai/gpt-4o
export OPENROUTER_MODEL_REASONING=anthropic/claude-sonnet-4
```

All keys and defaults are documented in `docs/ADR/000-main-architecture.md` §7.

## Build & run

```bash
mvn clean verify        # compile + run tests
mvn spring-boot:run     # start on http://localhost:8080
curl http://localhost:8080/api/health   # -> {"status":"UP"}
```

## Policy documents

The decision prompts inject `docs/policies/return-policy.md` and `docs/policies/complaint-policy.md`
(ADR-002 PolicyProvider). When implementing, either copy them into
`src/main/resources/policies/` at build time or read them from the docs path; keep `docs/policies/*`
as the single source of truth.

## Notes

- `com.openai:openai-java` version in `pom.xml` (4.41.0) should be confirmed against Maven Central before the first build.
- Tests mock the OpenRouter HTTP boundary (MockWebServer); no real API calls in unit/integration tests (AGENTS.md test strategy).
