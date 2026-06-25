package pl.nbp.copilot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.nbp.copilot.image.DefaultImageService;
import pl.nbp.copilot.image.ImageService;

/**
 * Enables binding of {@link AppProperties} from the application.yml / environment.
 * Also wires beans that require config-property constructor parameters.
 * Imported automatically by @SpringBootApplication component scan.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /**
     * Max edge in pixels for the vision model — sensible cap for multimodal LLMs.
     * Thumbnailator resizes so the longest side == this value.
     */
    private static final int IMAGE_MAX_EDGE_PX = 1024;

    @Bean
    ImageService imageService(AppProperties props) {
        return new DefaultImageService(props.image().maxBytes(), IMAGE_MAX_EDGE_PX);
    }
}
