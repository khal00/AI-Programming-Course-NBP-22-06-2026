package pl.nbp.copilot.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — EquipmentCategory has EXACTLY 12 categories with correct Polish labels and codes.
 * PRD §8: Smartfony, Laptopy, Tablety, Telewizory, Monitory, Słuchawki,
 *          Smartwatche / opaski, Konsole do gier, Sprzęt audio, Drobne AGD, Akcesoria, Inne.
 */
class EquipmentCategoryTest {

    @Test
    void hasExactlyTwelveCategories() {
        assertThat(EquipmentCategory.values()).hasSize(12);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "SMARTPHONES,      Smartfony",
            "LAPTOPS,          Laptopy",
            "TABLETS,          Tablety",
            "TVS,              Telewizory",
            "MONITORS,         Monitory",
            "HEADPHONES,       Słuchawki",
            "SMARTWATCHES,     Smartwatche / opaski",
            "GAME_CONSOLES,    Konsole do gier",
            "AUDIO,            Sprzęt audio",
            "SMALL_APPLIANCES, Drobne AGD",
            "ACCESSORIES,      Akcesoria",
            "OTHER,            Inne"
    })
    void categoryHasCorrectPolishLabel(String code, String expectedLabel) {
        EquipmentCategory category = EquipmentCategory.valueOf(code.trim());
        assertThat(category.getLabel()).isEqualTo(expectedLabel.trim());
    }
}
