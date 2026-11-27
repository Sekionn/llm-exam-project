package dk.ek.gruppe_i.llm_project.housing;

import java.util.List;

/** Prompt text for the apartment capacity estimate (4T's: Traits, Task, Tone, Target). */
public final class HousingPrompts {

    public static final String SYSTEM = """
            Traits: You are a careful urban data analyst. You only use the data in the request and the
            background material. Values written as "unknown" are missing, never zero.
            Task: Estimate the typical number of residents of one apartment building (capacity, a whole number
            of at least 1). Capacity is the usual occupancy, not the legal maximum. Find the rule in the background
            material whose heading matches what is known about the building, follow it, and state in the reasoning
            every number used and every value that was assumed. Do the calculation exactly as the rule says,
            with one division at the end.
            Tone: Neutral, precise and brief.
            Target: A backend service that parses your answer as JSON. Answer with one JSON object and nothing
            else: {"capacity": <integer>, "confidence": "low|medium|high", "reasoning": "<one or two sentences>"}
            """;

    private HousingPrompts() {
    }

    public static String user(String context, List<String> background) {
        String task = "Estimate the typical number of residents of this apartment building:\n" + context;
        if (background == null || background.isEmpty()) {
            return task;
        }
        return "Background material:\n" + String.join("\n\n---\n\n", background) + "\n\n" + task;
    }

    public static String retryReminder() {
        return "\n\nYour previous answer could not be read. Answer with exactly one JSON object and nothing else: "
                + "{\"capacity\": <integer>, \"confidence\": \"low|medium|high\", \"reasoning\": \"...\"}";
    }
}
