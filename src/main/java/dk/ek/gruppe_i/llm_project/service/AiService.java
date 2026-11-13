package dk.ek.gruppe_i.llm_project.service;

import dk.ek.gruppe_i.llm_project.config.AiDeploymentProperties;
import dk.ek.gruppe_i.llm_project.dto.AiRequestDTO;
import dk.ek.gruppe_i.llm_project.enums.AiRequestType;
import dk.ek.gruppe_i.llm_project.exception.AiServiceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final ChatClient chatClient;
    private final AiDeploymentProperties aiDeploymentProperties;

    public AiService(
        ChatClient.Builder chatClientBuilder,
        AiDeploymentProperties aiDeploymentProperties
    ) {
        this.chatClient = chatClientBuilder.build();
        this.aiDeploymentProperties = aiDeploymentProperties;
    }

    public String handleRequest(AiRequestDTO request) {
        validateRequest(request);

        String raw;
        try {
            raw = chatClient.prompt()
                .system(resolveSystemPrompt(request.getRequestType()))
                .user(buildUserMessage(request))
                .call()
                .content();

        } catch (Exception ex) {
            throw new AiServiceException(
                "AI provider request failed. Check AI deployment configuration and provider availability.",
                ex
            );
        }

        if (raw == null || raw.isBlank()) {
            throw new AiServiceException("AI provider returned an empty response.");
        }

        String response = aiDeploymentProperties.isStripMarkdown() ? stripMarkdown(raw) : raw.trim();
        if (response.isBlank()) {
            throw new AiServiceException("AI provider returned an empty response.");
        }
        return response;
    }

    private String stripMarkdown(String text) {
        return text
            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
            .replaceAll("\\*([^*]+)\\*", "$1")
            .replaceAll("(?m)^#{1,6}\\s*", "")
            .trim();
    }

    private void validateRequest(AiRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.getRequestType() == null) {
            throw new IllegalArgumentException("requestType is required.");
        }
        if (request.getContext() == null || request.getContext().isBlank()) {
            throw new IllegalArgumentException("context is required.");
        }
    }

    private String resolveSystemPrompt(AiRequestType type) {
        return switch (type) {
            case CHARACTER_RECAP -> """
                You are a narrator in a fantasy RPG called "Choose Your Fate".
                Your task is to write a brief, immersive character recap in second person (e.g., "You are...").

                Rules:
                - Use the character details provided in the request context.
                - Only state facts from that context. Do not invent stats or lore.
                - Keep the recap to 3-5 sentences.
                - Write in an engaging, fantasy-RPG tone.
                """;
            case PATH_SUMMARY -> """
                You are a chronicler in a fantasy RPG called "Choose Your Fate".
                Your task is to write a narrative summary of the character's journey.

                Rules:
                - Use the journey history provided in the request context.
                - Write a compelling, lore-appropriate narrative summary (2-4 sentences, past tense, third person).
                - Only describe events that appear in that context. Do not invent scenes or choices.
                - Return the generated summary as your final response.
                """;
        };
    }

    private String buildUserMessage(AiRequestDTO request) {
        return switch (request.getRequestType()) {
            case CHARACTER_RECAP ->
                "Generate a character recap using these character details:\n" + request.getContext();
            case PATH_SUMMARY ->
                "Generate a journey summary using this journey history:\n" + request.getContext();
        };
    }
}
