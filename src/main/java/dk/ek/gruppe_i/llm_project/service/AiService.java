package dk.ek.gruppe_i.llm_project.service;

import dk.ek.gruppe_i.llm_project.config.AiDeploymentProperties;
import dk.ek.gruppe_i.llm_project.dto.AiRequestDTO;
import dk.ek.gruppe_i.llm_project.dto.BaseResponse;
import dk.ek.gruppe_i.llm_project.dto.HousingBuildingDTO;
import dk.ek.gruppe_i.llm_project.dto.HousingCapacityResponseDTO;
import dk.ek.gruppe_i.llm_project.housing.BaselineParameters;
import dk.ek.gruppe_i.llm_project.housing.HousingCapacityEstimator;
import dk.ek.gruppe_i.llm_project.housing.HousingPrompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final ChatClient chatClient;
    private final AiDeploymentProperties properties;
    private final HousingKnowledgeBase knowledgeBase;
    private final BaselineParameters baselineParameters;

    public AiService(ChatClient.Builder chatClientBuilder, AiDeploymentProperties properties,
                     HousingKnowledgeBase knowledgeBase, BaselineParameters baselineParameters) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.knowledgeBase = knowledgeBase;
        this.baselineParameters = baselineParameters;
    }

    public BaseResponse handleRequest(AiRequestDTO request) {
        return switch (request.getRequestType()) {
            case HOUSING_CAPACITY -> estimateHousing(request, HousingPrompts.SYSTEM);
        };
    }

    private HousingCapacityResponseDTO estimateHousing(AiRequestDTO request, String systemPrompt) {
        boolean useRag = request.getUseRag() != null ? request.getUseRag() : properties.isRagEnabled();
        HousingCapacityEstimator estimator = new HousingCapacityEstimator(
                userMessage -> complete(systemPrompt, userMessage),
                knowledgeBase::retrieveApartmentRules,
                baselineParameters);

        // Estimate sequentially and preserve request order; one failed building does not stop the batch.
        var results = request.getBuildings().stream()
                .map(HousingBuildingDTO::toBuilding)
                .map(building -> estimator.estimate(building, useRag))
                .map(HousingCapacityResponseDTO.Item::from)
                .toList();
        return new HousingCapacityResponseDTO(results);
    }

    private String complete(String systemPrompt, String userMessage) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "AI provider request failed. Check AI deployment configuration and provider availability.", ex);
        }
    }
}
