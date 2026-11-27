package dk.ek.gruppe_i.llm_project.housing;

import java.math.BigDecimal;

/**
 * Describes which fields of a building are known. The phrase is the same wording as the headings of the
 * rule chunks in {@code rag/apartment-rules.md}, so retrieval matches on words instead of numbers.
 */
public final class HousingSituation {

    /** Tier A..E, the situation phrase, and the number of levels to use (from levels or height), or null. */
    public record Situation(String tier, String phrase, Integer effectiveLevels) {
    }

    private HousingSituation() {
    }

    public static Situation of(HousingBuilding building, double metersPerLevel) {
        Integer levels = building.effectiveLevels(metersPerLevel);
        boolean footprint = building.footprintM2() != null;
        if (building.numberOfFlats() != null) {
            return new Situation("A", "flats known", levels);
        }
        String tier;
        if (levels != null) {
            tier = footprint ? "B" : "C";
        } else {
            tier = footprint ? "D" : "E";
        }
        String phrase = (levels != null ? "levels known" : "levels unknown")
                + ", flats unknown, "
                + (footprint ? "footprint known" : "footprint unknown");
        return new Situation(tier, phrase, levels);
    }

    /** The text sent to the model. Same lines as the examples in {@code apartment-examples.md}. */
    public static String contextText(HousingBuilding building, Situation situation) {
        return "buildingType: apartment building\n"
                + "footprintM2: " + whole(building.footprintM2()) + "\n"
                + "numberOfLevels: " + whole(situation.effectiveLevels()) + "\n"
                + "numberOfFlats: " + whole(building.numberOfFlats()) + "\n"
                + "heightMeters: " + general(building.heightMeters());
    }

    /** Query for the knowledge base: words, not numbers. */
    public static String retrievalQuery(Situation situation) {
        return "apartment building, " + situation.phrase();
    }

    static String whole(Number value) {
        return value == null ? "unknown" : Long.toString((long) Math.rint(value.doubleValue()));
    }

    /** Like Python's {@code format(x, "g")} for ordinary values: 15.0 -> 15, 3.64 -> 3.64. */
    public static String general(Double value) {
        if (value == null) {
            return "unknown";
        }
        return format(value);
    }

    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }
}
