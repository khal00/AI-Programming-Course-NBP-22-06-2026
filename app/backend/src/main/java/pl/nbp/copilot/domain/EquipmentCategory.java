package pl.nbp.copilot.domain;

/**
 * Predefined list of electronics equipment categories.
 * PRD §8 (Functional Constraints): exactly 12 categories with Polish labels.
 * All labels are in Polish (AC-28).
 */
public enum EquipmentCategory {

    SMARTPHONES("Smartfony"),
    LAPTOPS("Laptopy"),
    TABLETS("Tablety"),
    TVS("Telewizory"),
    MONITORS("Monitory"),
    HEADPHONES("Słuchawki"),
    SMARTWATCHES("Smartwatche / opaski"),
    GAME_CONSOLES("Konsole do gier"),
    AUDIO("Sprzęt audio"),
    SMALL_APPLIANCES("Drobne AGD"),
    ACCESSORIES("Akcesoria"),
    OTHER("Inne");

    private final String label;

    EquipmentCategory(String label) {
        this.label = label;
    }

    /** Polish display label (AC-28). */
    public String getLabel() {
        return label;
    }
}
