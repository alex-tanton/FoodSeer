package FoodSeer.mealplan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import FoodSeer.constant.MealType;
import FoodSeer.entity.Food;

/**
 * Builds a meal plan for several days at once. Pure and deterministic (no
 * Spring, no database, no randomness), so the service, the tests and the M0
 * eval run the same code.
 *
 * <p>
 * Each meal is one main dish plus up to two sides. Hard rules, which are never
 * broken: no dish that breaks a restriction or is out of stock, the weekly
 * spending limit (when any plan can meet it), the repeat cap on main dishes,
 * pinned dishes, and meals already ordered. Within those, it minimizes how far
 * each day lands from its calorie target, keeps each meal near its share of
 * the day, and prefers variety and requested dishes.
 * </p>
 *
 * <p>
 * Method: start from the cheapest valid plan, which proves the budget can be
 * met, then improve one meal at a time (local search) until no single change
 * helps.
 * </p>
 */
public final class MealPlanner {

    /** Share of the day's calories each meal aims for, before skipped meals are removed. */
    static final Map<String, Double> MEAL_SHARE = new LinkedHashMap<>();

    static {
        MEAL_SHARE.put(MealType.BREAKFAST, 0.25);
        MEAL_SHARE.put(MealType.LUNCH, 0.35);
        MEAL_SHARE.put(MealType.DINNER, 0.40);
    }

    /** A day within this fraction of its target counts as on target. */
    public static final double HIT_TOLERANCE = 0.10;
    static final int MAX_SIDES = 2;
    static final int MAX_PASSES = 40;
    /** Bisection steps when searching for the price of a dollar that fits the budget. */
    static final int LAMBDA_STEPS = 8;

    /** How strongly each concern counts in the objective. */
    static final double SAFE_MARGIN = 0.08;
    static final double OUTSIDE_MARGIN_PENALTY = 4.0;
    static final double MEAL_BALANCE = 0.6;
    static final double PER_SIDE = 0.01;
    static final double REPEAT = 0.03;
    static final double SIDE_REPEAT = 0.06;
    static final int SIDE_FREE_USES = 2;
    static final double PREFERRED = 0.05;
    static final double PER_DOLLAR = 0.0005;
    static final double ON_TARGET_BONUS = 0.5;
    static final double OVER_CAP = 1.0;
    /** A dessert costs a little, so desserts appear only when they help. */
    static final double DESSERT_COST = 0.2;
    /** A day more than this far from target is penalized steeply. */
    static final double DEEP_DEFICIT = 0.25;
    static final double DEEP_DEFICIT_PENALTY = 6.0;
    /** Breaking a day rule (a second dessert, a dish twice in a day). */
    static final double DAY_RULE = 1.0;
    /** Options outside this range of a meal's share are not considered. */
    static final double MIN_SHARE = 0.3;
    static final double MAX_SHARE = 2.0;
    static final int CALORIE_BUCKET = 30;
    static final int KEEP_PER_GROUP = 2;
    static final double PROTEIN_BONUS = 0.35;
    static final double CARB_COST = 0.1;

    /**
     * Identifies one meal in the plan.
     *
     * @param date
     *            the day
     * @param meal
     *            BREAKFAST, LUNCH or DINNER
     */
    public record Slot(LocalDate date, String meal) {
    }

    /**
     * Everything a plan depends on.
     *
     * @param menu
     *            every dish
     * @param restrictions
     *            the customer's resolved restrictions
     * @param start
     *            first day of the plan
     * @param days
     *            number of days, 1 to 7
     * @param dailyTarget
     *            calories to aim for each day
     * @param weeklyBudget
     *            spending limit in dollars for the whole plan, or null
     * @param rules
     *            the customer's requests
     * @param locked
     *            meals that must stay as they are (already ordered)
     * @param banned
     *            dishes that must not be the main of a given meal (swapped out)
     */
    public record Input(List<Food> menu, Restrictions restrictions, LocalDate start, int days, int dailyTarget,
            Integer weeklyBudget, PlanRules rules, Map<Slot, List<Food>> locked, Map<Slot, Set<Long>> banned) {
        public Input {
            restrictions = restrictions == null ? Restrictions.NONE : restrictions;
            rules = rules == null ? PlanRules.NONE : rules;
            locked = locked == null ? Map.of() : locked;
            banned = banned == null ? Map.of() : banned;
            days = Math.max(1, Math.min(7, days));
        }
    }

    /**
     * One planned meal.
     *
     * @param meal
     *            BREAKFAST, LUNCH or DINNER
     * @param target
     *            calories this meal aims for
     * @param foods
     *            main first, then sides
     * @param calories
     *            total calories
     * @param cost
     *            total price in dollars
     * @param locked
     *            true if it was kept as is (already ordered)
     */
    public record PlannedMeal(String meal, int target, List<Food> foods, int calories, int cost, boolean locked) {
    }

