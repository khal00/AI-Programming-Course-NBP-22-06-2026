package pl.nbp.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Hardware Service Decision Copilot backend.
 *
 * <p>See {@code docs/ADR/000-main-architecture.md} and {@code docs/ADR/001-backend-api.md}
 * for the architecture this scaffold implements.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
