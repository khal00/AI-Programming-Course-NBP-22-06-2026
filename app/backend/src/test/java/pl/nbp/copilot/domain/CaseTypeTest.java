package pl.nbp.copilot.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — enum ↔ Polish label mapping for CaseType.
 * Written BEFORE production code (AGENTS.md §TDD Rules).
 */
class CaseTypeTest {

    @Test
    void complaintHasPolishLabel() {
        assertThat(CaseType.COMPLAINT.getLabel()).isEqualTo("Reklamacja");
    }

    @Test
    void returnHasPolishLabel() {
        assertThat(CaseType.RETURN.getLabel()).isEqualTo("Zwrot");
    }

    @Test
    void complaintHasCodeComplaint() {
        assertThat(CaseType.COMPLAINT.name()).isEqualTo("COMPLAINT");
    }
}
