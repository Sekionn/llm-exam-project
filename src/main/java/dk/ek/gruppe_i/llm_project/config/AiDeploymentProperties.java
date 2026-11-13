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

    private boolean stripMarkdown = true;
}
