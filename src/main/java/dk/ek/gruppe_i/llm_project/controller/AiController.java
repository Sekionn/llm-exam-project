package dk.ek.gruppe_i.llm_project.controller;

import dk.ek.gruppe_i.llm_project.dto.AiRequestDTO;
import dk.ek.gruppe_i.llm_project.dto.AiResponseDTO;
import dk.ek.gruppe_i.llm_project.service.AiService;
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
    public AiController(AiService aiService) {
        this.aiService = aiService;
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

}
