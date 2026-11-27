package dk.ek.gruppe_i.llm_project.dto;

import dk.ek.gruppe_i.llm_project.housing.HousingCapacityEstimator;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "One result per building, in request order")
public record HousingCapacityResponseDTO(List<Item> results) {

    @Schema(description = "Result for one building. If error is set, capacity is null.")
    public record Item(
            String id,
            @Schema(description = "A (flats known) to E (nothing known)", example = "B") String tier,
            @Schema(description = "Which fields are known", example = "levels known, flats unknown, footprint known") String situation,
            @Schema(description = "Typical number of residents (model answer, limited by the ceiling)", example = "63") Integer capacity,
            @Schema(description = "What the model answered before the ceiling was applied") Integer modelCapacity,
            @Schema(example = "medium") String confidence,
            String reasoning,
            @Schema(description = "true if the model answer was above the ceiling and was limited") boolean cappedByCeiling,
            @Schema(description = "Usable area / 20 m2 per person; null when footprint or levels are unknown") Integer ceiling,
            @Schema(description = "Rule-based value from the EDA notebook, for comparison") Integer baselineCapacity,
            String baselineBasis,
            @Schema(description = "true if background material was added to the prompt") boolean ragUsed,
            @Schema(description = "Set when no answer was obtained for this building") String error) {

        public static Item from(HousingCapacityEstimator.Result r) {
            return new Item(r.id(), r.tier(), r.situation(), r.capacity(), r.modelCapacity(), r.confidence(), r.reasoning(),
                    r.cappedByCeiling(), r.ceiling(), r.baselineCapacity(), r.baselineBasis(), r.ragUsed(), r.error());
        }
    }
}
