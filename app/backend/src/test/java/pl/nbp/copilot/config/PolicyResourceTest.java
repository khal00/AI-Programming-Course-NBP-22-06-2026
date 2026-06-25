package pl.nbp.copilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — both policy markdown files must be present on the classpath after build.
 * Verifies the maven-resources-plugin copy-resources configuration (TASK B2).
 * Written BEFORE pom.xml plugin configuration is added.
 */
class PolicyResourceTest {

    @Test
    void returnPolicyIsPresentOnClasspath() throws IOException {
        ClassPathResource resource = new ClassPathResource("policies/return-policy.md");
        assertThat(resource.exists())
                .as("policies/return-policy.md must be present on the classpath (copied from docs/policies/)")
                .isTrue();
        assertThat(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .as("return-policy.md must not be empty")
                .isNotBlank();
    }

    @Test
    void complaintPolicyIsPresentOnClasspath() throws IOException {
        ClassPathResource resource = new ClassPathResource("policies/complaint-policy.md");
        assertThat(resource.exists())
                .as("policies/complaint-policy.md must be present on the classpath (copied from docs/policies/)")
                .isTrue();
        assertThat(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .as("complaint-policy.md must not be empty")
                .isNotBlank();
    }
}
