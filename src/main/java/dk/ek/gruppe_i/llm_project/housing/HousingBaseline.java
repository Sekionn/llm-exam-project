package dk.ek.gruppe_i.llm_project.housing;

import java.util.Locale;

/**
 * The documented capacity rule from the EDA notebook (section 6), in Java. It is used to compare the model's
 * answer with a transparent rule, and to compute the legal ceiling. It is a rule, not ground truth.
 * Rounding is half-to-even ({@link Math#rint}) so the results match the notebook exactly.
 */
public final class HousingBaseline {

    /** capacity: typical number of residents; confidence: high, medium or low; flag: "" or a size warning. */
    public record Estimate(int capacity, String basis, String confidence, String flag) {
    }

    private HousingBaseline() {
    }

    /** "small footprint" (probably an annex), "large footprint" (may be several buildings), or "". */
    public static String flag(HousingBuilding building, BaselineParameters p) {
        Double footprint = building.footprintM2();
        if (footprint == null) {
            return "";
        }
        if (footprint < p.smallFootprintM2()) {
            return "small footprint";
        }
        if (footprint > p.largeFootprintM2()) {
            return "large footprint";
        }
        return "";
    }

    public static Estimate estimate(HousingBuilding building, HousingSituation.Situation situation, BaselineParameters p) {
        if (building.numberOfFlats() != null) {
            int flats = building.numberOfFlats();
            int capacity = (int) Math.max(1, Math.rint(flats * p.personsPerFlat()));
            return new Estimate(capacity, flats + " flats x " + g(p.personsPerFlat()) + " persons per flat", "high", "");
        }
        StringBuilder notes = new StringBuilder();
        double footprint;
        if (building.footprintM2() == null) {
            footprint = p.medianFootprintM2();
            notes.append("; footprint missing, median footprint ").append(f0(footprint)).append(" m2 used");
        } else {
            footprint = building.footprintM2();
        }
        double levels;
        if (situation.effectiveLevels() == null) {
            levels = p.levelsFor(building.footprintM2());
            notes.append("; levels missing, median of similar buildings (").append(g(levels)).append(") used");
        } else {
            levels = situation.effectiveLevels();
        }
        double usable = footprint * levels * p.floorEfficiency();
        double capacity = usable / p.m2PerPerson();
        int ceiling = (int) Math.max(1, Math.floor(usable / p.legalM2PerPerson()));
        int rounded = (int) Math.max(1, Math.rint(capacity));
        int finalCapacity = Math.min(rounded, ceiling);
        StringBuilder basis = new StringBuilder();
        basis.append("footprint ").append(f0(footprint)).append(" m2 x ").append(g(levels)).append(" floors x ")
                .append(g(p.floorEfficiency())).append(" = ").append(f0(usable)).append(" m2 usable; ")
                .append(f0(usable)).append(" / ").append(g(p.m2PerPerson())).append(" m2 per person = ")
                .append(String.format(Locale.ROOT, "%.1f", capacity));
        if (finalCapacity != rounded) {
            basis.append("; limited to ").append(ceiling).append(" by the 20 m2 per person ceiling");
        }
        basis.append(notes);
        String confidence = "B".equals(situation.tier()) ? "medium" : "low";
        String flag = flag(building, p);
        if (!flag.isEmpty()) {
            confidence = "low";
            basis.append("; flag: ").append(flag);
        }
        return new Estimate(finalCapacity, basis.toString(), confidence, flag);
    }

    /**
     * Highest plausible number of residents (usable area / legal m2 per person), or {@code null} when the
     * footprint or the number of levels is not given. Applied to the model's answer by the backend.
     */
    public static Integer ceiling(HousingBuilding building, HousingSituation.Situation situation, BaselineParameters p) {
        if (building.footprintM2() == null || situation.effectiveLevels() == null) {
            return null;
        }
        double usable = building.footprintM2() * situation.effectiveLevels() * p.floorEfficiency();
        return (int) Math.max(1, Math.floor(usable / p.legalM2PerPerson()));
    }

    private static String g(double value) {
        return HousingSituation.format(value);
    }

    private static String f0(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
