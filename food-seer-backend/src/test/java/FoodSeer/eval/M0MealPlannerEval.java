package FoodSeer.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import FoodSeer.constant.MealType;
import FoodSeer.entity.Food;
import FoodSeer.mealplan.DishRoles;
import FoodSeer.mealplan.MealPlanner;
import FoodSeer.mealplan.PlanRules;
import FoodSeer.mealplan.Restrictions;

/**
 * M0, the risk-killer eval for the weekly meal planner.
 *
 * <p>
 * <b>Claim</b> (fixed in docs/p3/M0_MEAL_PLANNER.md before this eval existed).
 * At least 80% of planned days within ±10% of the calorie target, at least 20
 * points above the best baseline, Cliff's delta at least 0.33 on per-seed hit
 * rate, and in every seed: 0 unsafe dishes, 0 feasible plans over budget, 0
 * main dishes over the repeat cap, 0 unhonored safe pins.
 * </p>
 *
 * <p>
 * Run: {@code mvn -Dtest=M0MealPlannerEval test}. Add {@code -Dm0.seeds=dev} for
 * the tuning seeds.
 * </p>
 */
class M0MealPlannerEval {

    static final long[] SCORED_SEEDS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    static final long[] DEV_SEEDS = {101, 102, 103, 104, 105, 106, 107, 108, 109, 110};
    static final int CUSTOMERS = 200;
    static final int DAYS = 7;
    static final double REQUEST_SHARE = 0.30;
    static final double CHECKBOX_SHARE = 0.60;
    static final LocalDate START = LocalDate.of(2026, 11, 23); // a Monday
    /** CI regression floors, not the claim: below the scored run (79.4%, +24.5 points). */
    static final double REGRESSION_FLOOR = 0.75;
    static final double REGRESSION_LEAD = 0.15;

    /** Seed menu prices in dollars, in {@link M0TraceEval#MENU} order (DataInitializer). */
    static final int[] PRICE = {3, 2, 4, 1, 2, 5, 4, 3, 12, 10, 11, 14, 8, 11, 9, 10, 13, 10, 28, 24, 35, 32, 30, 11,
        12, 13, 9, 7, 6, 5, 8};
    /** Seed menu calories, in {@link M0TraceEval#MENU} order (DataInitializer). */
    static final int[] CALORIES = {5, 2, 250, 105, 95, 110, 150, 190, 450, 120, 470, 600, 285, 650, 110, 180, 255, 450,
        679, 412, 320, 500, 720, 390, 380, 350, 420, 450, 270, 160, 130};

    static final String[] PIN_WORDS = {"pizza", "sushi", "pasta", "steak", "burrito", "salmon", "soup", "wrap"};
    static final String[] AVOID_WORDS = {"salad", "soup", "sushi", "wrap", "burger"};
    static final String[] MEALS = {MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER};
    static final Map<String, Double> SHARE = Map.of(MealType.BREAKFAST, 0.25, MealType.LUNCH, 0.35,
            MealType.DINNER, 0.40);

    enum Approach {
        PLANNER("Meal planner"),
        DAY_BY_DAY("Day-by-day Personal Diet"),
        GREEDY("Greedy calories"),
        RANDOM("Random");

        final String label;

        Approach(final String label) {
            this.label = label;
        }
    }

    /**
     * @param budgetFactor
     *            weekly budget as a share of what this customer would spend
     *            ordering day by day (0.6 to 1.0)
     * @param tightBudget
     *            the originally pre-registered budget ($140 to $320), for the stress test
     */
    record Customer(String restrictionsText, Set<String> trueAllergens, int target, double budgetFactor,
            int tightBudget, PlanRules rules) {
    }

