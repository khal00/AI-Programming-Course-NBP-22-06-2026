package pl.nbp.copilot.policy;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.domain.CaseType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Classpath-backed implementation of {@link PolicyProvider}.
 *
 * <p>Reads policy markdown files that are copied to the classpath by the
 * Maven build (from {@code docs/policies/}). Resources are loaded on every
 * call — no caching needed for this volume.
 *
 * <p>AC-18, TAC-04.
 */
@Component
public class ClasspathPolicyProvider implements PolicyProvider {

    private static final String DEFAULT_RETURN_PATH    = "policies/return-policy.md";
    private static final String DEFAULT_COMPLAINT_PATH = "policies/complaint-policy.md";

    private final String returnPath;
    private final String complaintPath;

    /** Default constructor — uses the standard classpath paths. */
    public ClasspathPolicyProvider() {
        this(DEFAULT_RETURN_PATH, DEFAULT_COMPLAINT_PATH);
    }

    /**
     * Constructor for testing with custom resource paths.
     *
     * @param returnPath    classpath path for the return policy
     * @param complaintPath classpath path for the complaint policy
     */
    public ClasspathPolicyProvider(String returnPath, String complaintPath) {
        this.returnPath    = returnPath;
        this.complaintPath = complaintPath;
    }

    @Override
    public String policyFor(CaseType caseType) {
        String path = switch (caseType) {
            case RETURN    -> returnPath;
            case COMPLAINT -> complaintPath;
        };
        return readClasspathResource(path);
    }

    private String readClasspathResource(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new PolicyResourceNotFoundException(path);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PolicyResourceNotFoundException(path, e);
        }
    }
}
