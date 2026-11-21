package FoodSeer.mealplan;

import static FoodSeer.mealplan.PlanFixtures.menu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/** The keyword reader, and what is accepted from the LLM. */
class PlanReadersTest {

    /** Returns canned replies and records prompts. */
    static final class FakeLlm implements LlmClient {
        final List<String> replies = new ArrayList<>();
        final List<String> prompts = new ArrayList<>();

        FakeLlm(final String... replies) {
            this.replies.addAll(List.of(replies));
        }

        @Override
        public Optional<String> completeJson(final String system, final String user) {
            prompts.add(user);
            return replies.isEmpty() ? Optional.empty() : Optional.ofNullable(replies.remove(0));
        }

        @Override
        public String model() {
            return "fake";
        }
    }

    private static PlanRules read(final String text) {
        return KeywordPlanReader.read(text, menu());
    }

    @Test
    void keywordsReadSkipsPinsAvoidsBudgetAndRepeats() {
        final PlanRules r = read("Skip breakfast, pizza on Friday, no salads, under $150 and don't repeat dishes");
        assertEquals(Set.of("BREAKFAST"), r.skipMeals());
        assertEquals(List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pizza", null)), r.pins());
        assertEquals(List.of("salads"), r.avoid());
        assertEquals(150, r.weeklyBudget());
        assertEquals(1, r.maxRepeats());
    }

    @Test
    void keywordsReadOnlyMealsPreferAndPinnedMeal() {
        final PlanRules r = read("only lunch and dinner. more sushi please. steak for saturday dinner");
        assertEquals(Set.of("BREAKFAST"), r.skipMeals());
        assertEquals(List.of("sushi"), r.prefer());
        assertEquals(List.of(new PlanRules.Pin(DayOfWeek.SATURDAY, "steak", "DINNER")), r.pins());
    }

    @Test
    void keywordsReadFreeSuffixesDollarAmountsAndPluralDays() {
        final PlanRules r = read("gluten-free week, $200 total, burritos on tuesdays, at most 3 times each");
        assertTrue(r.avoid().contains("gluten"));
        assertEquals(200, r.weeklyBudget());
        assertEquals(List.of(new PlanRules.Pin(DayOfWeek.TUESDAY, "burrito", null)), r.pins());
        assertEquals(3, r.maxRepeats());
    }

    @Test
    void anAvoidedDishIsNeverPinned() {
        final PlanRules r = read("no pizza on friday");
        assertTrue(r.pins().isEmpty());
        assertEquals(List.of("pizza"), r.avoid());
    }

    @Test
    void nothingAskedIsNothingRead() {
        assertTrue(read(null).isEmpty());
        assertTrue(read("   ").isEmpty());
        assertTrue(read("make it good").isEmpty());
        assertTrue(KeywordPlanReader.read("pizza on friday", null).pins().isEmpty());
    }

    @Test
    void aiAnswerIsKeptOnlyWhereTheRequestSaysSo() {
        final FakeLlm llm = new FakeLlm("{\"skipMeals\":[\"BREAKFAST\",\"DINNER\"],\"avoid\":[\"salad\",\"chocolate\"],"
                + "\"prefer\":[\"sushi\"],\"pins\":[{\"day\":\"FRIDAY\",\"dish\":\"pizza\",\"meal\":\"DINNER\"},"
                + "{\"day\":\"MONDAY\",\"dish\":\"steak\"}],\"maxRepeats\":1,\"weeklyBudget\":999}");
        final PlanRules r = new AiPlanReader(llm).read("I don't eat breakfast. Friday pizza night! Hate salads, love sushi",
                menu()).orElseThrow();
        assertEquals(Set.of("BREAKFAST"), r.skipMeals()); // "dinner" is not in the request
        assertTrue(r.avoid().contains("salad"));
        assertTrue(!r.avoid().contains("chocolate")); // invented
        assertEquals(List.of("sushi"), r.prefer());
        // No Monday in the text; "night" does not name a meal, so the model's DINNER is dropped.
        assertEquals(List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pizza", null)), r.pins());
        assertEquals(1, r.maxRepeats());
        assertNull(r.weeklyBudget()); // 999 is not in the text
        assertEquals("I don't eat breakfast. Friday pizza night! Hate salads, love sushi", llm.prompts.get(0));
    }

    @Test
    void aiResultIsMergedWithKeywordsSoNothingIsLost() {
        final PlanRules r = new AiPlanReader(new FakeLlm("{\"avoid\":[]}"))
                .read("skip lunch, no soup, under $120", menu()).orElseThrow();
        assertEquals(Set.of("LUNCH"), r.skipMeals());
        assertEquals(List.of("soup"), r.avoid());
        assertEquals(120, r.weeklyBudget());
    }

    @Test
    void aiFailsClosed() {
        assertTrue(new AiPlanReader(new FakeLlm()).read("pizza friday", menu()).isEmpty());
        assertTrue(new AiPlanReader(new FakeLlm("not json")).read("pizza friday", menu()).isEmpty());
        assertTrue(new AiPlanReader(new FakeLlm("[1]")).read("pizza friday", menu()).isEmpty());
        assertTrue(new AiPlanReader(new FakeLlm("{}")).read(" ", menu()).isEmpty());
        assertTrue(new AiPlanReader(new FakeLlm("{}")).read(null, menu()).isEmpty());
        assertEquals("fake", new AiPlanReader(new FakeLlm()).model());
    }

    @Test
    void validateUnderstandsDayNamesAndAbbreviations() throws Exception {
        final PlanRules r = AiPlanReader.validate(new ObjectMapper().readTree(
                "{\"pins\":[{\"day\":\"fri\",\"dish\":\"Pizzas\"},{\"day\":\"Someday\",\"dish\":\"pasta\"}]}"),
                "pizzas on fri and pasta sometime");
        assertEquals(List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pizza", null)), r.pins());
        assertTrue(AiPlanReader.inText("tree nuts", " no tree nuts please "));
        assertTrue(!AiPlanReader.inText("", " anything "));
    }

    @Test
    void keywordsReadDietGoals() {
        assertEquals(Set.of(PlanRules.HIGH_PROTEIN), read("high protein. no breakfast.").goals());
        assertTrue(read("high protein. no breakfast.").prefer().isEmpty()); // a goal, not a dish
        assertEquals(Set.of(PlanRules.VEGAN), read("plant-based week").goals());
        assertEquals(Set.of(PlanRules.VEGETARIAN), read("vegetarian please").goals());
        assertEquals(Set.of(PlanRules.LOW_CARB), read("I'm doing keto").goals());
        assertEquals(Set.of(PlanRules.NO_DESSERT), read("no desserts").goals());
        assertTrue(read("no desserts").avoid().isEmpty());
        assertEquals(Set.of(PlanRules.FEWER_DESSERTS), read("trying to eat clean").goals());
        assertEquals(Set.of(PlanRules.LIGHTER), read("I want to lose weight").goals());
        assertEquals(Set.of(PlanRules.LOW_CARB), read("cutting carbs").goals());
        assertTrue(read("pizza on friday").goals().isEmpty());
    }

    @Test
    void aiMapsCasualWordsOntoGoalsFromTheClosedList() {
        final PlanRules r = new AiPlanReader(new FakeLlm(
                "{\"goals\":[\"HIGH_PROTEIN\",\"MAKE_IT_TASTY\"],\"prefer\":[\"protein\"]}"))
                .read("trying to get big this month", menu()).orElseThrow(); // no keyword for this
        assertEquals(Set.of(PlanRules.HIGH_PROTEIN), r.goals());
        assertTrue(r.prefer().isEmpty());
    }

    @Test
    void ollamaClientFailsClosed() {
        final OllamaLlmClient off = new OllamaLlmClient(new RestTemplateBuilder(), false,
                "http://127.0.0.1:9/api/chat", "m", 1);
        assertTrue(off.completeJson("s", "u").isEmpty());
        assertEquals("m", off.model());
        assertTrue(new OllamaLlmClient(new RestTemplateBuilder(), true, "http://127.0.0.1:9/api/chat", "m", 1)
                .completeJson("s", "u").isEmpty());
    }
}
