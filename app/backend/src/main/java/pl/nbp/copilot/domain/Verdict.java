package pl.nbp.copilot.domain;

/**
 * AI-generated verdict for a complaint or return case.
 * PRD §9.2, ADR-000 §5.
 * All labels are in Polish (AC-28).
 */
public enum Verdict {

    /** Zatwierdzone — the case appears to meet policy; provide next steps. */
    APPROVE("Zatwierdzone"),

    /** Odrzucone — the case does not appear to meet policy; explain the reason. */
    REJECT("Odrzucone"),

    /** Wymaga uzupełnienia — more information or a clearer photo is needed. */
    NEEDS_INFO("Wymaga uzupełnienia"),

    /** Eskalacja do specjalisty — ambiguous or high-stakes case; requires human review. */
    ESCALATE("Eskalacja do specjalisty");

    private final String label;

    Verdict(String label) {
        this.label = label;
    }

    /** Polish display label (AC-28). */
    public String getLabel() {
        return label;
    }
}
