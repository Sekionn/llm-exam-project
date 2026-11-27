package dk.ek.gruppe_i.llm_project.service;

import dk.ek.gruppe_i.llm_project.config.AiDeploymentProperties;
import dk.ek.gruppe_i.llm_project.dto.AiRequestDTO;
import dk.ek.gruppe_i.llm_project.enums.AiRequestType;
import dk.ek.gruppe_i.llm_project.exception.AiServiceException;
import dk.ek.gruppe_i.llm_project.housing.HousingPrompts;
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

        String raw = complete(request.getRequestType(), buildUserMessage(request));

        if (raw == null || raw.isBlank()) {
            throw new AiServiceException("AI provider returned an empty response.");
        }

        String response = aiDeploymentProperties.isStripMarkdown() ? stripMarkdown(raw) : raw.trim();
        if (response.isBlank()) {
            throw new AiServiceException("AI provider returned an empty response.");
        }
        return response;
    }

    /**
     * Calls the chat model with the system prompt chosen for this request type. Returns the raw model text
     * (may be blank). Throws AiServiceException only when the provider call itself fails.
     */
    public String complete(AiRequestType type, String userMessage) {
        try {
            return chatClient.prompt()
                .system(resolveSystemPrompt(type))
                .user(userMessage)
                .call()
                .content();
        } catch (Exception ex) {
            throw new AiServiceException(
                "AI provider request failed. Check AI deployment configuration and provider availability.", ex);
        }
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
        // To add a request type: add the enum value, then add a case here and in the other switch.
        // 4T's: Traits, Task, Tone, Target
        return switch (type) {
            case HOUSING_CAPACITY -> HousingPrompts.SYSTEM;
        };
    }

    private String buildUserMessage(AiRequestDTO request) {
        // To add a request type: add the enum value, then add a case here and in the other switch.
        return switch (request.getRequestType()) {
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
