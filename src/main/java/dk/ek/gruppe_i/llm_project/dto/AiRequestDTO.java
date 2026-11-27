package dk.ek.gruppe_i.llm_project.dto;

import dk.ek.gruppe_i.llm_project.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Request body for AI endpoints")
public class AiRequestDTO {

    @Schema(
        description = "The type of AI operation to perform",
        allowableValues = {"HOUSING_CAPACITY"},
        example = "HOUSING_CAPACITY"
    )
    @NotNull(message = "requestType is required")
    private AiRequestType requestType;

    @Schema(description = "Apartment buildings to estimate (1 to 50). Omit unknown building fields.")
    @NotEmpty(message = "buildings is required")
    @Size(max = 50, message = "at most 50 buildings per request")
    private List<@Valid @NotNull(message = "a building must not be null") HousingBuildingDTO> buildings;

    @Schema(description = "Only used by HOUSING_CAPACITY: true/false forces retrieval (RAG) on or off; "
            + "omit to use the server default (app.ai.rag-enabled)", example = "true")
    private Boolean useRag;
}
