package dk.ek.gruppe_i.llm_project.housing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests of the housing rules: no Spring, no Ollama. */
class HousingLogicTest {

    static BaselineParameters params() {
        Properties p = new Properties();
        p.setProperty("m2PerPerson", "40");
        p.setProperty("personsPerFlat", "1.8");
        p.setProperty("legalM2PerPerson", "20");
        p.setProperty("floorEfficiency", "0.8");
        p.setProperty("mPerLevel", "3");
        p.setProperty("medianFootprintM2", "300");
        p.setProperty("overallLevels", "5");
        p.setProperty("smallFootprintM2", "60");
        p.setProperty("largeFootprintM2", "3000");
        p.setProperty("footprintBins", "0,100,250,500,1000");
        p.setProperty("levelsByClass", "3,4,5,6,8");
        return BaselineParameters.fromProperties(p);
    }

    static HousingBuilding b(Double footprint, Integer levels, Integer flats, Double height) {
        return new HousingBuilding("t", footprint, levels, flats, height);
    }

    static HousingSituation.Situation s(HousingBuilding b) {
        return HousingSituation.of(b, 3);
    }

    // ---- situation

    @Test
    void tiersFollowWhatIsKnown() {
        assertEquals("A", s(b(null, null, 10, null)).tier());
        assertEquals("B", s(b(630.0, 5, null, null)).tier());
        assertEquals("C", s(b(null, 5, null, null)).tier());
        assertEquals("D", s(b(630.0, null, null, null)).tier());
        assertEquals("E", s(b(null, null, null, null)).tier());
    }

    @Test
    void phraseUsesTheWordsOfTheRuleHeadings() {
        assertEquals("flats known", s(b(null, null, 10, null)).phrase());
        assertEquals("levels known, flats unknown, footprint known", s(b(630.0, 5, null, null)).phrase());
        assertEquals("levels unknown, flats unknown, footprint unknown", s(b(null, null, null, null)).phrase());
        assertEquals("apartment building, levels unknown, flats unknown, footprint known",
                HousingSituation.retrievalQuery(s(b(100.0, null, null, null))));
    }

    @Test
    void heightReplacesLevelsAndRoundsHalfToEven() {
        assertEquals(5, s(b(630.0, null, null, 15.0)).effectiveLevels());
        assertEquals("B", s(b(630.0, null, null, 15.0)).tier());
        assertEquals(2, s(b(630.0, null, null, 7.5)).effectiveLevels()); // 2.5 -> 2 like Python's round
        assertNull(s(b(630.0, null, null, 1.0)).effectiveLevels());      // 0.33 -> 0 floors is not a level
        assertEquals(7, s(b(630.0, 7, null, 30.0)).effectiveLevels());   // levels win over height
    }

    @Test
    void contextTextMatchesTheNotebookFormat() {
        HousingBuilding building = b(629.6, 5, null, null);
        assertEquals("buildingType: apartment building\nfootprintM2: 630\nnumberOfLevels: 5\nnumberOfFlats: unknown\nheightMeters: unknown",
                HousingSituation.contextText(building, s(building)));
        HousingBuilding tall = b(null, null, 12, 15.5);
        assertEquals("buildingType: apartment building\nfootprintM2: unknown\nnumberOfLevels: 5\nnumberOfFlats: 12\nheightMeters: 15.5",
                HousingSituation.contextText(tall, s(tall)));
        assertEquals("15", HousingSituation.general(15.0));
    }

    // ---- validation

    @Test
    void implausibleValuesAreReported() {
        assertTrue(b(630.0, 5, null, null).problems().isEmpty());
        assertEquals(1, b(5.0, 5, null, null).problems().size());
        assertEquals(4, b(Double.NaN, 0, 0, 0.0).problems().size());
    }

    // ---- baseline

    @Test
    void flatsKnownGivesFlatsTimesPersonsPerFlat() {
        HousingBuilding building = b(null, null, 30, null);
        HousingBaseline.Estimate e = HousingBaseline.estimate(building, s(building), params());
        assertEquals(54, e.capacity());
        assertEquals("high", e.confidence());
    }

    @Test
    void areaFormulaWithLevelsKnown() {
        HousingBuilding building = b(630.0, 5, null, null);
        HousingBaseline.Estimate e = HousingBaseline.estimate(building, s(building), params());
        assertEquals(63, e.capacity()); // 630 * 5 * 0.8 / 40
        assertEquals("medium", e.confidence());
        assertTrue(e.basis().contains("2520 m2 usable"), e.basis());
    }

