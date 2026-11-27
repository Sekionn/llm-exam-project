package dk.ek.gruppe_i.llm_project.dto;

import dk.ek.gruppe_i.llm_project.housing.HousingBuilding;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "One apartment building. Leave a field out (or null) when it is unknown; unknown is never 0.")
public record HousingBuildingDTO(
        @Schema(description = "Caller's own identifier, returned unchanged", example = "way/4393716")
        @Size(max = 100) String id,
        @Schema(description = "Footprint (ground area) in m2", example = "630")
        @DecimalMin(value = "10", message = "footprintM2 must be at least 10")
        @DecimalMax(value = "20000", message = "footprintM2 must be at most 20000") Double footprintM2,
        @Schema(description = "Number of building levels (floors above ground)", example = "5")
        @Min(value = 1, message = "numberOfLevels must be at least 1")
        @Max(value = 60, message = "numberOfLevels must be at most 60") Integer numberOfLevels,
        @Schema(description = "Number of flats, if known", example = "30")
        @Min(value = 1, message = "numberOfFlats must be at least 1")
        @Max(value = 500, message = "numberOfFlats must be at most 500") Integer numberOfFlats,
        @Schema(description = "Building height in metres, used when the number of levels is unknown", example = "15")
        @DecimalMin(value = "1", message = "heightMeters must be at least 1")
        @DecimalMax(value = "200", message = "heightMeters must be at most 200") Double heightMeters) {

    public HousingBuilding toBuilding() {
        return new HousingBuilding(id, footprintM2, numberOfLevels, numberOfFlats, heightMeters);
    }
}
