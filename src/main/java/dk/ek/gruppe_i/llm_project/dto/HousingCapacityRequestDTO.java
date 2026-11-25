package dk.ek.gruppe_i.llm_project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Capacity estimate for one or more apartment buildings")
public record HousingCapacityRequestDTO(
        @Schema(description = "The buildings to estimate (1 to 50)")
        @NotEmpty(message = "buildings is required")
        @Size(max = 50, message = "at most 50 buildings per request")
        List<@Valid @NotNull(message = "a building must not be null") HousingBuildingDTO> buildings,
        @Schema(description = "true/false forces retrieval (RAG) on or off; omit to use the server default (app.ai.rag-enabled)",
                example = "true")
        Boolean useRag) {
}