    /**
     * One planned day.
     *
     * @param date
     *            the day
     * @param target
     *            calorie target
     * @param meals
     *            planned meals in order
     * @param calories
     *            planned calories
     * @param cost
     *            planned spend in dollars
     */
    public record PlannedDay(LocalDate date, int target, List<PlannedMeal> meals, int calories, int cost) {
        /** @return true if the day lands within ±10% of its target */
        public boolean onTarget() {
            return target > 0 && Math.abs(calories - target) <= HIT_TOLERANCE * target;
        }
    }

    /**
     * @param days
     *            planned days
     * @param totalCost
     *            whole-plan spend
     * @param budgetMet
     *            false only when no plan could meet the spending limit
     * @param cheapestPossible
     *            the cheapest valid plan's cost, for messages
     * @param warnings
     *            things the customer should know, in words
     */
    public record Plan(List<PlannedDay> days, int totalCost, boolean budgetMet, int cheapestPossible,
            List<String> warnings) {
    }

    /** A meal choice: indexes into the food list, main first. */
    private record Option(int[] foods, int calories, int cost, boolean preferred, int desserts,
            boolean proteinMain, int carbs) {
    }

    /**
     * @param in
     *            inputs
     * @return the plan
     */
    public Plan plan(final Input in) {
        final List<String> warnings = new ArrayList<>();
        final List<Food> foods = new ArrayList<>();
        // Diet goals that are hard filters.
        Restrictions restrictions = in.restrictions();
        if (in.rules().has(PlanRules.VEGAN)) {
            restrictions = restrictions.plus(Restrictions.parse("vegan"));
        } else if (in.rules().has(PlanRules.VEGETARIAN)) {
            restrictions = restrictions.plus(Restrictions.parse("vegetarian"));
        }
        for (final Food f : in.menu()) {
            if (f.getAmount() > 0 && f.getCalories() != null && restrictions.violatedBy(f) == null
                    && !avoided(f, in.rules())
                    && !(in.rules().has(PlanRules.NO_DESSERT) && DishRoles.isDessert(f))) {
                foods.add(f);
            }
        }
        unapplied(in, warnings);
        final Map<Long, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < foods.size(); i++) {
            indexOf.put(foods.get(i).getId(), i);
        }

        // Meals and their calorie targets.
        final List<String> meals = new ArrayList<>();
        for (final String m : MEAL_SHARE.keySet()) {
            if (!in.rules().skipMeals().contains(m)) {
                meals.add(m);
            }
        }
        double shareTotal = 0;
        for (final String m : meals) {
            shareTotal += MEAL_SHARE.get(m);
        }
        final Map<String, Integer> mealTarget = new HashMap<>();
        for (final String m : meals) {
            mealTarget.put(m, (int) Math.round(in.dailyTarget() * MEAL_SHARE.get(m) / shareTotal));
        }

        // Every slot and the options it may take.
        final List<Slot> slots = new ArrayList<>();
        final Map<String, List<Option>> byMeal = new HashMap<>();
        for (final String m : meals) {
            byMeal.put(m, options(foods, m, in.rules()));
        }
        final List<List<Option>> candidates = new ArrayList<>();
        final boolean[] lockedSlot;
        final List<Option> lockedOption = new ArrayList<>();
        for (int d = 0; d < in.days(); d++) {
            final LocalDate date = in.start().plusDays(d);
            for (final String m : meals) {
                slots.add(new Slot(date, m));
            }
        }
        lockedSlot = new boolean[slots.size()];
        final Map<Slot, Integer> pinnedSlots = pinSlots(in, slots, foods, byMeal, warnings);
        for (int s = 0; s < slots.size(); s++) {
            final Slot slot = slots.get(s);
            final List<Food> lock = in.locked().get(slot);
            if (lock != null && !lock.isEmpty()) {
                lockedSlot[s] = true;
                lockedOption.add(lockedOption(lock, foods, indexOf));
                candidates.add(List.of());
                continue;
            }
            lockedOption.add(null);
            final Set<Long> ban = in.banned().getOrDefault(slot, Set.of());
            final String pinWord = pinnedSlots.containsKey(slot) ? in.rules().pins().get(pinnedSlots.get(slot)).dish()
                    : null;
            final List<Option> list = new ArrayList<>();
            for (final Option o : byMeal.get(slot.meal())) {
                if (ban.contains(foods.get(o.foods()[0]).getId())) {
                    continue;
                }
                if (pinWord != null && !containsDish(o, foods, pinWord)) {
                    continue;
                }
                // Meals far from their share are never worth scoring (speed only).
                final int share = mealTarget.get(slot.meal());
                if (pinWord == null && (o.calories() < MIN_SHARE * share || o.calories() > MAX_SHARE * share)) {
                    continue;
                }
                list.add(o);
            }
            if (list.isEmpty()) {
                warnings.add("No safe dish is available for " + label(slot) + ".");
            }
            candidates.add(list);
        }

