package pl.nbp.copilot.domain;

/**
 * Case type submitted by the customer.
 * PRD §8, ADR-000 §5.
 * All labels are in Polish (AC-28).
 */
public enum CaseType {

    /** Reklamacja — customer reports a defect and requests repair/replacement/refund. */
    COMPLAINT("Reklamacja"),

    /** Zwrot — customer wishes to return the product within the withdrawal window. */
    RETURN("Zwrot");

    private final String label;

    CaseType(String label) {
        this.label = label;
    }

    /** Polish display label (AC-28). */
    public String getLabel() {
        return label;
    }
}
