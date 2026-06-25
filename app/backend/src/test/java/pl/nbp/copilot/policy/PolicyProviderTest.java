package pl.nbp.copilot.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.CaseType;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD — PolicyProvider returns the correct policy text from the classpath.
 * Tests written BEFORE the implementation (BE-1, AC-18, TAC-04).
 */
class PolicyProviderTest {

    private PolicyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ClasspathPolicyProvider();
    }

    // -------------------------------------------------------------------------
    // Happy path: RETURN
    // -------------------------------------------------------------------------

    @Test
    void returnPolicyIsNonEmpty() {
        String policy = provider.policyFor(CaseType.RETURN);
        assertThat(policy).isNotBlank();
    }

    @Test
    void returnPolicyContainsKnownPhrase() {
        String policy = provider.policyFor(CaseType.RETURN);
        // Known phrase from docs/policies/return-policy.md §2
        assertThat(policy).contains("30 dni");
    }

    @Test
    void returnPolicyContainsTechSerwisHeader() {
        String policy = provider.policyFor(CaseType.RETURN);
        // Document title present
        assertThat(policy).contains("Regulamin zwrotów");
    }

    // -------------------------------------------------------------------------
    // Happy path: COMPLAINT
    // -------------------------------------------------------------------------

    @Test
    void complaintPolicyIsNonEmpty() {
        String policy = provider.policyFor(CaseType.COMPLAINT);
        assertThat(policy).isNotBlank();
    }

    @Test
    void complaintPolicyContainsKnownPhrase() {
        String policy = provider.policyFor(CaseType.COMPLAINT);
        // Known phrase from docs/policies/complaint-policy.md §2
        assertThat(policy).contains("24 miesięcy");
    }

    @Test
    void complaintPolicyContainsHeader() {
        String policy = provider.policyFor(CaseType.COMPLAINT);
        assertThat(policy).contains("Regulamin reklamacji");
    }

    // -------------------------------------------------------------------------
    // The two policies are distinct
    // -------------------------------------------------------------------------

    @Test
    void returnAndComplaintPoliciesAreDifferent() {
        String returnPolicy = provider.policyFor(CaseType.RETURN);
        String complaintPolicy = provider.policyFor(CaseType.COMPLAINT);
        assertThat(returnPolicy).isNotEqualTo(complaintPolicy);
    }

    // -------------------------------------------------------------------------
    // Missing resource → typed error
    // (tested via a provider configured with a bogus path)
    // -------------------------------------------------------------------------

    @Test
    void missingResourceThrowsPolicyResourceNotFoundException() {
        PolicyProvider broken = new ClasspathPolicyProvider("policies/does-not-exist-return.md",
                "policies/does-not-exist-complaint.md");
        assertThatThrownBy(() -> broken.policyFor(CaseType.RETURN))
                .isInstanceOf(PolicyResourceNotFoundException.class)
                .hasMessageContaining("does-not-exist-return.md");
    }
}
