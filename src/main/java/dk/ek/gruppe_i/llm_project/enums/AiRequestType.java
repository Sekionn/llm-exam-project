package dk.ek.gruppe_i.llm_project.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The type of AI request to perform")
public enum AiRequestType {

    @Schema(description = "Estimates the typical number of residents of apartment buildings from their OSM data")
    HOUSING_CAPACITY
}
