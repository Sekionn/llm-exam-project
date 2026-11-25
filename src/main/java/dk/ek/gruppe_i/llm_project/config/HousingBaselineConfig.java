package dk.ek.gruppe_i.llm_project.config;

import dk.ek.gruppe_i.llm_project.housing.BaselineParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Loads the numbers of the baseline rule that housing_eda.ipynb wrote to rag/baseline-parameters.properties. */
@Configuration
public class HousingBaselineConfig {

    static final String RESOURCE = "rag/baseline-parameters.properties";

    @Bean
    public BaselineParameters baselineParameters() {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            properties.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + RESOURCE + ". Copy it from the notebook's rag_export folder.", ex);
        }
        return BaselineParameters.fromProperties(properties);
    }
}
