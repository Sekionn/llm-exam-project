package dk.ek.gruppe_i.llm_project.housing;

import java.util.Properties;

/**
 * The numbers of the baseline capacity rule. They are written by the EDA notebook to
 * {@code rag/baseline-parameters.properties}, so the API and the RAG material use the same figures.
 */
public record BaselineParameters(double m2PerPerson, double personsPerFlat, double legalM2PerPerson,
                                 double floorEfficiency, double metersPerLevel, double medianFootprintM2,
                                 double overallLevels, double smallFootprintM2, double largeFootprintM2,
                                 double[] footprintBins, double[] levelsByClass) {

    public BaselineParameters {
        if (m2PerPerson <= 0 || personsPerFlat <= 0 || legalM2PerPerson <= 0 || floorEfficiency <= 0
                || metersPerLevel <= 0 || medianFootprintM2 <= 0 || overallLevels < 1) {
            throw new IllegalArgumentException("baseline parameters must be positive");
        }
        if (footprintBins.length == 0 || levelsByClass.length != footprintBins.length) {
            throw new IllegalArgumentException("levelsByClass needs one value per footprintBins entry (lower bounds)");
        }
        for (int i = 1; i < footprintBins.length; i++) {
            if (footprintBins[i] <= footprintBins[i - 1]) {
                throw new IllegalArgumentException("footprintBins must be ascending");
            }
        }
    }

    /** footprintBins are the lower bounds of the classes (0, 100, 250, ...); the last class is open-ended.
     * Median number of levels of buildings with a similar footprint; the overall median if the footprint is unknown. */
    public double levelsFor(Double footprint) {
        if (footprint == null) {
            return overallLevels;
        }
        int index = 0;
        for (int i = 0; i < footprintBins.length; i++) {
            if (footprint >= footprintBins[i]) {
                index = i;
            }
        }
        return levelsByClass[index];
    }

    public static BaselineParameters fromProperties(Properties p) {
        return new BaselineParameters(
                number(p, "m2PerPerson"), number(p, "personsPerFlat"), number(p, "legalM2PerPerson"),
                number(p, "floorEfficiency"), number(p, "mPerLevel"), number(p, "medianFootprintM2"),
                number(p, "overallLevels"), number(p, "smallFootprintM2"), number(p, "largeFootprintM2"),
                list(p, "footprintBins"), list(p, "levelsByClass"));
    }

    private static double number(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("baseline parameter missing: " + key);
        }
        return Double.parseDouble(value.trim());
    }

    private static double[] list(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("baseline parameter missing: " + key);
        }
        String[] parts = value.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }
}
