package dk.ek.gruppe_i.llm_project.dto;

import dk.ek.gruppe_i.llm_project.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Request body for AI endpoints")
public class AiRequestDTO {

    @Schema(
        description = "The type of AI operation to perform",
        allowableValues = {"CHARACTER_RECAP", "PATH_SUMMARY"},
        example = "CHARACTER_RECAP"
    )
    @NotNull(message = "requestType is required")
    private AiRequestType requestType;

    @Schema(description = "Optional character identifier supplied by the caller; no data is looked up", example = "1")
    @Positive(message = "characterId must be positive")
    private Integer characterId;

    @Schema(description = "Character details or journey history to use when generating the response",
            example = "Arin is an elven ranger who crossed the forest and rescued a lost traveler.")
    @NotBlank(message = "context is required")
    private String context;
}