    @Test
    void missingValuesAreImputed() {
        HousingBuilding d = b(300.0, null, null, null);       // 250-500 class -> 5 levels
        HousingBaseline.Estimate ed = HousingBaseline.estimate(d, s(d), params());
        assertEquals(30, ed.capacity());                       // 300*5*0.8/40
        assertEquals("low", ed.confidence());
        assertTrue(ed.basis().contains("levels missing"));
        HousingBuilding c = b(null, 4, null, null);           // median footprint 300
        assertEquals(24, HousingBaseline.estimate(c, s(c), params()).capacity()); // 300*4*0.8/40
        HousingBuilding e = b(null, null, null, null);        // 300 * 5 levels
        assertEquals(30, HousingBaseline.estimate(e, s(e), params()).capacity());
    }

    @Test
    void sizeFlagsLowerConfidence() {
        HousingBuilding small = b(40.0, 2, null, null);
        assertEquals("small footprint", HousingBaseline.flag(small, params()));
        assertEquals("low", HousingBaseline.estimate(small, s(small), params()).confidence());
        assertEquals("large footprint", HousingBaseline.flag(b(3500.0, 3, null, null), params()));
        assertEquals("", HousingBaseline.flag(b(60.0, 3, null, null), params()));
    }

    @Test
    void ceilingIsUsableAreaOverLegalM2() {
        HousingBuilding building = b(630.0, 5, null, null);
        assertEquals(126, HousingBaseline.ceiling(building, s(building), params())); // 2520 / 20
        assertNull(HousingBaseline.ceiling(b(630.0, null, null, null), s(b(630.0, null, null, null)), params()));
        assertNull(HousingBaseline.ceiling(b(null, 5, null, null), s(b(null, 5, null, null)), params()));
    }

    @Test
    void levelsByFootprintClass() {
        BaselineParameters p = params();
        assertEquals(3.0, p.levelsFor(50.0));
        assertEquals(4.0, p.levelsFor(100.0));
        assertEquals(5.0, p.levelsFor(499.9));
        assertEquals(8.0, p.levelsFor(5000.0));
        assertEquals(5.0, p.levelsFor(null));
    }

    @Test
    void parametersMustBeComplete() {
        Properties p = new Properties();
        assertThrows(IllegalArgumentException.class, () -> BaselineParameters.fromProperties(p));
    }

    // ---- answer parser

    @Test
    void parsesPlainJson() {
        CapacityAnswerParser.Answer a = CapacityAnswerParser.parse(
                "{\"capacity\": 54, \"confidence\": \"Medium\", \"reasoning\": \"30 flats x 1.8\"}");
        assertEquals(54, a.capacity());
        assertEquals("medium", a.confidence());
        assertEquals("30 flats x 1.8", a.reasoning());
    }

    @Test
    void stripsThinkingFencesAndSurroundingText() {
        String raw = "<think>maybe {\"capacity\": 1} hmm</think>\nHere you go:\n```json\n"
                + "{\"capacity\": \"63\", \"confidence\": \"high\", \"reasoning\": \"a } brace in text, \\\"quoted\\\"\"}\n```\nDone.";
        CapacityAnswerParser.Answer a = CapacityAnswerParser.parse(raw);
        assertEquals(63, a.capacity());
        assertEquals("a } brace in text, \"quoted\"", a.reasoning());
    }

    @Test
    void unclosedThinkingBlockIsAnError() {
        assertThrows(CapacityAnswerParser.AnswerException.class,
                () -> CapacityAnswerParser.parse("<think>still thinking {\"capacity\": 5}"));
    }

    @Test
    void roundsDecimalCapacityAndDefaultsConfidence() {
        CapacityAnswerParser.Answer a = CapacityAnswerParser.parse("{\"capacity\": 53.6, \"confidence\": \"certain\"}");
        assertEquals(54, a.capacity());
        assertEquals("unknown", a.confidence());
        assertEquals("", a.reasoning());
    }

    @Test
    void rejectsBadAnswers() {
        for (String bad : new String[]{null, "", "no json here", "{\"capacity\": 0}", "{\"capacity\": -3}",
                "{\"capacity\": \"many\"}", "{\"confidence\": \"low\"}", "{\"capacity\": 5", "{\"capacity\": 5, }",
                "{\"capacity\": 2000000}", "{\"capacity\": [1]}"}) {
            assertThrows(CapacityAnswerParser.AnswerException.class, () -> CapacityAnswerParser.parse(bad), String.valueOf(bad));
        }
    }

    // ---- estimator