        // Start from the cheapest valid plan.
        final Option[] chosen = new Option[slots.size()];
        final int[] mainUses = new int[foods.size()];
        for (int s = 0; s < slots.size(); s++) {
            if (lockedSlot[s]) {
                chosen[s] = lockedOption.get(s);
                if (chosen[s] != null && chosen[s].foods().length > 0 && chosen[s].foods()[0] >= 0) {
                    mainUses[chosen[s].foods()[0]]++;
                }
            }
        }
        // Fill the most constrained meals first (fewest different mains), so a
        // main that is the only choice somewhere is not used up elsewhere first.
        final List<Integer> order = new ArrayList<>();
        for (int s = 0; s < slots.size(); s++) {
            order.add(s);
        }
        order.sort((a, b) -> Integer.compare(distinctMains(candidates.get(a)), distinctMains(candidates.get(b))));
        // Give every meal a main within the repeat cap when that is possible at all
        // (a capacity matching, solved with augmenting paths), then pick each
        // meal's cheapest option with that main.
        final int[] assignedMain = assignMains(slots, candidates, lockedSlot, mainUses, foods.size(),
                in.rules(), pinnedSlots, foods);
        for (final int s : order) {
            if (lockedSlot[s]) {
                continue;
            }
            Option best = null;
            for (final Option o : candidates.get(s)) {
                if (assignedMain[s] >= 0 ? o.foods()[0] != assignedMain[s]
                        : mainUses[o.foods()[0]] >= in.rules().maxRepeats()
                                && !pinnedMain(pinnedSlots, in.rules(), slots.get(s), o, foods)) {
                    continue;
                }
                if (best == null || o.cost() < best.cost()
                        || o.cost() == best.cost() && mealError(o, mealTarget.get(slots.get(s).meal())) < mealError(
                                best, mealTarget.get(slots.get(s).meal()))) {
                    best = o;
                }
            }
            if (best == null && !candidates.get(s).isEmpty()) {
                // The repeat cap cannot be met for this meal: take the cheapest anyway and say so.
                best = candidates.get(s).stream().min((a, b) -> Integer.compare(a.cost(), b.cost())).orElseThrow();
                warnings.add("Not enough different dishes to keep each main to " + in.rules().maxRepeats()
                        + " times a week.");
            }
            chosen[s] = best;
            if (best != null) {
                mainUses[best.foods()[0]]++;
            }
        }
        final int cheapest = totalCost(chosen);
        Integer budget = in.weeklyBudget();
        boolean budgetMet = true;
        if (budget != null && cheapest > budget) {
            budgetMet = false;
            warnings.add("No plan fits $" + budget + "; the cheapest plan that meets your restrictions costs $"
                    + cheapest + ".");
            budget = null;
        }

