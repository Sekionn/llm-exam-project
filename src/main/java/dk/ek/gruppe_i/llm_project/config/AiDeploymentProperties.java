package dk.ek.gruppe_i.llm_project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiDeploymentProperties {

    /** Default for HOUSING_CAPACITY when the request does not set useRag. */
    private boolean ragEnabled = true;

    /** Number of knowledge chunks added to the prompt. */
    private int ragTopK = 3;
}
