package FoodSeer.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import FoodSeer.entity.Food;
import FoodSeer.mealplan.AiPlanReader;
import FoodSeer.mealplan.KeywordPlanReader;
import FoodSeer.mealplan.OllamaLlmClient;
import FoodSeer.mealplan.PlanRules;

/**
 * Scores how well plan requests are read, on a fixed hand-labeled set (see
 * docs/p3/M0_MEAL_PLANNER.md, "Plan requests"). The keyword reader is scored on
 * every run. The LLM reader (keyword + LLM, merged) is scored only live:
 *
 * <pre>
 * ollama pull qwen2.5:1.5b
 * mvn -Dtest=PlanRequestEval -Dfoodseer.llm.live=true test
 * </pre>
 *
 * A request is read correctly when every rule matches the label exactly.
 */
class PlanRequestEval {

    record Label(String text, Set<String> skip, Set<String> avoid, Set<String> prefer, Set<String> pins,
            int repeats, Integer budget) {
    }

    private static Label l(final String text, final String skip, final String avoid, final String prefer,
            final String pins, final int repeats, final Integer budget) {
        return new Label(text, set(skip), set(avoid), set(prefer), set(pins), repeats, budget);
    }

    private static Set<String> set(final String csv) {
        final Set<String> out = new LinkedHashSet<>();
        if (csv != null && !csv.isBlank()) {
            for (final String s : csv.split(",")) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /** Pins are written "FRIDAY:pizza" or "SATURDAY:steak:DINNER". Dishes and avoids are singular. */
    static final List<Label> LABELS = List.of(
            l("skip breakfast", "BREAKFAST", "", "", "", 2, null),
            l("pizza on Friday", "", "", "", "FRIDAY:pizza", 2, null),
            l("no salads please", "", "salad", "", "", 2, null),
            l("under $150 this week", "", "", "", "", 2, 150),
            l("don't repeat dishes", "", "", "", "", 1, null),
            l("Skip breakfast, pizza on Friday, no salads, under $150", "BREAKFAST", "salad", "", "FRIDAY:pizza", 2,
                    150),
            l("I only eat lunch and dinner", "BREAKFAST", "", "", "", 2, null),
            l("more sushi", "", "", "sushi", "", 2, null),
            l("steak for saturday dinner", "", "", "", "SATURDAY:steak:DINNER", 2, null),
            l("burritos on tuesdays and pasta on thursday", "", "", "", "TUESDAY:burrito,THURSDAY:pasta", 2, null),
            l("no soup, no wraps", "", "soup,wrap", "", "", 2, null),
            l("gluten-free week", "", "gluten", "", "", 2, null),
            l("budget of 200", "", "", "", "", 2, 200),
            l("at most 3 times each", "", "", "", "", 3, null),
            l("no lunch on any day, salmon on monday", "LUNCH", "", "", "MONDAY:salmon", 2, null),
            l("I hate burgers", "", "burger", "", "", 2, null),
            l("lots of soup and no pizza", "", "pizza", "soup", "", 2, null),
            l("sushi on wednesday lunch", "", "", "", "WEDNESDAY:sushi:LUNCH", 2, null),
            l("all different meals, spend no more than $180", "", "", "", "", 1, 180),
            l("without breakfast and without dinner", "BREAKFAST,DINNER", "", "", "", 2, null),
            l("Friday: pizza. Saturday: steak.", "", "", "", "FRIDAY:pizza,SATURDAY:steak", 2, null),
            l("avoid fish", "", "fish", "", "", 2, null),
            l("dairy-free and no pasta", "", "dairy,pasta", "", "", 2, null),
            l("make it healthy", "", "", "", "", 2, null),
            l("I love pasta, give me pasta on sunday", "", "", "pasta", "SUNDAY:pasta", 2, null),
            l("skip lunch, max $120", "LUNCH", "", "", "", 2, 120),
            l("no repeats and no breakfast", "BREAKFAST", "", "", "", 1, null),
            l("tofu bowl on thursday dinner, no steak", "", "steak", "", "THURSDAY:tofu:DINNER", 2, null),
            l("prefer salmon, avoid lobster", "", "lobster", "salmon", "", 2, null),
            l("pizza friday night", "", "", "", "FRIDAY:pizza", 2, null));

    /**
     * Held out: written after both readers were fixed against {@link #LABELS},
     * scored once, and not used to change either reader afterwards.
     */
    static final List<Label> HELD_OUT = List.of(
            l("I'm not a breakfast person", "BREAKFAST", "", "", "", 2, null),
            l("Fridays are pizza night", "", "", "", "FRIDAY:pizza", 2, null),
            l("keep it under a hundred and fifty bucks", "", "", "", "", 2, 150),
            l("nothing with salad in it", "", "salad", "", "", 2, null),
            l("I'd like the burrito on Wednesday", "", "", "", "WEDNESDAY:burrito", 2, null),
            l("please no seafood", "", "seafood", "", "", 2, null),
            l("cap spending at $175", "", "", "", "", 2, 175),
            l("the same dish once a week at most", "", "", "", "", 1, null),
            l("lunch is covered at work, so leave it out", "LUNCH", "", "", "", 2, null),
            l("salmon monday, steak thursday", "", "", "", "MONDAY:salmon,THURSDAY:steak", 2, null));

    record Score(int exact, int fields, int fieldsTotal, int invented, List<String> misreads) {
    }

    static Score score(final List<PlanRules> readings) {
        return score(LABELS, readings);
    }

    static Score score(final List<Label> labels, final List<PlanRules> readings) {
        int exact = 0;
        int fields = 0;
        int invented = 0;
        final List<String> misreads = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            final Label want = labels.get(i);
            final PlanRules got = readings.get(i);
            final boolean[] ok = {
                want.skip().equals(got.skipMeals()),
                want.avoid().equals(singular(got.avoid())),
                want.prefer().equals(singular(got.prefer())),
                want.pins().equals(pins(got)),
                want.repeats() == got.maxRepeats(),
                java.util.Objects.equals(want.budget(), got.weeklyBudget())
            };
            int good = 0;
            for (final boolean b : ok) {
                good += b ? 1 : 0;
            }
            fields += good;
            exact += good == ok.length ? 1 : 0;
            if (good != ok.length) {
                misreads.add("\"" + want.text() + "\" was read as: " + (got.isEmpty() ? "nothing" : got.describe()));
            }
            for (final String a : singular(got.avoid())) {
                invented += want.avoid().contains(a) ? 0 : 1;
            }
            for (final String p : pins(got)) {
                invented += want.pins().contains(p) ? 0 : 1;
            }
        }
        return new Score(exact, fields, labels.size() * 6, invented, misreads);
    }

    private static Set<String> singular(final List<String> words) {
        final Set<String> out = new LinkedHashSet<>();
        for (final String w : words) {
            out.add(KeywordPlanReader.singular(w.toLowerCase(Locale.ROOT).trim()));
        }
        return out;
    }

    private static Set<String> pins(final PlanRules r) {
        final Set<String> out = new LinkedHashSet<>();
        for (final PlanRules.Pin p : r.pins()) {
            final String dish = p.dish().split(" ")[0];
            out.add(p.day() + ":" + dish + (p.meal() == null ? "" : ":" + p.meal()));
        }
        return out;
    }

    @Test
    void keywordReader() throws IOException {
        final List<Food> menu = M0MealPlannerEval.buildMenu(new java.util.Random(1));
        final List<PlanRules> readings = new ArrayList<>();
        for (final Label label : LABELS) {
            readings.add(KeywordPlanReader.read(label.text(), menu));
        }
        final Score s = score(readings);
        String report = header() + row("Keyword reader", s);
        if ("true".equals(System.getProperty("foodseer.llm.live"))) {
            final String model = System.getProperty("foodseer.llm.model", "qwen2.5:1.5b");
            final AiPlanReader ai = new AiPlanReader(new OllamaLlmClient(new RestTemplateBuilder(), true,
                    System.getProperty("foodseer.llm.url", "http://localhost:11434/api/chat"), model, 60));
            final List<PlanRules> aiReadings = new ArrayList<>();
            int failed = 0;
            for (final Label label : LABELS) {
                final Optional<PlanRules> r = ai.read(label.text(), menu);
                failed += r.isEmpty() ? 1 : 0;
                aiReadings.add(r.orElseGet(() -> KeywordPlanReader.read(label.text(), menu)));
            }
            report += row("Keyword + LLM (" + model + ")", score(aiReadings));
            report += String.format(Locale.ROOT, "%nThe LLM gave no usable answer for %d of %d requests (keywords used).%n",
                    failed, LABELS.size());
            // Held-out requests: scored once, never used to change either reader.
            final List<PlanRules> kw = new ArrayList<>();
            final List<PlanRules> both = new ArrayList<>();
            for (final Label label : HELD_OUT) {
                kw.add(KeywordPlanReader.read(label.text(), menu));
                both.add(ai.read(label.text(), menu).orElseGet(() -> KeywordPlanReader.read(label.text(), menu)));
            }
            report += String.format(Locale.ROOT, "%n## Held-out requests (%d, written after tuning)%n%n"
                    + "| Reader | Requests read exactly | Rules correct | Avoids or pins not asked for |%n"
                    + "| --- | ---: | ---: | ---: |%n", HELD_OUT.size());
            report += heldRow("Keyword reader", score(HELD_OUT, kw));
            report += heldRow("Keyword + LLM (" + model + ")", score(HELD_OUT, both));
        } else {
            report += "\nLLM not measured on this run (add -Dfoodseer.llm.live=true with Ollama running).\n";
        }
        report += MISREADS;
        final Path out = Path.of("target", "m0-eval", "plan-requests-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);
        // Guards, not a claim: the keyword reader must not regress below what it scored when written.
        assertTrue(s.exact() >= 28, "keyword reader now reads only " + s.exact() + " requests exactly");
        assertTrue(s.invented() == 0, "keyword reader invented rules");
    }

    private static String header() {
        return String.format(Locale.ROOT, "# Plan request reading%n%n%d hand-labeled requests.%n%n"
                + "| Reader | Requests read exactly | Rules correct | Avoids or pins not asked for |%n"
                + "| --- | ---: | ---: | ---: |%n", LABELS.size());
    }

    private static String row(final String name, final Score s) {
        final StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "| %s | %d / %d | %.0f%% | %d |%n", name,
                s.exact(), LABELS.size(), 100.0 * s.fields() / s.fieldsTotal(), s.invented()));
        MISREADS.append("\n**").append(name).append(" misreads:**\n\n");
        if (s.misreads().isEmpty()) {
            MISREADS.append("- none\n");
        }
        s.misreads().forEach(m -> MISREADS.append("- ").append(m).append('\n'));
        return b.toString();
    }

    private static final StringBuilder MISREADS = new StringBuilder();

    private static String heldRow(final String name, final Score s) {
        MISREADS.append("\n**Held out, ").append(name).append(" misreads:**\n\n");
        if (s.misreads().isEmpty()) {
            MISREADS.append("- none\n");
        }
        s.misreads().forEach(m -> MISREADS.append("- ").append(m).append('\n'));
        return String.format(Locale.ROOT, "| %s | %d / %d | %.0f%% | %d |%n", name, s.exact(), HELD_OUT.size(),
                100.0 * s.fields() / s.fieldsTotal(), s.invented());
    }
}