        // Improve the plan. With no binding budget, one local search does it.
        // Otherwise price each dollar (lambda) so the plan fits, which spreads
        // money across the week instead of letting the first days spend it all,
        // then spend whatever is left.
        final Search search = new Search(slots, meals, mealTarget, in.dailyTarget(), foods, candidates, lockedSlot,
                pinnedSlots, in.rules());
        Option[] best = search.improve(chosen.clone(), mainUses.clone(), 0, null);
        final Option[] unlimited = best.clone();
        if (budget != null && totalCost(best) > budget) {
            double lo = 0;
            double hi = 0.002;
            Option[] fit = null;
            // Each search starts from the last one's plan (warm start), which converges much faster.
            Option[] warm = best.clone();
            for (int i = 0; i < 20 && fit == null; i++) {
                final Option[] trial = search.improve(warm.clone(), usesOf(warm, foods.size()), hi, null);
                warm = trial;
                if (totalCost(trial) <= budget) {
                    fit = trial;
                } else {
                    lo = hi;
                    hi *= 2;
                }
            }
            if (fit == null) {
                fit = chosen.clone();
            }
            for (int i = 0; i < LAMBDA_STEPS; i++) {
                final double mid = (lo + hi) / 2;
                final Option[] trial = search.improve(warm.clone(), usesOf(warm, foods.size()), mid, null);
                warm = trial;
                if (totalCost(trial) <= budget) {
                    fit = trial;
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            best = search.improve(fit, usesOf(fit, foods.size()), 0, budget);
        }
        System.arraycopy(best, 0, chosen, 0, chosen.length);
        // When the limit is what keeps days under target, say what would fix it.
        final int hitsWithLimit = onTargetDays(chosen, meals.size(), in.dailyTarget());
        final int hitsWithout = onTargetDays(unlimited, meals.size(), in.dailyTarget());
        if (budget != null && hitsWithout > hitsWithLimit) {
            // The cheapest plan found that still hits as many days as the unlimited one.
            int needed = totalCost(unlimited);
            Option[] warmer = unlimited.clone();
            for (double price = 0.002; price <= 0.064; price *= 2) {
                final Option[] trial = search.improve(warmer.clone(), usesOf(warmer, foods.size()), price, null);
                if (onTargetDays(trial, meals.size(), in.dailyTarget()) < hitsWithout) {
                    break;
                }
                needed = Math.min(needed, totalCost(trial));
                warmer = trial;
            }
            warnings.add("Your $" + budget + " limit keeps " + (hitsWithout - hitsWithLimit) + " day"
                    + (hitsWithout - hitsWithLimit == 1 ? "" : "s") + " under target; about $" + needed
                    + " would plan " + hitsWithout + " of " + in.days() + " days on target.");
        }

        // Assemble the result.
        final List<PlannedDay> days = new ArrayList<>();
        for (int d = 0; d < in.days(); d++) {
            final List<PlannedMeal> planned = new ArrayList<>();
            int cal = 0;
            int cost = 0;
            for (int k = 0; k < meals.size(); k++) {
                final int s = d * meals.size() + k;
                final Option o = chosen[s];
                final List<Food> dishes = new ArrayList<>();
                if (o != null) {
                    if (lockedSlot[s]) {
                        dishes.addAll(in.locked().get(slots.get(s)));
                    } else {
                        for (final int i : o.foods()) {
                            dishes.add(foods.get(i));
                        }
                    }
                }
                final int mc = o == null ? 0 : o.calories();
                final int mcost = o == null ? 0 : o.cost();
                planned.add(new PlannedMeal(meals.get(k), mealTarget.get(meals.get(k)), List.copyOf(dishes), mc, mcost,
                        lockedSlot[s]));
                cal += mc;
                cost += mcost;
            }
            days.add(new PlannedDay(in.start().plusDays(d), in.dailyTarget(), List.copyOf(planned), cal, cost));
        }
        for (final PlannedDay day : days) {
            if (!day.onTarget() && day.target() > 0) {
                final int pct = (int) Math.round(100.0 * (day.calories() - day.target()) / day.target());
                warnings.add(dayName(day.date()) + " is " + Math.abs(pct) + "% " + (pct < 0 ? "under" : "over")
                        + " your calorie target.");
            }
        }
        if (!in.restrictions().unrecognized().isEmpty()) {
            warnings.add("Could not recognize: " + String.join(", ", in.restrictions().unrecognized())
                    + ". Dishes with these words in their name were left out; check ingredients when ordering.");
        }
        return new Plan(List.copyOf(days), totalCost(chosen), budgetMet, cheapest, List.copyOf(warnings));
    }

    // ------------------------------------------------------------- options

    private static List<Option> options(final List<Food> foods, final String meal, final PlanRules rules) {
        final List<Integer> mains = new ArrayList<>();
        final List<Integer> sides = new ArrayList<>();
        for (int i = 0; i < foods.size(); i++) {
            if (DishRoles.canBeMain(foods.get(i), meal)) {
                mains.add(i);
            }
            if (DishRoles.canBeSide(foods.get(i), meal)) {
                sides.add(i);
            }
        }
        final List<Option> out = new ArrayList<>();
        for (final int m : mains) {
            out.add(option(foods, rules, m));
            for (int a = 0; a < sides.size(); a++) {
                if (sides.get(a) == m) {
                    continue;
                }
                out.add(option(foods, rules, m, sides.get(a)));
                for (int b = a + 1; b < sides.size() && MAX_SIDES >= 2; b++) {
                    final Food x = foods.get(sides.get(a));
                    final Food y = foods.get(sides.get(b));
                    // A real meal: one dessert at most, one drink at most.
                    if (sides.get(b) == m || DishRoles.isDessert(x) && DishRoles.isDessert(y)
                            || DishRoles.isDrink(x) && DishRoles.isDrink(y)) {
                        continue;
                    }
                    out.add(option(foods, rules, m, sides.get(a), sides.get(b)));
                }
            }
        }
        return prune(out);
    }

    /**
     * Drops near-duplicate options: for each main, among options with the same
     * dessert and carb counts, preference and calories (within 30 kcal), only
     * the two cheapest are kept. Two keep some variety in sides; the rest could
     * never be chosen for a better reason than price. This is for speed.
     */
    private static List<Option> prune(final List<Option> options) {
        final Map<String, List<Option>> groups = new LinkedHashMap<>();
        for (final Option o : options) {
            final String key = o.foods()[0] + "|" + o.desserts() + "|" + o.carbs() + "|" + o.preferred() + "|"
                    + o.calories() / CALORIE_BUCKET;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }
        final List<Option> out = new ArrayList<>();
        for (final List<Option> group : groups.values()) {
            group.sort((x, y) -> Integer.compare(x.cost(), y.cost()));
            out.addAll(group.subList(0, Math.min(KEEP_PER_GROUP, group.size())));
        }
        return out;
    }

    private static Option option(final List<Food> foods, final PlanRules rules, final int... idx) {
        int cal = 0;
        int cost = 0;
        boolean preferred = false;
        int desserts = 0;
        int carbs = 0;
        for (final int i : idx) {
            final Food f = foods.get(i);
            cal += f.getCalories();
            cost += f.getPrice();
            preferred |= matchesAny(f, rules.prefer());
            desserts += DishRoles.isDessert(f) ? 1 : 0;
            carbs += DishRoles.isCarbHeavy(f) ? 1 : 0;
        }
        return new Option(idx, cal, cost, preferred, desserts, DishRoles.isProteinRich(foods.get(idx[0])), carbs);
    }

    private static Option lockedOption(final List<Food> lock, final List<Food> foods, final Map<Long, Integer> idx) {
        int cal = 0;
        int cost = 0;
        final int[] ids = new int[lock.size()];
        for (int i = 0; i < lock.size(); i++) {
            final Food f = lock.get(i);
            cal += f.getCalories() == null ? 0 : f.getCalories();
            cost += f.getPrice();
            ids[i] = idx.getOrDefault(f.getId(), -1);
        }
        int desserts = 0;
        int carbs = 0;
        for (final Food f : lock) {
            desserts += DishRoles.isDessert(f) ? 1 : 0;
            carbs += DishRoles.isCarbHeavy(f) ? 1 : 0;
        }
        return new Option(ids, cal, cost, false, desserts, !lock.isEmpty() && DishRoles.isProteinRich(lock.get(0)),
                carbs);
    }

    /**
     * Says which requested words could not be applied, so a request is never
     * silently ignored.
     */
    private static void unapplied(final Input in, final List<String> warnings) {
        for (final String word : in.rules().prefer()) {
            if (in.menu().stream().noneMatch(f -> matchesAny(f, List.of(word)))) {
                warnings.add("\"" + word + "\" does not match any dish on the menu, so it was not used.");
            }
        }
        for (final String word : in.rules().avoid()) {
            final Restrictions r = Restrictions.parse(word);
            if (in.menu().stream().noneMatch(f -> matchesAny(f, List.of(word)) || r.violatedBy(f) != null)) {
                warnings.add("Nothing on the menu matches \"" + word + "\", so nothing was removed for it.");
            }
        }
    }

    private static boolean avoided(final Food food, final PlanRules rules) {
        if (matchesAny(food, rules.avoid())) {
            return true;
        }
        for (final String a : rules.avoid()) {
            if (Restrictions.parse(a).violatedBy(food) != null) {
                return true;
            }
        }
        return false;
    }

    /** True if any word appears as a whole word (singular or plural) in the dish name. */
    static boolean matchesAny(final Food food, final List<String> words) {
        final String name = " " + food.getFoodName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ") + " ";
        for (final String w : words) {
            final String word = w.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]+", " ").trim();
            if (word.isEmpty()) {
                continue;
            }
            final String singular = word.endsWith("s") && word.length() > 3 ? word.substring(0, word.length() - 1)
                    : word;
            if (name.contains(" " + word + " ") || name.contains(" " + singular + " ")
                    || name.contains(" " + singular + "s ") || name.contains(" " + singular + "es ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDish(final Option o, final List<Food> foods, final String word) {
        for (final int i : o.foods()) {
            if (matchesAny(foods.get(i), List.of(word))) {
                return true;
            }
        }
        return false;
    }

    /** Assigns each pin to a meal on its day; drops pins that cannot be met. */
    private static Map<Slot, Integer> pinSlots(final Input in, final List<Slot> slots, final List<Food> foods,
            final Map<String, List<Option>> byMeal, final List<String> warnings) {
        final Map<Slot, Integer> out = new HashMap<>();
        for (int p = 0; p < in.rules().pins().size(); p++) {
            final PlanRules.Pin pin = in.rules().pins().get(p);
            Slot target = null;
            for (final String meal : pin.meal() != null ? List.of(pin.meal())
                    : List.of(MealType.DINNER, MealType.LUNCH, MealType.BREAKFAST)) {
                for (final Slot s : slots) {
                    if (s.date().getDayOfWeek() == pin.day() && s.meal().equals(meal) && !out.containsKey(s)
                            && !in.locked().containsKey(s)) {
                        final boolean possible = byMeal.get(meal).stream()
                                .anyMatch(o -> containsDish(o, foods, pin.dish()));
                        if (possible) {
                            target = s;
                        }
                    }
                }
                if (target != null) {
                    break;
                }
            }
            if (target == null) {
                final boolean inWeek = slots.stream().anyMatch(s -> s.date().getDayOfWeek() == pin.day());
                // A day whose meals are all kept as they are (a swap on another day) is already settled.
                final boolean settled = slots.stream().filter(s -> s.date().getDayOfWeek() == pin.day())
                        .allMatch(s -> in.locked().containsKey(s));
                if (inWeek && !settled) {
                    warnings.add("Could not plan " + pin.describe()
                            + ": no matching dish is on the menu and safe for you.");
                }
                continue;
            }
            out.put(target, p);
        }
        return out;
    }

    /** Local search over one plan: change one meal at a time while it helps. */
    private static final class Search {
        private final List<Slot> slots;
        private final List<String> meals;
        private final Map<String, Integer> mealTarget;
        private final int dailyTarget;
        private final List<Food> foods;
        private final List<List<Option>> candidates;
        private final boolean[] lockedSlot;
        private final Map<Slot, Integer> pinned;
        private final PlanRules rules;
        private final int maxRepeats;

        Search(final List<Slot> slots, final List<String> meals, final Map<String, Integer> mealTarget,
                final int dailyTarget, final List<Food> foods, final List<List<Option>> candidates,
                final boolean[] lockedSlot, final Map<Slot, Integer> pinned, final PlanRules rules) {
            this.slots = slots;
            this.meals = meals;
            this.mealTarget = mealTarget;
            this.dailyTarget = dailyTarget;
            this.foods = foods;
            this.candidates = candidates;
            this.lockedSlot = lockedSlot;
            this.pinned = pinned;
            this.rules = rules;
            this.maxRepeats = rules.maxRepeats();
        }

        /**
         * @param chosen
         *            starting plan, changed in place
         * @param mainUses
         *            uses of each main in {@code chosen}, changed in place
         * @param lambda
         *            price of one dollar in the objective
         * @param budget
         *            hard spending limit, or null
         * @return the improved plan
         */
        Option[] improve(final Option[] chosen, final int[] mainUses, final double lambda, final Integer budget) {
            int total = totalCost(chosen);
            final int[] sideUses = sideUsesOf(chosen, mainUses.length);
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                boolean improved = false;
                for (int s = 0; s < slots.size(); s++) {
                    if (lockedSlot[s] || chosen[s] == null) {
                        continue;
                    }
                    final int dayStart = s - s % meals.size();
                    final Option current = chosen[s];
                    final double before = dayScore(chosen, dayStart, meals, mealTarget, dailyTarget, foods, rules)
                            + lambda * current.cost();
                    Option bestOption = null;
                    double bestScore = before - 1e-9;
                    for (final Option o : candidates.get(s)) {
                        if (o == current) {
                            continue;
                        }
                        final int main = o.foods()[0];
                        final boolean sameMain = main == current.foods()[0];
                        if (!sameMain && mainUses[main] >= maxRepeats
                                && !pinnedMain(pinned, rules, slots.get(s), o, foods)) {
                            continue;
                        }
                        if (budget != null && total - current.cost() + o.cost() > budget) {
                            continue;
                        }
                        chosen[s] = o;
                        final double after = dayScore(chosen, dayStart, meals, mealTarget, dailyTarget, foods, rules)
                                + lambda * o.cost()
                                + (sameMain ? 0 : repeatDelta(mainUses, current.foods()[0], main, maxRepeats))
                                + sideDelta(sideUses, current, o);
                        chosen[s] = current;
                        if (after < bestScore) {
                            bestScore = after;
                            bestOption = o;
                        }
                    }
                    if (bestOption != null) {
                        mainUses[current.foods()[0]]--;
                        mainUses[bestOption.foods()[0]]++;
                        moveSides(sideUses, current, bestOption);
                        total += bestOption.cost() - current.cost();
                        chosen[s] = bestOption;
                        improved = true;
                    }
                }
                if (!improved) {
                    break;
                }
            }
            return chosen;
        }
    }

    /**
     * Assigns a main to each open meal so no main exceeds the cap (pinned mains
     * excepted), using augmenting paths. Meals that cannot be covered get -1.
     */
    private static int[] assignMains(final List<Slot> slots, final List<List<Option>> candidates,
            final boolean[] lockedSlot, final int[] lockedUses, final int foodCount, final PlanRules rules,
            final Map<Slot, Integer> pinned, final List<Food> foods) {
        final int n = slots.size();
        final List<List<Integer>> mainsOf = new ArrayList<>();
        for (int s = 0; s < n; s++) {
            final Set<Integer> m = new java.util.LinkedHashSet<>();
            if (!lockedSlot[s]) {
                for (final Option o : candidates.get(s)) {
                    m.add(o.foods()[0]);
                }
            }
            mainsOf.add(new ArrayList<>(m));
        }
        final int[] capacity = new int[foodCount];
        for (int f = 0; f < foodCount; f++) {
            capacity[f] = Math.max(0, rules.maxRepeats() - lockedUses[f]);
        }
        final int[] assigned = new int[n];
        java.util.Arrays.fill(assigned, -1);
        final List<List<Integer>> holders = new ArrayList<>();
        for (int f = 0; f < foodCount; f++) {
            holders.add(new ArrayList<>());
        }
        // Pinned meals claim their dish first, so the cap left for other meals is right.
        final List<Integer> pinnedFirst = new ArrayList<>();
        for (int s = 0; s < n; s++) {
            if (pinned.containsKey(slots.get(s))) {
                pinnedFirst.add(0, s);
            } else {
                pinnedFirst.add(s);
            }
        }
        for (final int s : pinnedFirst) {
            if (lockedSlot[s] || mainsOf.get(s).isEmpty()) {
                continue;
            }
            // A pinned meal takes its pinned main regardless of the cap.
            if (pinned.containsKey(slots.get(s))) {
                for (final int m : mainsOf.get(s)) {
                    if (matchesAny(foods.get(m), List.of(rules.pins().get(pinned.get(slots.get(s))).dish()))) {
                        assigned[s] = m;
                        holders.get(m).add(s);
                        capacity[m]--;
                        break;
                    }
                }
                continue;
            }
            augment(s, mainsOf, capacity, assigned, holders, new boolean[foodCount], pinned, slots);
        }
        return assigned;
    }

    private static boolean augment(final int s, final List<List<Integer>> mainsOf, final int[] capacity,
            final int[] assigned, final List<List<Integer>> holders, final boolean[] seen,
            final Map<Slot, Integer> pinned, final List<Slot> slots) {
        for (final int m : mainsOf.get(s)) {
            if (seen[m]) {
                continue;
            }
            seen[m] = true;
            if (capacity[m] > 0) {
                capacity[m]--;
                assigned[s] = m;
                holders.get(m).add(s);
                return true;
            }
            for (final int other : new ArrayList<>(holders.get(m))) {
                if (pinned.containsKey(slots.get(other))) {
                    continue;
                }
                if (augment(other, mainsOf, capacity, assigned, holders, seen, pinned, slots)) {
                    holders.get(m).remove(Integer.valueOf(other));
                    assigned[s] = m;
                    holders.get(m).add(s);
                    return true;
                }
            }
        }
        return false;
    }

    /** Side uses per food in a plan. */
    private static int[] sideUsesOf(final Option[] chosen, final int foodCount) {
        final int[] uses = new int[foodCount];
        for (final Option o : chosen) {
            if (o == null) {
                continue;
            }
            for (int i = 1; i < o.foods().length; i++) {
                if (o.foods()[i] >= 0) {
                    uses[o.foods()[i]]++;
                }
            }
        }
        return uses;
    }

    private static void moveSides(final int[] sideUses, final Option from, final Option to) {
        for (int i = 1; i < from.foods().length; i++) {
            sideUses[from.foods()[i]]--;
        }
        for (int i = 1; i < to.foods().length; i++) {
            sideUses[to.foods()[i]]++;
        }
    }

    /**
     * Variety for sides: every use of a side beyond {@link #SIDE_FREE_USES} a
     * week costs {@link #SIDE_REPEAT}.
     *
     * @return the change in that cost when a meal's sides change from {@code from} to {@code to}
     */
    static double sideDelta(final int[] sideUses, final Option from, final Option to) {
        // At most four foods are touched (two sides each); no allocation, this runs millions of times.
        final int[] touched = new int[4];
        int n = 0;
        for (final Option o : new Option[] {from, to}) {
            for (int i = 1; i < o.foods().length; i++) {
                final int f = o.foods()[i];
                boolean dup = false;
                for (int t = 0; t < n; t++) {
                    dup |= touched[t] == f;
                }
                if (!dup && f >= 0) {
                    touched[n++] = f;
                }
            }
        }
        double change = 0;
        for (int t = 0; t < n; t++) {
            final int f = touched[t];
            int after = sideUses[f];
            for (int i = 1; i < from.foods().length; i++) {
                after -= from.foods()[i] == f ? 1 : 0;
            }
            for (int i = 1; i < to.foods().length; i++) {
                after += to.foods()[i] == f ? 1 : 0;
            }
            change += Math.max(0, after - SIDE_FREE_USES) - Math.max(0, sideUses[f] - SIDE_FREE_USES);
        }
        return SIDE_REPEAT * change;
    }

    private static int onTargetDays(final Option[] chosen, final int mealsPerDay, final int target) {
        int hits = 0;
        for (int start = 0; start + mealsPerDay <= chosen.length; start += mealsPerDay) {
            int cal = 0;
            for (int k = 0; k < mealsPerDay; k++) {
                cal += chosen[start + k] == null ? 0 : chosen[start + k].calories();
            }
            hits += target > 0 && Math.abs(cal - target) <= HIT_TOLERANCE * target ? 1 : 0;
        }
        return hits;
    }

    private static int distinctMains(final List<Option> options) {
        final Set<Integer> mains = new HashSet<>();
        for (final Option o : options) {
            mains.add(o.foods()[0]);
        }
        return mains.size();
    }

    private static int[] usesOf(final Option[] chosen, final int foodCount) {
        final int[] uses = new int[foodCount];
        for (final Option o : chosen) {
            if (o != null && o.foods().length > 0 && o.foods()[0] >= 0) {
                uses[o.foods()[0]]++;
            }
        }
        return uses;
    }

    // ----------------------------------------------------------- objective

    private static double mealError(final Option o, final int target) {
        return target <= 0 ? 0 : Math.abs(o.calories() - target) / (double) target;
    }

    private static double dayScore(final Option[] chosen, final int dayStart, final List<String> meals,
            final Map<String, Integer> mealTarget, final int dailyTarget, final List<Food> foods,
            final PlanRules rules) {
        int cal = 0;
        double score = 0;
        int desserts = 0;
        int duplicates = 0;
        final double dessertCost = rules.has(PlanRules.FEWER_DESSERTS) ? 4 * DESSERT_COST : DESSERT_COST;
        for (int k = 0; k < meals.size(); k++) {
            final Option o = chosen[dayStart + k];
            if (o == null) {
                continue;
            }
            cal += o.calories();
            score += MEAL_BALANCE * mealError(o, mealTarget.get(meals.get(k)));
            score += PER_SIDE * (o.foods().length - 1);
            score += PER_DOLLAR * o.cost();
            score -= o.preferred() ? PREFERRED : 0;
            score += dessertCost * o.desserts();
            desserts += o.desserts();
            if (rules.has(PlanRules.HIGH_PROTEIN)) {
                score -= o.proteinMain() ? PROTEIN_BONUS : 0;
            }
            if (rules.has(PlanRules.LOW_CARB)) {
                score += CARB_COST * o.carbs();
            }
            // A dish already used earlier the same day (no allocation: this runs millions of times).
            for (int j = 0; j < k; j++) {
                final Option earlier = chosen[dayStart + j];
                if (earlier == null) {
                    continue;
                }
                for (final int f : o.foods()) {
                    for (final int g : earlier.foods()) {
                        if (f >= 0 && f == g) {
                            duplicates++;
                        }
                    }
                }
            }
        }
        // Real days: one dessert at most, and no dish twice in a day.
        score += DAY_RULE * (Math.max(0, desserts - 1) + duplicates);
        if (dailyTarget > 0) {
            final double err = Math.abs(cal - dailyTarget) / (double) dailyTarget;
            score += err + OUTSIDE_MARGIN_PENALTY * Math.max(0, err - SAFE_MARGIN);
            // Never starve a day to finish others: a day far short costs much more.
            score += DEEP_DEFICIT_PENALTY * Math.max(0, err - DEEP_DEFICIT);
            // Finishing a day on target is worth more than leaving every day a little short.
            score -= err <= SAFE_MARGIN ? ON_TARGET_BONUS : 0;
        }
        return score;
    }

    /**
     * Variety: every use of a main beyond the first costs {@link #REPEAT}, and
     * every use beyond the repeat cap costs {@link #OVER_CAP}, so the search
     * repairs a starting plan that had to break the cap.
     *
     * @return the change in that cost when one use moves from {@code from} to {@code to}
     */
    static double repeatDelta(final int[] mainUses, final int from, final int to, final int cap) {
        final int extraTo = mainUses[to] >= 1 ? 1 : 0;
        final int extraFrom = mainUses[from] >= 2 ? 1 : 0;
        final int overTo = mainUses[to] >= cap ? 1 : 0;
        final int overFrom = mainUses[from] > cap ? 1 : 0;
        return REPEAT * (extraTo - extraFrom) + OVER_CAP * (overTo - overFrom);
    }

    /** True if the slot is pinned and this option's main is the pinned dish (exempt from the cap). */
    private static boolean pinnedMain(final Map<Slot, Integer> pinned, final PlanRules rules, final Slot slot,
            final Option o, final List<Food> foods) {
        final Integer p = pinned.get(slot);
        return p != null && matchesAny(foods.get(o.foods()[0]), List.of(rules.pins().get(p).dish()));
    }

    private static int totalCost(final Option[] chosen) {
        int total = 0;
        for (final Option o : chosen) {
            total += o == null ? 0 : o.cost();
        }
        return total;
    }

    static String dayName(final LocalDate date) {
        final String n = date.getDayOfWeek().name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String label(final Slot slot) {
        return dayName(slot.date()) + " " + slot.meal().toLowerCase(Locale.ROOT);
    }
}
