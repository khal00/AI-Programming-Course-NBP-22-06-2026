package pl.nbp.copilot.policy;

import pl.nbp.copilot.domain.CaseType;

/**
 * Returns the full markdown text of the applicable business policy
 * for the given case type.
 *
 * <p>Implementations read policies from the classpath (copied from
 * {@code docs/policies/} by the Maven build). Throws
 * {@link PolicyResourceNotFoundException} when a policy resource is missing.
 *
 * <p>AC-18: policy rules are injected into decision prompts.
 * TAC-04: correct policy selected per case type.
 */
public interface PolicyProvider {

    /**
     * Returns the full policy markdown text for the given case type.
     *
     * @param caseType the type of case (RETURN or COMPLAINT)
     * @return non-empty markdown text of the policy
     * @throws PolicyResourceNotFoundException if the policy resource is not found on the classpath
     */
    String policyFor(CaseType caseType);
}
