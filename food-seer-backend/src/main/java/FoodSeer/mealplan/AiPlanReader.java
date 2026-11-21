package FoodSeer.mealplan;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import FoodSeer.entity.Food;

/**
 * Reads a plan request with the LLM, then checks the answer against what the
 * customer actually typed. Small models invent things (a "no chocolate" nobody
 * asked for), so:
 *
 * <ul>
 * <li>every avoid, prefer and pinned dish word must appear in the request text;</li>
 * <li>every pinned day must appear in the request text;</li>
 * <li>skipped meals must be named in the request text;</li>
 * <li>the result is merged with the keyword reader so it can only add rules,
 * never drop one the keywords found.</li>
 * </ul>
 */
@Component
public class AiPlanReader {

    static final String PROMPT = String.join("\n",
            "You read a restaurant customer's request for a weekly meal plan and turn it into JSON.",
            "Reply with only one JSON object with exactly these keys:",
            "  skipMeals: meals they do not want planned, from \"BREAKFAST\", \"LUNCH\", \"DINNER\", or []",
            "  avoid: foods or ingredients they do not want, using their words, or []",
            "  prefer: foods or ingredients they want more of, using their words, or []",
            "  pins: dishes they want on a specific weekday, as [{\"day\": \"FRIDAY\", \"dish\": \"pizza\", "
                    + "\"meal\": \"DINNER\" or null}], or []",
            "  maxRepeats: the most times one dish may appear in the week if they say so, else null",
            "  weeklyBudget: a spending limit in whole dollars if they give one, else null",
            "  goals: diet goals the request implies, from this list only, or []:",
            "    HIGH_PROTEIN (wants protein, bulking, building muscle),",
            "    VEGETARIAN (no meat or fish), VEGAN (no animal products, plant-based),",
            "    LOW_CARB (low carb, keto), NO_DESSERT (no desserts or sweets at all),",
            "    FEWER_DESSERTS (fewer sweets, eating healthy or clean),",
            "    LIGHTER (wants to eat less or lose weight)",
            "Only include what the request says. Do not guess or add anything.");

    private final LlmClient llm;
    private final ObjectMapper json = new ObjectMapper();

    public AiPlanReader(final LlmClient llm) {
        this.llm = llm;
    }

    /**
     * @param request
     *            the customer's text
     * @param menu
     *            dishes, for the keyword reader
     * @return rules from the model merged with the keyword reading, or empty
     *         if the model failed or gave nothing usable
     */
    public Optional<PlanRules> read(final String request, final List<Food> menu) {
        if (request == null || request.isBlank()) {
            return Optional.empty();
        }
        final JsonNode n;
        try {
            final Optional<String> raw = llm.completeJson(PROMPT, request.trim());
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            n = json.readTree(raw.get());
        } catch (final Exception e) {
            return Optional.empty();
        }
        if (n == null || !n.isObject()) {
            return Optional.empty();
        }
        final PlanRules merged = validate(n, request).mergeSafely(KeywordPlanReader.read(request, menu));
        // A dish pinned to a day is already asked for; it is not also "more of".
        final List<String> prefer = merged.prefer().stream()
                .filter(w -> merged.pins().stream().noneMatch(p -> p.dish().contains(KeywordPlanReader.singular(w))))
                .toList();
        return Optional.of(new PlanRules(merged.skipMeals(), merged.avoid(), prefer, merged.pins(),
                merged.maxRepeats(), merged.weeklyBudget(), merged.goals()));
    }