    /** Per-seed results. */
    static final class SeedResult {
        final Map<Approach, int[]> hits = new EnumMap<>(Approach.class); // [hit days, planned days]
        final Map<Approach, double[]> calErr = new EnumMap<>(Approach.class); // [sum abs err, days]
        final Map<Approach, long[]> spend = new EnumMap<>(Approach.class); // [sum spend, plans, over budget]
        final Map<Approach, int[]> stress = new EnumMap<>(Approach.class); // tight budgets: [hit days, days]
        int unsafe;
        int overBudgetFeasible;
        int feasiblePlans;
        int repeatViolations;
        int capFeasiblePlans;
        int pinsUnhonored;
        int safePins;
        int distinctDishes;
        int ruleBreakDays;
        int plans;

        SeedResult() {
            for (final Approach a : Approach.values()) {
                hits.put(a, new int[2]);
                calErr.put(a, new double[2]);
                spend.put(a, new long[3]);
                stress.put(a, new int[2]);
            }
        }

        double hitRate(final Approach a) {
            return hits.get(a)[1] == 0 ? 0 : hits.get(a)[0] / (double) hits.get(a)[1];
        }
    }

    @Test
    void m0Claim() throws IOException {
        final boolean dev = "dev".equalsIgnoreCase(System.getProperty("m0.seeds"));
        final long[] seeds = dev ? DEV_SEEDS : SCORED_SEEDS;
        final List<SeedResult> results = new ArrayList<>();
        for (final long seed : seeds) {
            results.add(runSeed(seed));
        }
        final String report = report(results, seeds, dev);
        final Path out = Path.of("target", "m0-eval", dev ? "m0-meal-planner-dev-report.md" : "m0-meal-planner-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);
        // The report carries the pre-registered verdict (the scored run is recorded as a
        // FAIL in docs/p3/M0_MEAL_PLANNER.md). The build guards the hard guarantees, which
        // must hold on every run, plus regression floors set below today's results.
        final Verdict v = verdict(results);
        assertEquals(0, v.unsafe(), "unsafe dish planned");
        assertEquals(0, v.overBudget(), "feasible plan over budget");
        assertEquals(0, v.repeats(), "main dish over the repeat cap");
        assertEquals(0, v.pinsMissed(), "safe pin not honored");
        assertTrue(v.planner() >= REGRESSION_FLOOR, "planner hit rate fell below the regression floor");
        assertTrue(v.planner() - v.best() >= REGRESSION_LEAD, "planner lead over the best baseline fell");
    }

    @Test
    void menuMatchesTheSeedMenu() {
        assertEquals(M0TraceEval.MENU.length, PRICE.length);
        assertEquals(M0TraceEval.MENU.length, CALORIES.length);
    }

    // ------------------------------------------------------------- world

    static List<Food> buildMenu(final Random rng) {
        final List<Food> menu = M0TraceEval.buildMenu();
        for (int i = 0; i < menu.size(); i++) {
            final Food f = menu.get(i);
            f.setPrice(PRICE[i]);
            f.setCalories(CALORIES[i]);
            f.setAmount(50);
            f.setRating(3.0 + rng.nextDouble() * 2.0);
            f.setNumberOfRatings(5 + rng.nextInt(30));
        }
        return menu;
    }

    static List<Customer> buildCustomers(final Random rng, final List<Food> menu) {
        final List<Customer> out = new ArrayList<>();
        for (int c = 0; c < CUSTOMERS; c++) {
            final Set<String> truth = new LinkedHashSet<>();
            final List<String> typed = new ArrayList<>();
            final boolean checkbox = rng.nextDouble() < CHECKBOX_SHARE;
            for (int a = 0; a < M0TraceEval.ALLERGENS.length; a++) {
                if (rng.nextDouble() < M0TraceEval.PREVALENCE[a]) {
                    truth.add(M0TraceEval.ALLERGENS[a]);
                    typed.add(checkbox ? M0TraceEval.CHECKBOX_VALUE[a]
                            : M0TraceEval.FREE_TEXT[a][rng.nextInt(M0TraceEval.FREE_TEXT[a].length)]);
                }
            }
            final int goal = 1400 + rng.nextInt(801);
            final int burn = 150 + rng.nextInt(351);
            final double factor = 0.6 + 0.4 * rng.nextDouble();
            final int tight = 140 + rng.nextInt(181);
            PlanRules rules = PlanRules.NONE;
            if (rng.nextDouble() < REQUEST_SHARE) {
                final int kind = rng.nextInt(3);
                if (kind == 0) {
                    final DayOfWeek day = START.plusDays(rng.nextInt(DAYS)).getDayOfWeek();
                    rules = new PlanRules(Set.of(), List.of(), List.of(),
                            List.of(new PlanRules.Pin(day, PIN_WORDS[rng.nextInt(PIN_WORDS.length)], null)),
                            PlanRules.DEFAULT_MAX_REPEATS, null);
                } else if (kind == 1) {
                    rules = new PlanRules(Set.of(rng.nextBoolean() ? MealType.BREAKFAST : MealType.LUNCH), List.of(),
                            List.of(), List.of(), PlanRules.DEFAULT_MAX_REPEATS, null);
                } else {
                    rules = new PlanRules(Set.of(), List.of(AVOID_WORDS[rng.nextInt(AVOID_WORDS.length)]), List.of(),
                            List.of(), PlanRules.DEFAULT_MAX_REPEATS, null);
                }
            }
            out.add(new Customer(typed.isEmpty() ? null : String.join(", ", typed), truth, goal + burn, factor, tight,
                    rules));
        }
        return out;
    }

    // ---------------------------------------------------------- baselines

    /** A meal choice for the baselines: main first, then up to two sides. */
    record Option(List<Food> foods, int calories, int cost, int desserts) {
    }

    static List<Option> options(final List<Food> safe, final String meal) {
        final List<Food> mains = safe.stream().filter(f -> DishRoles.canBeMain(f, meal)).toList();
        final List<Food> sides = safe.stream().filter(f -> DishRoles.canBeSide(f, meal)).toList();
        final List<Option> out = new ArrayList<>();
        for (final Food m : mains) {
            out.add(option(m));
            for (int a = 0; a < sides.size(); a++) {
                if (sides.get(a) == m) {
                    continue;
                }
                out.add(option(m, sides.get(a)));
                for (int b = a + 1; b < sides.size(); b++) {
                    final Food x = sides.get(a);
                    final Food y = sides.get(b);
                    if (y != m && !(DishRoles.isDessert(x) && DishRoles.isDessert(y))
                            && !(DishRoles.isDrink(x) && DishRoles.isDrink(y))) {
                        out.add(option(m, x, y));
                    }
                }
            }
        }
        return out;
    }

    static Option option(final Food... foods) {
        int cal = 0;
        int cost = 0;
        int desserts = 0;
        for (final Food f : foods) {
            cal += f.getCalories();
            cost += f.getPrice();
            desserts += DishRoles.isDessert(f) ? 1 : 0;
        }
        return new Option(List.of(foods), cal, cost, desserts);
    }

    /** {@link #fitsDay} for an option, without allocating (it runs millions of times). */
    static boolean fits(final Option o, final List<Food> day, final int dessertsToday) {
        if (dessertsToday + o.desserts() > 1) {
            return false;
        }
        for (final Food f : o.foods()) {
            for (final Food g : day) {
                if (f == g) {
                    return false;
                }
            }
        }
        return true;
    }

    static int desserts(final List<Food> day) {
        int n = 0;
        for (final Food f : day) {
            n += DishRoles.isDessert(f) ? 1 : 0;
        }
        return n;
    }

    /** Each baseline returns per-day planned calories and total spend. */
    record BaselinePlan(int[] dayCalories, int spend) {
    }

    /**
     * The same day rules the planner follows, so baselines cannot reach calories
     * by stacking desserts or repeating a dish within a day.
     */
    static boolean fitsDay(final List<Food> option, final List<Food> day) {
        long desserts = day.stream().filter(DishRoles::isDessert).count()
                + option.stream().filter(DishRoles::isDessert).count();
        return desserts <= 1 && option.stream().noneMatch(day::contains);
    }

    static List<String> meals(final PlanRules rules) {
        final List<String> out = new ArrayList<>();
        for (final String m : MEALS) {
            if (!rules.skipMeals().contains(m)) {
                out.add(m);
            }
        }
        return out;
    }

    static int mealTarget(final int target, final String meal, final List<String> meals) {
        double total = 0;
        for (final String m : meals) {
            total += SHARE.get(m);
        }
        return (int) Math.round(target * SHARE.get(meal) / total);
    }

    /** Personal Diet today: for each meal, the best-rated dishes that still fit the day's remaining calories. */
    static BaselinePlan dayByDay(final List<Food> safe, final Customer c) {
        final List<String> meals = meals(c.rules());
        final int[] days = new int[DAYS];
        int spend = 0;
        final Map<Long, Integer> mainUses = new HashMap<>();
        for (int d = 0; d < DAYS; d++) {
            int remaining = c.target();
            final List<Food> today = new ArrayList<>();
            for (int k = 0; k < meals.size(); k++) {
                final String meal = meals.get(k);
                final int share = k == meals.size() - 1 ? remaining : mealTarget(c.target(), meal, meals);
                Food main = null;
                for (final Food f : safe) {
                    if (DishRoles.canBeMain(f, meal) && f.getCalories() <= remaining && !today.contains(f)
                            && mainUses.getOrDefault(f.getId(), 0) < PlanRules.DEFAULT_MAX_REPEATS
                            && (main == null || f.getRating() > main.getRating())) {
                        main = f;
                    }
                }
                if (main == null) {
                    continue;
                }
                mainUses.merge(main.getId(), 1, Integer::sum);
                today.add(main);
                int mealCal = main.getCalories();
                spend += main.getPrice();
                final List<Food> sides = new ArrayList<>(safe.stream().filter(f -> DishRoles.canBeSide(f, meal)
                        && f != null).toList());
                sides.sort((x, y) -> Double.compare(y.getRating(), x.getRating()));
                int added = 0;
                for (final Food s : sides) {
                    if (added == 2 || s == main) {
                        continue;
                    }
                    if (mealCal + s.getCalories() <= share && mealCal + s.getCalories() <= remaining
                            && fitsDay(List.of(s), today)) {
                        today.add(s);
                        mealCal += s.getCalories();
                        spend += s.getPrice();
                        added++;
                    }
                }
                remaining -= mealCal;
                days[d] += mealCal;
            }
        }
        return new BaselinePlan(days, spend);
    }

    /** For each meal, the option closest to its share, within an even split of the money left. */
    static BaselinePlan greedy(final Map<String, List<Option>> byMeal, final Customer c, final int budget) {
        final List<String> meals = meals(c.rules());
        final int[] days = new int[DAYS];
        int spend = 0;
        int money = budget;
        final Map<Long, Integer> mainUses = new HashMap<>();
        int slotsLeft = DAYS * meals.size();
        for (int d = 0; d < DAYS; d++) {
            final List<Food> today = new ArrayList<>();
            for (final String meal : meals) {
                final int share = mealTarget(c.target(), meal, meals);
                final double allot = money / (double) slotsLeft;
                final int dessertsToday = desserts(today);
                Option best = null;
                Option cheapest = null;
                for (final Option o : byMeal.get(meal)) {
                    if (mainUses.getOrDefault(o.foods().get(0).getId(), 0) >= PlanRules.DEFAULT_MAX_REPEATS
                            || !fits(o, today, dessertsToday)) {
                        continue;
                    }
                    if (cheapest == null || o.cost() < cheapest.cost()) {
                        cheapest = o;
                    }
                    if (o.cost() <= allot && (best == null
                            || Math.abs(o.calories() - share) < Math.abs(best.calories() - share))) {
                        best = o;
                    }
                }
                final Option pick = best != null ? best : cheapest;
                slotsLeft--;
                if (pick == null) {
                    continue;
                }
                mainUses.merge(pick.foods().get(0).getId(), 1, Integer::sum);
                today.addAll(pick.foods());
                days[d] += pick.calories();
                spend += pick.cost();
                money -= pick.cost();
            }
        }
        return new BaselinePlan(days, spend);
    }

    static BaselinePlan random(final Map<String, List<Option>> byMeal, final Customer c, final Random rng) {
        final List<String> meals = meals(c.rules());
        final int[] days = new int[DAYS];
        int spend = 0;
        final Map<Long, Integer> mainUses = new HashMap<>();
        for (int d = 0; d < DAYS; d++) {
            final List<Food> today = new ArrayList<>();
            for (final String meal : meals) {
                final int dessertsToday = desserts(today);
                final List<Option> ok = new ArrayList<>();
                for (final Option o : byMeal.get(meal)) {
                    if (mainUses.getOrDefault(o.foods().get(0).getId(), 0) < PlanRules.DEFAULT_MAX_REPEATS
                            && fits(o, today, dessertsToday)) {
                        ok.add(o);
                    }
                }
                if (ok.isEmpty()) {
                    continue;
                }
                final Option pick = ok.get(rng.nextInt(ok.size()));
                mainUses.merge(pick.foods().get(0).getId(), 1, Integer::sum);
                today.addAll(pick.foods());
                days[d] += pick.calories();
                spend += pick.cost();
            }
        }
        return new BaselinePlan(days, spend);
    }

    // ------------------------------------------------------------ run seed

    static SeedResult runSeed(final long seed) {
        final Random rng = new Random(seed);
        final List<Food> menu = buildMenu(rng);
        final List<Customer> customers = buildCustomers(rng, menu);
        final MealPlanner planner = new MealPlanner();
        final SeedResult r = new SeedResult();
        for (final Customer c : customers) {
            final Restrictions restrictions = Restrictions.parse(c.restrictionsText());
            r.plans++;

            // Every approach chooses from the same safe dishes.
            final List<Food> safe = new ArrayList<>();
            for (final Food f : menu) {
                if (restrictions.violatedBy(f) == null && c.rules().avoid().stream()
                        .noneMatch(a -> f.getFoodName().toLowerCase(Locale.ROOT).contains(a))) {
                    safe.add(f);
                }
            }
            final Map<String, List<Option>> byMeal = new HashMap<>();
            for (final String m : MEALS) {
                byMeal.put(m, options(safe, m));
            }
            // Day by day ignores money; the customer's budget is a share of what it spends.
            final BaselinePlan day = dayByDay(safe, c);
            final int budget = (int) Math.round(day.spend() * c.budgetFactor());

            // The planner.
            final MealPlanner.Plan plan = planner.plan(new MealPlanner.Input(menu, restrictions, START, DAYS,
                    c.target(), budget, c.rules(), null, null));
            final int[] plannedDays = new int[DAYS];
            final Map<Long, Integer> mainUses = new HashMap<>();
            final Set<Long> distinct = new LinkedHashSet<>();
            for (int d = 0; d < DAYS; d++) {
                final MealPlanner.PlannedDay pd = plan.days().get(d);
                plannedDays[d] = pd.calories();
                final List<Food> dayFoods = pd.meals().stream().flatMap(m -> m.foods().stream()).toList();
                if (dayFoods.stream().filter(DishRoles::isDessert).count() > 1
                        || new java.util.HashSet<>(dayFoods).size() < dayFoods.size()) {
                    r.ruleBreakDays++;
                }
                for (final MealPlanner.PlannedMeal meal : pd.meals()) {
                    for (int i = 0; i < meal.foods().size(); i++) {
                        final Food f = meal.foods().get(i);
                        distinct.add(f.getId());
                        if (M0TraceEval.declaresAny(f, c.trueAllergens())) {
                            r.unsafe++;
                        }
                        if (i == 0) {
                            mainUses.merge(f.getId(), 1, Integer::sum);
                        }
                    }
                }
            }
            r.distinctDishes += distinct.size();
            if (capFeasible(safe, c.rules())) {
                r.capFeasiblePlans++;
                for (final int uses : mainUses.values()) {
                    r.repeatViolations += uses > c.rules().maxRepeats() ? 1 : 0;
                }
            }
            if (plan.cheapestPossible() <= budget) {
                r.feasiblePlans++;
                r.overBudgetFeasible += plan.totalCost() > budget ? 1 : 0;
            }
            for (final PlanRules.Pin pin : c.rules().pins()) {
                final boolean safeExists = safe.stream()
                        .anyMatch(f -> f.getFoodName().toLowerCase(Locale.ROOT).contains(pin.dish())
                                && (DishRoles.canBeMain(f, MealType.DINNER) || DishRoles.canBeMain(f, MealType.LUNCH)
                                        || DishRoles.canBeSide(f, MealType.DINNER)
                                        || DishRoles.canBeSide(f, MealType.LUNCH)));
                if (!safeExists) {
                    continue;
                }
                r.safePins++;
                final MealPlanner.PlannedDay pd = plan.days().stream()
                        .filter(x -> x.date().getDayOfWeek() == pin.day()).findFirst().orElseThrow();
                final boolean honored = pd.meals().stream().flatMap(m -> m.foods().stream())
                        .anyMatch(f -> f.getFoodName().toLowerCase(Locale.ROOT).contains(pin.dish()));
                r.pinsUnhonored += honored ? 0 : 1;
            }
            record(r, Approach.PLANNER, plannedDays, c, plan.totalCost(), budget);
            record(r, Approach.DAY_BY_DAY, day.dayCalories(), c, day.spend(), budget);
            final BaselinePlan greedy = greedy(byMeal, c, budget);
            record(r, Approach.GREEDY, greedy.dayCalories(), c, greedy.spend(), budget);
            final BaselinePlan random = random(byMeal, c, rng);
            record(r, Approach.RANDOM, random.dayCalories(), c, random.spend(), budget);

            // Stress test: the originally pre-registered tight budgets.
            final MealPlanner.Plan tight = planner.plan(new MealPlanner.Input(menu, restrictions, START, DAYS,
                    c.target(), c.tightBudget(), c.rules(), null, null));
            final int[] tightDays = new int[DAYS];
            for (int d = 0; d < DAYS; d++) {
                tightDays[d] = tight.days().get(d).calories();
            }
            stress(r, Approach.PLANNER, tightDays, c);
            stress(r, Approach.DAY_BY_DAY, day.dayCalories(), c);
            stress(r, Approach.GREEDY, greedy(byMeal, c, c.tightBudget()).dayCalories(), c);
            stress(r, Approach.RANDOM, random.dayCalories(), c);
        }
        return r;
    }

    /**
     * Hall's condition: a week within the repeat cap exists if, for every set of
     * meal types, the distinct mains usable for any of them, times the cap, cover
     * the number of those meals in the week.
     */
    static boolean capFeasible(final List<Food> safe, final PlanRules rules) {
        final List<String> meals = meals(rules);
        for (int mask = 1; mask < 1 << meals.size(); mask++) {
            final Set<Long> mains = new LinkedHashSet<>();
            int slots = 0;
            for (int k = 0; k < meals.size(); k++) {
                if ((mask & 1 << k) != 0) {
                    slots += DAYS;
                    for (final Food f : safe) {
                        if (DishRoles.canBeMain(f, meals.get(k))) {
                            mains.add(f.getId());
                        }
                    }
                }
            }
            if (mains.size() * rules.maxRepeats() < slots) {
                return false;
            }
        }
        return true;
    }

    static void stress(final SeedResult r, final Approach a, final int[] dayCalories, final Customer c) {
        for (final int cal : dayCalories) {
            r.stress.get(a)[0] += Math.abs(cal - c.target()) <= MealPlanner.HIT_TOLERANCE * c.target() ? 1 : 0;
            r.stress.get(a)[1]++;
        }
    }

    static void record(final SeedResult r, final Approach a, final int[] dayCalories, final Customer c,
            final int spend, final int budget) {
        for (final int cal : dayCalories) {
            final double err = Math.abs(cal - c.target()) / (double) c.target();
            r.hits.get(a)[0] += err <= MealPlanner.HIT_TOLERANCE ? 1 : 0;
            r.hits.get(a)[1]++;
            r.calErr.get(a)[0] += Math.abs(cal - c.target());
            r.calErr.get(a)[1]++;
        }
        r.spend.get(a)[0] += spend;
        r.spend.get(a)[1]++;
        r.spend.get(a)[2] += spend > budget ? 1 : 0;
    }

    // ------------------------------------------------------------- verdict

    record Verdict(double planner, Approach bestApproach, double best, double delta, int unsafe, int overBudget,
            int repeats, int pinsMissed) {
        boolean pass() {
            return planner >= 0.80 && planner - best >= 0.20 && delta >= 0.33 && unsafe == 0 && overBudget == 0
                    && repeats == 0 && pinsMissed == 0;
        }
    }

    static double pooled(final List<SeedResult> results, final Approach a) {
        long hit = 0;
        long days = 0;
        for (final SeedResult r : results) {
            hit += r.hits.get(a)[0];
            days += r.hits.get(a)[1];
        }
        return days == 0 ? 0 : hit / (double) days;
    }

    static double[] perSeed(final List<SeedResult> results, final Approach a) {
        final double[] out = new double[results.size()];
        for (int i = 0; i < results.size(); i++) {
            out[i] = results.get(i).hitRate(a);
        }
        return out;
    }

    static Verdict verdict(final List<SeedResult> results) {
        Approach best = null;
        double bestRate = -1;
        for (final Approach a : List.of(Approach.DAY_BY_DAY, Approach.GREEDY, Approach.RANDOM)) {
            final double rate = pooled(results, a);
            if (rate > bestRate) {
                bestRate = rate;
                best = a;
            }
        }
        int unsafe = 0;
        int over = 0;
        int repeats = 0;
        int pins = 0;
        for (final SeedResult r : results) {
            unsafe += r.unsafe;
            over += r.overBudgetFeasible;
            repeats += r.repeatViolations;
            pins += r.pinsUnhonored;
        }
        return new Verdict(pooled(results, Approach.PLANNER), best, bestRate,
                EvalStats.cliffsDelta(perSeed(results, Approach.PLANNER), perSeed(results, best)), unsafe, over,
                repeats, pins);
    }

    static String report(final List<SeedResult> results, final long[] seeds, final boolean dev) {
        final Verdict v = verdict(results);
        int plans = 0;
        int feasible = 0;
        int safePins = 0;
        long distinct = 0;
        for (final SeedResult r : results) {
            plans += r.plans;
            feasible += r.feasiblePlans;
            safePins += r.safePins;
            distinct += r.distinctDishes;
        }
        final StringBuilder b = new StringBuilder();
        b.append("# M0 meal planner eval").append(dev ? " (DEVELOPMENT SEEDS — not the result)" : "").append("\n\n");
        b.append(String.format(Locale.ROOT, "**%s.** Seeds %d–%d, %d weekly plans (%d customers per seed, %d days).%n%n",
                v.pass() ? "PASS" : "FAIL", seeds[0], seeds[seeds.length - 1], plans, CUSTOMERS, DAYS));
        b.append("| Approach | Days within ±10% | Mean daily calorie error | Mean weekly spend | Plans over budget | per-seed median hit rate |\n");
        b.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (final Approach a : Approach.values()) {
            double err = 0;
            double errDays = 0;
            long spend = 0;
            long n = 0;
            long over = 0;
            for (final SeedResult r : results) {
                err += r.calErr.get(a)[0];
                errDays += r.calErr.get(a)[1];
                spend += r.spend.get(a)[0];
                n += r.spend.get(a)[1];
                over += r.spend.get(a)[2];
            }
            b.append(String.format(Locale.ROOT, "| %s | %.1f%% | %.0f kcal | $%.0f | %.1f%% | %.1f%% |%n", a.label,
                    100 * pooled(results, a), err / errDays, spend / (double) n, 100.0 * over / n,
                    100 * EvalStats.median(perSeed(results, a))));
        }
        b.append("\n## Pass rule\n\n| Check | Needed | Result | |\n| --- | --- | --- | --- |\n");
        b.append(String.format(Locale.ROOT, "| Planner days within ±10%% | ≥ 80%% | %.1f%% | %s |%n", 100 * v.planner(),
                v.planner() >= 0.80 ? "✅" : "❌"));
        b.append(String.format(Locale.ROOT, "| Points above best baseline (%s) | ≥ 20 | %.1f (%.1f%% vs %.1f%%) | %s |%n",
                v.bestApproach().label, 100 * (v.planner() - v.best()), 100 * v.planner(), 100 * v.best(),
                v.planner() - v.best() >= 0.20 ? "✅" : "❌"));
        b.append(String.format(Locale.ROOT, "| Cliff's δ, per-seed hit rate | ≥ 0.33 | %.2f (%s) | %s |%n", v.delta(),
                EvalStats.cliffsMagnitude(v.delta()), v.delta() >= 0.33 ? "✅" : "❌"));
        b.append(String.format(Locale.ROOT, "| Dishes with a true allergen | 0 | %d | %s |%n", v.unsafe(),
                v.unsafe() == 0 ? "✅" : "❌"));
        b.append(String.format(Locale.ROOT, "| Feasible plans over budget | 0 | %d of %d feasible | %s |%n",
                v.overBudget(), feasible, v.overBudget() == 0 ? "✅" : "❌"));
        int capFeasible = 0;
        for (final SeedResult r : results) {
            capFeasible += r.capFeasiblePlans;
        }
        b.append(String.format(Locale.ROOT, "| Main dishes over the repeat cap | 0 | %d (in %d plans where the cap is possible) | %s |%n",
                v.repeats(), capFeasible, v.repeats() == 0 ? "✅" : "❌"));
        b.append(String.format(Locale.ROOT, "| Safe pins not honored | 0 | %d of %d | %s |%n", v.pinsMissed(),
                safePins, v.pinsMissed() == 0 ? "✅" : "❌"));
        int ruleBreaks = 0;
        for (final SeedResult r : results) {
            ruleBreaks += r.ruleBreakDays;
        }
        b.append(String.format(Locale.ROOT, "%nPlanner variety: %.1f distinct dishes per week on average. "
                + "Days with two desserts or a dish twice: %d of %d. Baselines follow the same day rules.%n",
                distinct / (double) plans, ruleBreaks, plans * DAYS));
        b.append("\n## Stress test: original $140–$320 budgets (reported, not part of the pass rule)\n\n");
        b.append("| Approach | Days within ±10% |\n| --- | ---: |\n");
        for (final Approach a : Approach.values()) {
            long hit = 0;
            long days = 0;
            for (final SeedResult r : results) {
                hit += r.stress.get(a)[0];
                days += r.stress.get(a)[1];
            }
            b.append(String.format(Locale.ROOT, "| %s%s | %.1f%% |%n", a.label,
                    a == Approach.DAY_BY_DAY || a == Approach.RANDOM ? " (ignores money)" : "", 100.0 * hit / days));
        }
        return b.toString();
    }
}
