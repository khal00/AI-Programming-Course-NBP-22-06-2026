package pl.nbp.copilot.web.dto;

import java.util.List;

/**
 * Response body for GET /api/metadata.
 * Supplies the frontend form with selectable options and their Polish labels (AC-28).
 * ADR-001 §4.
 */
public record MetadataResponse(

        /** Available case types with Polish labels. */
        List<Option> caseTypes,

        /** Available equipment categories with Polish labels. */
        List<Option> categories

) {

    /**
     * A selectable option: a stable machine-readable code and a Polish display label.
     */
    public record Option(

            /** Stable enum name (e.g. "COMPLAINT", "SMARTPHONES"). */
            String code,

            /** Polish display label (e.g. "Reklamacja", "Smartfony"). */
            String label

    ) {}
}
