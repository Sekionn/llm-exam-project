package dk.ek.gruppe_i.llm_project.housing;

import java.util.ArrayList;
import java.util.List;

/**
 * One apartment building as the caller describes it. {@code null} means "unknown", never zero.
 * Plain Java, no framework: the estimation rules can be tested without Spring or Ollama.
 */
public record HousingBuilding(String id, Double footprintM2, Integer numberOfLevels,
                              Integer numberOfFlats, Double heightMeters) {

    public static final double MIN_FOOTPRINT_M2 = 10;
    public static final double MAX_FOOTPRINT_M2 = 20000;
    public static final int MIN_LEVELS = 1;
    public static final int MAX_LEVELS = 60;
    public static final int MIN_FLATS = 1;
    public static final int MAX_FLATS = 500;
    public static final double MIN_HEIGHT_M = 1;
    public static final double MAX_HEIGHT_M = 200;

    /** Values outside the plausible range (the same limits as the EDA notebook). Empty list = valid. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (footprintM2 != null && !(footprintM2 >= MIN_FOOTPRINT_M2 && footprintM2 <= MAX_FOOTPRINT_M2)) {
            problems.add("footprintM2 must be between " + (int) MIN_FOOTPRINT_M2 + " and " + (int) MAX_FOOTPRINT_M2);
        }
        if (numberOfLevels != null && (numberOfLevels < MIN_LEVELS || numberOfLevels > MAX_LEVELS)) {
            problems.add("numberOfLevels must be between " + MIN_LEVELS + " and " + MAX_LEVELS);
        }
        if (numberOfFlats != null && (numberOfFlats < MIN_FLATS || numberOfFlats > MAX_FLATS)) {
            problems.add("numberOfFlats must be between " + MIN_FLATS + " and " + MAX_FLATS);
        }
        if (heightMeters != null && !(heightMeters >= MIN_HEIGHT_M && heightMeters <= MAX_HEIGHT_M)) {
            problems.add("heightMeters must be between " + (int) MIN_HEIGHT_M + " and " + (int) MAX_HEIGHT_M);
        }
        return problems;
    }

    /**
     * The number of levels, or, when only a height is known, height / metersPerLevel rounded
     * (half to even, like the notebook). {@code null} when neither is known.
     */
    public Integer effectiveLevels(double metersPerLevel) {
        if (numberOfLevels != null) {
            return numberOfLevels;
        }
        if (heightMeters != null) {
            long levels = (long) Math.rint(heightMeters / metersPerLevel);
            return levels >= 1 ? (int) levels : null;
        }
        return null;
    }
}
