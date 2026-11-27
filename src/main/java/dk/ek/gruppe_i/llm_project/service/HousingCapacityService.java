package dk.ek.gruppe_i.llm_project.service;

import dk.ek.gruppe_i.llm_project.config.AiDeploymentProperties;
import dk.ek.gruppe_i.llm_project.dto.HousingBuildingDTO;
import dk.ek.gruppe_i.llm_project.dto.HousingCapacityRequestDTO;
import dk.ek.gruppe_i.llm_project.dto.HousingCapacityResponseDTO;
import dk.ek.gruppe_i.llm_project.enums.AiRequestType;
import dk.ek.gruppe_i.llm_project.housing.BaselineParameters;
import dk.ek.gruppe_i.llm_project.housing.HousingCapacityEstimator;
import org.springframework.stereotype.Service;

import java.util.List;

/** Spring glue around {@link HousingCapacityEstimator}: connects the chat model and the knowledge base. */
@Service
public class HousingCapacityService {

    private final HousingCapacityEstimator estimator;
    private final AiDeploymentProperties properties;

    public HousingCapacityService(AiService aiService, AiDeploymentProperties properties,
                                  HousingKnowledgeBase knowledgeBase, BaselineParameters baselineParameters) {
        this.properties = properties;
        this.estimator = new HousingCapacityEstimator(
                userMessage -> aiService.complete(AiRequestType.HOUSING_CAPACITY, userMessage),
                knowledgeBase::retrieveApartmentRules,
                baselineParameters);
    }

    /** Buildings are estimated one after the other (a local model handles one request at a time anyway). */
    public HousingCapacityResponseDTO estimate(HousingCapacityRequestDTO request) {
        boolean useRag = request.useRag() != null ? request.useRag() : properties.isRagEnabled();
        List<HousingCapacityResponseDTO.Item> items = request.buildings().stream()
                .map(HousingBuildingDTO::toBuilding)
                .map(building -> estimator.estimate(building, useRag))
                .map(HousingCapacityResponseDTO.Item::from)
                .toList();
        return new HousingCapacityResponseDTO(items);
    }
}
