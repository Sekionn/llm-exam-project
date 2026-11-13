package dk.ek.gruppe_i.llm_project.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The type of AI request to perform")
public enum AiRequestType {

    @Schema(description = "Generates and returns a narrative summary of the supplied journey history")
    PATH_SUMMARY,
    @Schema(description = "Generates an immersive second-person recap of the character's current state")
    CHARACTER_RECAP
}
