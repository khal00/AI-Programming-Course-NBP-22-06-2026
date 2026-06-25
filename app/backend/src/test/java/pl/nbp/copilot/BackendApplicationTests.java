package pl.nbp.copilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring context loads. This is the first TDD checkpoint —
 * feature tests (validation, pipeline, SSE) are added per ADR §10 as code lands.
 */
@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: fails if the application context cannot start.
    }
}
