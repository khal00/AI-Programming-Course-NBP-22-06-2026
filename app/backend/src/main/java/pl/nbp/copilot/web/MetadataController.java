package pl.nbp.copilot.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.web.dto.MetadataResponse;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GET /api/metadata — supplies intake form with Polish-labelled selectable options.
 * ADR-001 §5, AC-28.
 */
@RestController
@RequestMapping("/api")
public class MetadataController {

    @GetMapping("/metadata")
    public MetadataResponse metadata() {
        List<MetadataResponse.Option> caseTypes = Arrays.stream(CaseType.values())
                .map(ct -> new MetadataResponse.Option(ct.name(), ct.getLabel()))
                .collect(Collectors.toList());

        List<MetadataResponse.Option> categories = Arrays.stream(EquipmentCategory.values())
                .map(ec -> new MetadataResponse.Option(ec.name(), ec.getLabel()))
                .collect(Collectors.toList());

        return new MetadataResponse(caseTypes, categories);
    }
}
