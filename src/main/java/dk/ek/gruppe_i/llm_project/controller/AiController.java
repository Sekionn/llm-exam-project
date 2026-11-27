package dk.ek.gruppe_i.llm_project.controller;

import dk.ek.gruppe_i.llm_project.dto.AiRequestDTO;
import dk.ek.gruppe_i.llm_project.dto.BaseResponse;
import dk.ek.gruppe_i.llm_project.dto.HousingCapacityResponseDTO;
import dk.ek.gruppe_i.llm_project.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@Tag(name = "AI", description = "AI generation using a configured local LLM")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/ask")
    @Operation(
        summary = "Ask the AI",
        description = "Selects the prompt using requestType. HOUSING_CAPACITY estimates 1 to 50 apartment buildings "
                + "and returns parsed results with confidence, reasoning, a capacity ceiling and a baseline comparison. "
                + "Leave unknown building fields out. A building that fails gets an error in its result; the rest are still answered."
    )
    @ApiResponse(responseCode = "200", description = "Response for the selected request type",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(oneOf = {HousingCapacityResponseDTO.class})))
    public BaseResponse ask(@Valid @RequestBody AiRequestDTO request) {
        return aiService.handleRequest(request);
    }
}
