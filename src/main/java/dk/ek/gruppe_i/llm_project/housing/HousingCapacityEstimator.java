package dk.ek.gruppe_i.llm_project.housing;

import java.util.List;

/**
 * Estimates one apartment building: builds the situation, asks the model (with or without background
 * material), reads and checks the answer, applies the legal ceiling and computes the baseline for comparison.
 * No Spring and no Ollama in here; the model and the knowledge base are small interfaces.
 */
public class HousingCapacityEstimator {

    /** One call to the language model. Throws if the model cannot be reached. */
    @FunctionalInterface
    public interface Model {
        String ask(String userMessage);
    }

    /** Retrieval: the most relevant knowledge chunks for a query, best first. */
    @FunctionalInterface
    public interface Background {
        List<String> retrieve(String query);
    }

    /**
     * capacity: the answer to use (model answer, limited by the ceiling). modelCapacity: what the model said.
     * baselineCapacity: the rule-based value for comparison. error is set when no model answer was obtained.
     */
    public record Result(String id, String tier, String situation, Integer capacity, Integer modelCapacity,
                         String confidence, String reasoning, boolean cappedByCeiling, Integer ceiling,
                         Integer baselineCapacity, String baselineBasis, boolean ragUsed, String error) {
    }

    private final Model model;
    private final Background background;
    private final BaselineParameters parameters;

    public HousingCapacityEstimator(Model model, Background background, BaselineParameters parameters) {
        this.model = model;
        this.background = background;
        this.parameters = parameters;
    }

    public Result estimate(HousingBuilding building, boolean useRag) {
        List<String> problems = building.problems();
        if (!problems.isEmpty()) {
            return new Result(building.id(), null, null, null, null, null, null, false, null, null, null, false,
                    "invalid building: " + String.join("; ", problems));
        }
        HousingSituation.Situation situation = HousingSituation.of(building, parameters.metersPerLevel());
        HousingBaseline.Estimate baseline = HousingBaseline.estimate(building, situation, parameters);
        Integer ceiling = HousingBaseline.ceiling(building, situation, parameters);
        String context = HousingSituation.contextText(building, situation);

        boolean ragUsed = false;
        List<String> chunks = List.of();
        if (useRag) {
            try {
                chunks = background.retrieve(HousingSituation.retrievalQuery(situation));
                ragUsed = !chunks.isEmpty();
            } catch (RuntimeException ex) {
                // Fail soft: the estimate is still possible without background, only less informed.
                chunks = List.of();
            }
        }

        String userMessage = HousingPrompts.user(context, chunks);
        CapacityAnswerParser.Answer answer;
        try {
            answer = ask(userMessage);
        } catch (CapacityAnswerParser.AnswerException ex) {
            return failed(building, situation, baseline, ceiling, ragUsed, "model answer could not be read: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return failed(building, situation, baseline, ceiling, ragUsed, "model unavailable: " + ex.getMessage());
        }

        int used = answer.capacity();
        String confidence = answer.confidence();
        String reasoning = answer.reasoning();
        boolean capped = ceiling != null && used > ceiling;
        if (capped) {
            used = ceiling;
            confidence = "low";
            reasoning = reasoning + (reasoning.isEmpty() ? "" : " ") + "[Limited to " + ceiling
                    + " by the " + HousingSituation.format(parameters.legalM2PerPerson()) + " m2 per person ceiling.]";
        }
        return new Result(building.id(), situation.tier(), situation.phrase(), used, answer.capacity(), confidence, reasoning,
                capped, ceiling, baseline.capacity(), baseline.basis(), ragUsed, null);
    }

    /** One retry when the answer cannot be read; a model that cannot be reached is not retried. */
    private CapacityAnswerParser.Answer ask(String userMessage) {
        String raw = model.ask(userMessage);
        try {
            return CapacityAnswerParser.parse(raw);
        } catch (CapacityAnswerParser.AnswerException first) {
            String again = model.ask(userMessage + HousingPrompts.retryReminder());
            return CapacityAnswerParser.parse(again);
        }
    }

    private Result failed(HousingBuilding building, HousingSituation.Situation situation, HousingBaseline.Estimate baseline,
                          Integer ceiling, boolean ragUsed, String error) {
        return new Result(building.id(), situation.tier(), situation.phrase(), null, null, null, null, false, ceiling,
                baseline.capacity(), baseline.basis(), ragUsed, error);
    }
}
