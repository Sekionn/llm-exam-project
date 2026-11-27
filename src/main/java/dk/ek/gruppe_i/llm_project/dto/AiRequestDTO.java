package dk.ek.gruppe_i.llm_project.dto;

import dk.ek.gruppe_i.llm_project.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for AI endpoints")
public class AiRequestDTO {

    @Schema(
        description = "The type of AI operation to perform",
        allowableValues = {"HOUSING_CAPACITY"},
        example = "HOUSING_CAPACITY"
    )
    @NotNull(message = "requestType is required")
    private AiRequestType requestType;

    @Schema(description = "Housing data to use when generating the response",
            example = "housingType: apartment\nnumberOfLevels: 4\nnumberOfFlats: 20\nheightMeters: unknown")
    @NotBlank(message = "context is required")
    private String context;

    @Schema(description = "Only used by HOUSING_CAPACITY: true/false forces retrieval (RAG) on or off; "
            + "omit to use the server default (app.ai.rag-enabled)", example = "true")
    private Boolean useRag;
}
