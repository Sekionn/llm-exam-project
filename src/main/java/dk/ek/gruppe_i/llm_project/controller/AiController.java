package dk.ek.gruppe_i.llm_project.controller;

import dk.ek.gruppe_i.llm_project.dto.AiRequestDTO;
import dk.ek.gruppe_i.llm_project.dto.AiResponseDTO;
import dk.ek.gruppe_i.llm_project.dto.HousingCapacityRequestDTO;
import dk.ek.gruppe_i.llm_project.dto.HousingCapacityResponseDTO;
import dk.ek.gruppe_i.llm_project.service.AiService;
import dk.ek.gruppe_i.llm_project.service.HousingCapacityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/choose-your-fate/ai")
@Tag(name = "AI", description = "Endpoints for AI-generated content using a configured LLM provider")
public class AiController {

    private final AiService aiService;
    private final HousingCapacityService housingCapacityService;

    public AiController(AiService aiService, HousingCapacityService housingCapacityService) {
        this.aiService = aiService;
        this.housingCapacityService = housingCapacityService;
    }

    @PostMapping("/ask")

    @Operation(
        summary = "Ask the AI",
        description = "Generates a response from the supplied context. Possible request types: CHARACTER_RECAP, PATH_SUMMARY"
    )
    @ApiResponse(responseCode = "503", description = "AI generation is unavailable; try again later")
    public AiResponseDTO ask(@Valid @RequestBody AiRequestDTO request) {
        return new AiResponseDTO(aiService.handleRequest(request));
    }

    @PostMapping("/housing-capacity")
    @Operation(
        summary = "Estimate the capacity of apartment buildings",
        description = "Takes 1 to 50 apartment buildings with footprintM2, numberOfLevels, numberOfFlats and heightMeters "
            + "(leave unknown fields out). Returns the typical number of residents per building, with confidence, reasoning "
            + "and a rule-based baseline for comparison. A building that fails gets an error in its result, the rest are still answered."
    )
    public HousingCapacityResponseDTO housingCapacity(@Valid @RequestBody HousingCapacityRequestDTO request) {
        return housingCapacityService.estimate(request);
    }

}
