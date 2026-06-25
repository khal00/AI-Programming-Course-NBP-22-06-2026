package pl.nbp.copilot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables binding of {@link AppProperties} from the application.yml / environment.
 * Imported automatically by @SpringBootApplication component scan.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
