package dk.ek.gruppe_i.llm_project.service;

import dk.ek.gruppe_i.llm_project.config.AiDeploymentProperties;
import dk.ek.gruppe_i.llm_project.dto.AiRequestDTO;
import dk.ek.gruppe_i.llm_project.enums.AiRequestType;
import dk.ek.gruppe_i.llm_project.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AiService {

    private final ChatClient chatClient;
    private final AiDeploymentProperties aiDeploymentProperties;
    private final HousingKnowledgeBase housingKnowledgeBase;

    public AiService(
        ChatClient.Builder chatClientBuilder,
        AiDeploymentProperties aiDeploymentProperties,
        HousingKnowledgeBase housingKnowledgeBase
    ) {
        this.chatClient = chatClientBuilder.build();
        this.aiDeploymentProperties = aiDeploymentProperties;
        this.housingKnowledgeBase = housingKnowledgeBase;
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
            // 4T's: Traits, Task, Tone, Target
            case HOUSING_CAPACITY -> """
                Traits: You are a careful urban data analyst. You only use the data in the request and the
                background material, and you say so when data is missing.
                Task: Estimate the maximum number of residents (capacity, a whole number of at least 1) of the
                single housing record in the request. Values written as "unknown" are missing, never zero.
                Tone: Neutral, precise and brief.
                Target: A backend service that parses your answer as JSON. Answer with one JSON object and
                nothing else: {"capacity": <integer>, "confidence": "low|medium|high", "reasoning": "<one or two sentences>"}
                """;
        };
    }

    private String buildUserMessage(AiRequestDTO request) {
        return switch (request.getRequestType()) {
            case CHARACTER_RECAP ->
                "Generate a character recap using these character details:\n" + request.getContext();
            case PATH_SUMMARY ->
                "Generate a journey summary using this journey history:\n" + request.getContext();
            case HOUSING_CAPACITY -> buildHousingMessage(request);
        };
    }

    private String buildHousingMessage(AiRequestDTO request) {
        String message = "Estimate the housing capacity for this record:\n" + request.getContext();
        boolean useRag = request.getUseRag() != null ? request.getUseRag() : aiDeploymentProperties.isRagEnabled();
        if (!useRag) {
            return message;
        }
        List<String> background;
        try {
            background = housingKnowledgeBase.retrieve(request.getContext());
        } catch (Exception ex) {
            // Fail soft: the estimate is still possible without background, only less informed.
            log.warn("Retrieval failed, answering without background material", ex);
            return message;
        }
        log.info("Retrieved {} background chunks for HOUSING_CAPACITY", background.size());
        return "Background material:\n" + String.join("\n\n---\n\n", background) + "\n\n" + message;
    }
}
