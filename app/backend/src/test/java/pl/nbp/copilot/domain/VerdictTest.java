package pl.nbp.copilot.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — enum ↔ Polish label mapping for Verdict.
 */
class VerdictTest {

    @Test
    void approveHasPolishLabel() {
        assertThat(Verdict.APPROVE.getLabel()).isEqualTo("Zatwierdzone");
    }

    @Test
    void rejectHasPolishLabel() {
        assertThat(Verdict.REJECT.getLabel()).isEqualTo("Odrzucone");
    }

    @Test
    void needsInfoHasPolishLabel() {
        assertThat(Verdict.NEEDS_INFO.getLabel()).isEqualTo("Wymaga uzupełnienia");
    }

    @Test
    void escalateHasPolishLabel() {
        assertThat(Verdict.ESCALATE.getLabel()).isEqualTo("Eskalacja do specjalisty");
    }
}
