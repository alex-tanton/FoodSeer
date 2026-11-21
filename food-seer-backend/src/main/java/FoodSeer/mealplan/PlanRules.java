package FoodSeer.mealplan;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import FoodSeer.constant.MealType;

/**
 * What a customer asked the planner for, beyond their saved profile. Produced
 * from a typed request by the keyword reader or the LLM; both go through this
 * constructor, which drops anything outside the vocabulary.
 *
 * @param skipMeals
 *            meals not to plan: BREAKFAST, LUNCH and/or DINNER
 * @param avoid
 *            dishes or ingredients to leave out, such as "salad" or "dairy"
 * @param prefer
 *            dishes or ingredients to favor, such as "sushi"
 * @param pins
 *            a dish on a given day, such as pizza on Friday
 * @param maxRepeats
 *            times a main dish may appear in the week, 1 to 7
 * @param weeklyBudget
 *            a spending limit in dollars from the request, or null
 * @param goals
 *            diet goals from {@link #GOALS}, such as HIGH_PROTEIN
 */
public record PlanRules(Set<String> skipMeals, List<String> avoid, List<String> prefer, List<Pin> pins,
        int maxRepeats, Integer weeklyBudget, Set<String> goals) {

    /** Favor dishes built around meat, fish, eggs, tofu or yogurt. */
    public static final String HIGH_PROTEIN = "HIGH_PROTEIN";
    /** No meat or fish (a hard filter). */
    public static final String VEGETARIAN = "VEGETARIAN";
    /** No animal products (a hard filter). */
    public static final String VEGAN = "VEGAN";
    /** Fewer wheat- and sugar-heavy dishes. */
    public static final String LOW_CARB = "LOW_CARB";
    /** No desserts at all. */
    public static final String NO_DESSERT = "NO_DESSERT";
    /** Desserts only rarely. */
    public static final String FEWER_DESSERTS = "FEWER_DESSERTS";
    /** Aim 15% under the usual calorie target. */
    public static final String LIGHTER = "LIGHTER";

    /** Every goal the planner acts on, with how it is described to the customer. */
    public static final java.util.Map<String, String> GOALS = java.util.Map.of(
            HIGH_PROTEIN, "high protein (favoring meat, fish, eggs, tofu and yogurt; FoodSeer does not store protein grams)",
            VEGETARIAN, "vegetarian",
            VEGAN, "vegan",
            LOW_CARB, "low carb (fewer bread, pasta and sweets)",
            NO_DESSERT, "no desserts",
            FEWER_DESSERTS, "fewer desserts",
            LIGHTER, "lighter (15% under your usual target)");

    /** Rules without diet goals. */
    public PlanRules(final Set<String> skipMeals, final List<String> avoid, final List<String> prefer,
            final List<Pin> pins, final int maxRepeats, final Integer weeklyBudget) {
        this(skipMeals, avoid, prefer, pins, maxRepeats, weeklyBudget, Set.of());
    }

    /** Default times a main dish may appear in a week. */
    public static final int DEFAULT_MAX_REPEATS = 2;
    /** Most entries kept in any list. */
    static final int MAX_ITEMS = 8;

    /** No requests. */
    public static final PlanRules NONE = new PlanRules(Set.of(), List.of(), List.of(), List.of(), DEFAULT_MAX_REPEATS,
            null, Set.of());

    /**
     * A dish asked for on one day.
     *
     * @param day
     *            the weekday
     * @param dish
     *            a word from the dish name, such as "pizza"
     * @param meal
     *            LUNCH or DINNER, or null to let the planner choose
     */
    public record Pin(DayOfWeek day, String dish, String meal) {
        public Pin {
            dish = dish == null ? null : dish.trim().toLowerCase(Locale.ROOT);
            meal = meal == null || !MealType.isValid(meal) ? null : meal.toUpperCase(Locale.ROOT);
        }

        /** @return e.g. "pizza on Friday" */
        public String describe() {
            final String d = day.name().charAt(0) + day.name().substring(1).toLowerCase(Locale.ROOT);
            return dish + " on " + d + (meal == null ? "" : " (" + meal.toLowerCase(Locale.ROOT) + ")");
        }
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public PlanRules {
        final Set<String> skip = new LinkedHashSet<>();
        for (final String m : skipMeals == null ? Set.<String>of() : skipMeals) {
            if (MealType.isValid(m)) {
                skip.add(m.toUpperCase(Locale.ROOT));
            }
        }
        // A plan with no meals is not a plan.
        if (skip.size() == 3) {
            skip.clear();
        }
        skipMeals = Set.copyOf(skip);
        avoid = cleanWords(avoid);
        prefer = cleanWords(prefer);
        final List<Pin> p = new ArrayList<>();
        for (final Pin pin : pins == null ? List.<Pin>of() : pins) {
            if (pin != null && pin.day() != null && pin.dish() != null && !pin.dish().isBlank() && p.size() < 7) {
                p.add(pin);
            }
        }
        pins = List.copyOf(p);
        maxRepeats = maxRepeats < 1 || maxRepeats > 7 ? DEFAULT_MAX_REPEATS : maxRepeats;
        weeklyBudget = weeklyBudget == null || weeklyBudget < 10 || weeklyBudget > 2000 ? null : weeklyBudget;
        final Set<String> g = new java.util.TreeSet<>();
        for (final String goal : goals == null ? Set.<String>of() : goals) {
            final String up = goal == null ? "" : goal.trim().toUpperCase(Locale.ROOT);
            if (GOALS.containsKey(up)) {
                g.add(up);
            }
        }
        // Vegan includes vegetarian; no desserts includes fewer.
        if (g.contains(VEGAN)) {
            g.remove(VEGETARIAN);
        }
        if (g.contains(NO_DESSERT)) {
            g.remove(FEWER_DESSERTS);
        }
        goals = Set.copyOf(g);
    }

    /**
     * @param goal
     *            a goal from {@link #GOALS}
     * @return true if it was asked for
     */
    public boolean has(final String goal) {
        return goals.contains(goal);
    }

    private static List<String> cleanWords(final List<String> words) {
        final Set<String> out = new LinkedHashSet<>();
        for (final String w : words == null ? List.<String>of() : words) {
            if (w != null && !w.isBlank() && w.length() <= 40 && out.size() < MAX_ITEMS) {
                out.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    /**
     * @return true if nothing was asked for
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return equals(NONE);
    }

    /**
     * @param other
     *            rules read another way
     * @return rules that keep every restriction from both: skips, avoids and
     *         pins are unioned, the lower repeat cap and budget win
     */
    public PlanRules mergeSafely(final PlanRules other) {
        final Set<String> skip = new LinkedHashSet<>(skipMeals);
        skip.addAll(other.skipMeals);
        final Set<String> av = new LinkedHashSet<>(avoid);
        av.addAll(other.avoid);
        final Set<String> pr = new LinkedHashSet<>(prefer);
        pr.addAll(other.prefer);
        final List<Pin> pn = new ArrayList<>(pins);
        for (final Pin pin : other.pins) {
            if (pn.stream().noneMatch(x -> x.day() == pin.day())) {
                pn.add(pin);
            }
        }
        Integer budget = weeklyBudget;
        if (other.weeklyBudget != null) {
            budget = budget == null ? other.weeklyBudget : Math.min(budget, other.weeklyBudget);
        }
        final Set<String> g = new LinkedHashSet<>(goals);
        g.addAll(other.goals);
        return new PlanRules(skip, new ArrayList<>(av), new ArrayList<>(pr), pn,
                Math.min(maxRepeats, other.maxRepeats), budget, g);
    }

    /**
     * @return plain words, such as "no breakfast; pizza on Friday; avoid salad"
     */
    public String describe() {
        final List<String> parts = new ArrayList<>();
        for (final String m : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)) {
            if (skipMeals.contains(m)) {
                parts.add("no " + m.toLowerCase(Locale.ROOT));
            }
        }
        for (final String goal : List.of(VEGAN, VEGETARIAN, HIGH_PROTEIN, LOW_CARB, NO_DESSERT, FEWER_DESSERTS, LIGHTER)) {
            if (goals.contains(goal)) {
                parts.add(GOALS.get(goal));
            }
        }
        pins.forEach(p -> parts.add(p.describe()));
        if (!avoid.isEmpty()) {
            parts.add("avoid " + String.join(", ", avoid));
        }
        if (!prefer.isEmpty()) {
            parts.add("more " + String.join(", ", prefer));
        }
        if (maxRepeats != DEFAULT_MAX_REPEATS) {
            parts.add("each main at most " + maxRepeats + "×");
        }
        if (weeklyBudget != null) {
            parts.add("under $" + weeklyBudget + " this week");
        }
        return String.join("; ", parts);
    }
}
