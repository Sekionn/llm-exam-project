package dk.ek.gruppe_i.llm_project.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("llm_project API")
                        .version("1.0")
                        .description("Apartment capacity estimates using a local LLM with RAG."));
    }
}