    /**
     * @param n
     *            the model's JSON
     * @param request
     *            what the customer typed
     * @return only the parts of the reply grounded in the request
     */
    static PlanRules validate(final JsonNode n, final String request) {
        final String text = " " + request.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9$]+", " ") + " ";
        final Set<String> skip = new LinkedHashSet<>();
        for (final JsonNode m : n.path("skipMeals")) {
            if (m.isTextual() && text.contains(" " + m.asText().toLowerCase(Locale.ROOT).replaceAll("e?s$", ""))) {
                skip.add(m.asText());
            }
        }
        final List<String> avoid = grounded(n.path("avoid"), text);
        final List<PlanRules.Pin> pins = new ArrayList<>();
        for (final JsonNode p : n.path("pins")) {
            final DayOfWeek day = day(p.path("day").asText(""));
            final String dish = p.path("dish").asText("").trim().toLowerCase(Locale.ROOT);
            // A meal or a day is not a dish ("lunch on Wednesday").
            final boolean notADish = dish.matches("(breakfast|lunch|dinner|meal|food)s?")
                    || KeywordPlanReader.DAYS.containsKey(dish);
            if (day != null && dayInText(day, text) && !dish.isEmpty() && !notADish && inText(dish, text)) {
                // A meal is kept only when the request names it.
                final String meal = p.path("meal").isTextual() && text.contains(" "
                        + p.path("meal").asText().toLowerCase(Locale.ROOT)) ? p.path("meal").asText() : null;
                pins.add(new PlanRules.Pin(day, KeywordPlanReader.singular(dish), meal));
            }
        }
        final List<String> prefer = new ArrayList<>();
        for (final String word : grounded(n.path("prefer"), text)) {
            final String w = KeywordPlanReader.singular(word);
            // A pinned dish is already asked for; "X-free" means avoid X, never more of it.
            final boolean pinned = pins.stream().anyMatch(p -> p.dish().equals(w) || p.dish().contains(w));
            final boolean free = word.endsWith("-free") || text.contains(" " + w + " free ");
            if (!pinned && !free) {
                prefer.add(word);
            }
        }
        // Goals come from a closed list, so the model may map casual words onto them.
        final Set<String> goals = new LinkedHashSet<>();
        for (final JsonNode g : n.path("goals")) {
            if (g.isTextual()) {
                goals.add(g.asText());
            }
        }
        avoid.removeIf(KeywordPlanReader::isGoalWord);
        prefer.removeIf(KeywordPlanReader::isGoalWord);
        final int repeats = n.path("maxRepeats").canConvertToInt() && n.path("maxRepeats").isNumber()
                ? n.path("maxRepeats").asInt() : PlanRules.DEFAULT_MAX_REPEATS;
        Integer budget = null;
        if (n.path("weeklyBudget").isNumber()) {
            final int value = n.path("weeklyBudget").asInt();
            if (text.contains(String.valueOf(value))) {
                budget = value;
            }
        }
        return new PlanRules(skip, avoid, prefer, pins, repeats, budget, goals);
    }

    private static List<String> grounded(final JsonNode array, final String text) {
        final List<String> out = new ArrayList<>();
        for (final JsonNode a : array) {
            if (a.isTextual() && inText(a.asText(), text)) {
                out.add(a.asText().trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** True if every word of {@code phrase} (singular or plural) appears in the text. */
    static boolean inText(final String phrase, final String text) {
        final String[] words = phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]+", " ").trim().split(" +");
        if (words.length == 0 || words[0].isEmpty()) {
            return false;
        }
        for (final String w : words) {
            final String s = KeywordPlanReader.singular(w);
            if (!text.contains(" " + w + " ") && !text.contains(" " + s + " ") && !text.contains(" " + s + "s ")
                    && !text.contains(" " + s + "es ")) {
                return false;
            }
        }
        return true;
    }

    private static boolean dayInText(final DayOfWeek day, final String text) {
        for (final var e : KeywordPlanReader.DAYS.entrySet()) {
            if (e.getValue() == day && (text.contains(" " + e.getKey() + " ") || text.contains(" " + e.getKey() + "s "))) {
                return true;
            }
        }
        return false;
    }

    private static DayOfWeek day(final String raw) {
        final DayOfWeek d = KeywordPlanReader.DAYS.get(raw.trim().toLowerCase(Locale.ROOT));
        if (d != null) {
            return d;
        }
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /** @return the model name */
    public String model() {
        return llm.model();
    }
}