    @Test
    void estimatorUsesModelAnswerAndReportsBaseline() {
        List<String> queries = new ArrayList<>();
        HousingCapacityEstimator est = new HousingCapacityEstimator(
                (sys, user) -> "{\"capacity\": 60, \"confidence\": \"medium\", \"reasoning\": \"ok\"}",
                q -> { queries.add(q); return List.of("## Rule B: levels known"); }, params());
        HousingCapacityEstimator.Result r = est.estimate(new HousingBuilding("x", 630.0, 5, null, null), true);
        assertNull(r.error());
        assertEquals("B", r.tier());
        assertEquals(60, r.capacity());
        assertEquals(63, r.baselineCapacity());
        assertTrue(r.ragUsed());
        assertEquals(List.of("apartment building, levels known, flats unknown, footprint known"), queries);
    }

    @Test
    void estimatorCapsAtCeilingAndLowersConfidence() {
        HousingCapacityEstimator est = new HousingCapacityEstimator(
                (sys, user) -> "{\"capacity\": 500, \"confidence\": \"high\", \"reasoning\": \"big\"}", q -> List.of(), params());
        HousingCapacityEstimator.Result r = est.estimate(new HousingBuilding("x", 630.0, 5, null, null), false);
        assertEquals(126, r.capacity());
        assertEquals(500, r.modelCapacity());
        assertTrue(r.cappedByCeiling());
        assertEquals("low", r.confidence());
        assertFalse(r.ragUsed());
        assertTrue(r.reasoning().contains("Limited to 126"));
    }

    @Test
    void estimatorRetriesOnceWhenTheAnswerIsUnreadable() {
        int[] calls = {0};
        List<String> users = new ArrayList<>();
        HousingCapacityEstimator est = new HousingCapacityEstimator((sys, user) -> {
            users.add(user);
            return calls[0]++ == 0 ? "about sixty" : "{\"capacity\": 60, \"confidence\": \"low\", \"reasoning\": \"r\"}";
        }, q -> List.of(), params());
        HousingCapacityEstimator.Result r = est.estimate(new HousingBuilding("x", 630.0, 5, null, null), false);
        assertEquals(60, r.capacity());
        assertEquals(2, calls[0]);
        assertTrue(users.get(1).contains("could not be read"));
    }

    @Test
    void estimatorReportsErrorsPerBuildingWithoutThrowing() {
        HousingCapacityEstimator unreadable = new HousingCapacityEstimator((s, u) -> "nope", q -> List.of(), params());
        HousingCapacityEstimator.Result r1 = unreadable.estimate(new HousingBuilding("x", 630.0, 5, null, null), false);
        assertNull(r1.capacity());
        assertTrue(r1.error().startsWith("model answer could not be read"));
        assertEquals(63, r1.baselineCapacity());

        HousingCapacityEstimator down = new HousingCapacityEstimator((s, u) -> { throw new IllegalStateException("connection refused"); },
                q -> List.of(), params());
        HousingCapacityEstimator.Result r2 = down.estimate(new HousingBuilding("x", 630.0, 5, null, null), false);
        assertTrue(r2.error().contains("connection refused"));

        HousingCapacityEstimator.Result r3 = down.estimate(new HousingBuilding("x", 5.0, 5, null, null), false);
        assertTrue(r3.error().startsWith("invalid building"));
        assertNull(r3.baselineCapacity());
    }

    @Test
    void failedRetrievalFallsBackToNoBackground() {
        List<String> users = new ArrayList<>();
        HousingCapacityEstimator est = new HousingCapacityEstimator(
                (s, u) -> { users.add(u); return "{\"capacity\": 60}"; },
                q -> { throw new IllegalStateException("ollama down"); }, params());
        HousingCapacityEstimator.Result r = est.estimate(new HousingBuilding("x", 630.0, 5, null, null), true);
        assertEquals(60, r.capacity());
        assertFalse(r.ragUsed());
        assertFalse(users.get(0).contains("Background material"));
    }

    @Test
    void backgroundIsPlacedBeforeTheTask() {
        List<String> users = new ArrayList<>();
        HousingCapacityEstimator est = new HousingCapacityEstimator(
                (s, u) -> { users.add(u); return "{\"capacity\": 60}"; }, q -> List.of("## Rule B", "## Rule D"), params());
        est.estimate(new HousingBuilding("x", 630.0, 5, null, null), true);
        String user = users.get(0);
        assertTrue(user.startsWith("Background material:\n## Rule B\n\n---\n\n## Rule D"));
        assertTrue(user.contains("footprintM2: 630"));
    }
}
