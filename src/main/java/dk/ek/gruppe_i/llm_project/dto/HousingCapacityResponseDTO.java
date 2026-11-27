package dk.ek.gruppe_i.llm_project.dto;

import dk.ek.gruppe_i.llm_project.housing.HousingCapacityEstimator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Schema(description = "One result per building, in request order")
@Getter
@AllArgsConstructor
public final class HousingCapacityResponseDTO extends BaseResponse {

    private final List<Item> results;

    @Schema(description = "Result for one building. If error is set, capacity is null.")
    @Getter
    @AllArgsConstructor
    public static final class Item extends BaseResponse {

        private final String id;
        @Schema(description = "A (flats known) to E (nothing known)", example = "B")
        private final String tier;
        @Schema(description = "Which fields are known", example = "levels known, flats unknown, footprint known")
        private final String situation;
        @Schema(description = "Typical number of residents (model answer, limited by the ceiling)", example = "63")
        private final Integer capacity;
        @Schema(description = "What the model answered before the ceiling was applied")
        private final Integer modelCapacity;
        @Schema(example = "medium")
        private final String confidence;
        private final String reasoning;
        @Schema(description = "true if the model answer was above the ceiling and was limited")
        private final boolean cappedByCeiling;
        @Schema(description = "Usable area / 20 m2 per person; null when footprint or levels are unknown")
        private final Integer ceiling;
        @Schema(description = "Rule-based value from the EDA notebook, for comparison")
        private final Integer baselineCapacity;
        private final String baselineBasis;
        @Schema(description = "true if background material was added to the prompt")
        private final boolean ragUsed;
        @Schema(description = "Set when no answer was obtained for this building")
        private final String error;

        public static Item from(HousingCapacityEstimator.Result r) {
            return new Item(r.id(), r.tier(), r.situation(), r.capacity(), r.modelCapacity(), r.confidence(), r.reasoning(),
                    r.cappedByCeiling(), r.ceiling(), r.baselineCapacity(), r.baselineBasis(), r.ragUsed(), r.error());
        }
    }
}
