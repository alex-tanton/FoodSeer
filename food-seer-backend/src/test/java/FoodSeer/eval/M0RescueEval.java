package FoodSeer.eval;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import FoodSeer.rescue.MatchResult;
import FoodSeer.rescue.PriceCeiling;
import FoodSeer.rescue.RankedCandidate;
import FoodSeer.rescue.RescueCandidate;
import FoodSeer.rescue.RescueListing;
import FoodSeer.rescue.RescueMatcher;
import FoodSeer.rescue.RescuePolicy;
import FoodSeer.service.AllergenMatcher;

/**
 * M0, the risk-killer eval for surplus rescue matching.
 *
 * <p>
 * <b>Claim</b> (fixed in docs/p3/M0_RESCUE.md before this eval existed). Rescue
 * matching sells at least 20% more surplus portions before the booking deadline
 * than the best equal-budget baseline, with at most 3.0 alerts per portion sold,
 * 0 allergen-unsafe alerts in every run, and Cliff's delta of at least 0.33 on
 * per-seed sell-through against that baseline.
 * </p>
 *
 * <p>
 * <b>Simulation.</b> There are no real users, so each seed builds a world: the
 * real FoodSeer seed menu, customers with true allergies (same prevalence and
 * the same free-text phrase lists as {@link M0TraceEval}), cost preferences,
 * optional calorie budgets, a hidden taste for each dish and a hidden
 * engagement level. Order history is sampled from the hidden taste and
 * engagement, so history is only a noisy view of them. Then surplus listings
 * arrive over several days.
 * </p>
 *
 * <p>
 * Every approach sees the same world and the same random draws for every
 * (listing, customer) pair, so differences come from who is alerted, not luck.
 * An alerted customer reserves with a probability that rises with hidden taste,
 * engagement and discount, and falls with each alert already received that day.
 * Nobody reserves food they are truly allergic to, cannot afford, or have no
 * calories left for. Customers who were not alerted still find a listing by
 * browsing at a small fixed rate. Portions go first come, first served.
 * </p>
 *
 * <p>
 * Run with {@code mvn -Dtest=M0RescueEval -Dsurefire.failIfNoSpecifiedTests=false test}.
 * Add {@code -Drescue.eval.seeds=dev} to use the development seeds (101 to 110),
 * the only seeds used while building the matcher and this simulator. The report
 * goes to target/m0-eval/m0-rescue-report.md. Like {@link M0TraceEval}, the class
 * name does not end in "Test", so the normal build skips it; its own workflow runs it.
 * </p>
 */
public class M0RescueEval {

    // The claim, fixed before any scored run.
    static final double MIN_IMPROVEMENT = 0.20;
    static final double MAX_ALERTS_PER_SALE = 3.0;
    static final double MIN_CLIFFS_DELTA = 0.33;

    static final long[] SCORED_SEEDS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    static final long[] DEV_SEEDS = {101, 102, 103, 104, 105, 106, 107, 108, 109, 110};

    // The simulated world. Committed before the first scored run.
    static final int CUSTOMERS = 400;
    static final int DAYS = 14;
    static final int LISTINGS_PER_DAY = 5;
    static final int HISTORY_DAYS = 60;
    static final double DAILY_ORDER_RATE = 0.30;
    static final double AVOID_DECLARED_ALLERGEN = 0.95;
    static final double CALORIE_TRACKER_SHARE = 0.40;
    static final int MIN_DAILY_CALORIES = 100;
    static final int MAX_DAILY_CALORIES = 1500;
    static final double[] COST_PREFERENCE_SHARE = {0.30, 0.35, 0.15, 0.20};
    static final String[] COST_PREFERENCE = {"budget", "moderate", "premium", "no-limit"};
    static final double BROWSE_RATE = 0.02;
    static final double BASE_BUY = 0.02;
    static final double TASTE_WEIGHT = 0.60;
    static final double MAX_BUY = 0.95;
    static final double FATIGUE = 0.70;
    static final double TASTE_SKEW = 4.0;
    static final double ENGAGEMENT_SKEW = 1.5;
    static final Instant START = Instant.parse("2026-11-02T00:00:00Z");

    /** Seed menu prices (dollars) and calories, in M0TraceEval.MENU order, from DataInitializer. */
    static final int[] MENU_PRICE = {3, 2, 4, 1, 2, 5, 4, 3, 12, 10, 11, 14, 8, 11, 9, 10, 13, 10, 28, 24, 35, 32,
        30, 11, 12, 13, 9, 7, 6, 5, 8};
    static final int[] MENU_CALORIES = {5, 2, 250, 105, 95, 110, 150, 190, 450, 120, 470, 600, 285, 650, 110, 180,
        255, 450, 679, 412, 320, 500, 720, 390, 380, 350, 420, 450, 270, 160, 130};

    /** The allergen vocabulary DataInitializer seeds. */
    static final List<String> KNOWN_ALLERGENS = List.of("MILK", "LACTOSE", "EGGS", "FISH", "SHELLFISH",
            "TREE-NUTS", "PEANUTS", "WHEAT", "GLUTEN", "SOY", "SESAME", "CORN", "SULFITES", "MUSTARD", "MEAT",
            "BEEF", "PORK", "POULTRY", "GELATIN", "CAFFEINE", "DAIRY");

    /** The approaches compared. */
    enum Approach {
        MATCHER("Rescue matching (this feature)"),
        EVERYONE("Notify everyone"),
        RANDOM_K("Random K (hard filters)"),
        RECENT_K("Most recent K (hard filters)");

        final String label;

        Approach(final String label) {
            this.label = label;
        }
    }

    /** A simulated customer: what FoodSeer sees, plus the truth it does not. */
    static final class Customer {
        final String username;
        final String savedRestrictions;
        final String costPreference;
        final boolean tracksCalories;
        final Set<Integer> trueAllergens;
        final double[] taste;
        final double engagement;
        final int[] pastOrders;
        long lastOrderMinute;

        Customer(final String username, final String savedRestrictions, final String costPreference,
                final boolean tracksCalories, final Set<Integer> trueAllergens, final double[] taste,
                final double engagement, final int[] pastOrders, final long lastOrderMinute) {
            this.username = username;
            this.savedRestrictions = savedRestrictions;
            this.costPreference = costPreference;
            this.tracksCalories = tracksCalories;
            this.trueAllergens = trueAllergens;
            this.taste = taste;
            this.engagement = engagement;
            this.pastOrders = pastOrders;
            this.lastOrderMinute = lastOrderMinute;
        }

        Customer copy() {
            return new Customer(username, savedRestrictions, costPreference, tracksCalories, trueAllergens, taste,
                    engagement, pastOrders.clone(), lastOrderMinute);
        }
    }

    /** One surplus listing and the random draws every approach shares for it. */
    static final class Listing {
        final int day;
        final long postedMinute;
        final int dish;
        final double discount;
        final BigDecimal price;
        final int portions;
        final int windowMinutes;
        final double[] buyDraw;
        final double[] browseDraw;
        final double[] arrival;

        Listing(final int day, final long postedMinute, final int dish, final double discount,
                final BigDecimal price, final int portions, final int windowMinutes, final double[] buyDraw,
                final double[] browseDraw, final double[] arrival) {
            this.day = day;
            this.postedMinute = postedMinute;
            this.dish = dish;
            this.discount = discount;
            this.price = price;
            this.portions = portions;
            this.windowMinutes = windowMinutes;
            this.buyDraw = buyDraw;
            this.browseDraw = browseDraw;
            this.arrival = arrival;
        }
    }

    /** Everything one seed builds before any approach runs. */
    static final class World {
        final long seed;
        final List<Customer> customers;
        final List<Listing> listings;
        final int[][] dailyCalories;
        final boolean[][] unsafe;

        World(final long seed, final List<Customer> customers, final List<Listing> listings,
                final int[][] dailyCalories, final boolean[][] unsafe) {
            this.seed = seed;
            this.customers = customers;
            this.listings = listings;
            this.dailyCalories = dailyCalories;
            this.unsafe = unsafe;
        }
    }

    /** One approach's totals for one seed. */
    static final class Outcome {
        long listed;
        long sold;
        long alerts;
        long unsafeAlerts;

        double sellThrough() {
            return listed == 0 ? 0.0 : (double) sold / listed;
        }

        double alertsPerSale() {
            return sold == 0 ? Double.POSITIVE_INFINITY : (double) alerts / sold;
        }
    }

    /** Per-seed outcomes for every approach. */
    static final class Results {
        final long[] seeds;
        final Map<Approach, List<Outcome>> byApproach = new EnumMap<>(Approach.class);

        Results(final long[] seeds) {
            this.seeds = seeds;
            for (final Approach approach : Approach.values()) {
                byApproach.put(approach, new ArrayList<>());
            }
        }

        double[] sellThrough(final Approach approach) {
            return byApproach.get(approach).stream().mapToDouble(Outcome::sellThrough).toArray();
        }

        double[] alertsPerSale(final Approach approach) {
            return byApproach.get(approach).stream().mapToDouble(Outcome::alertsPerSale).toArray();
        }

        long unsafeTotal(final Approach approach) {
            return byApproach.get(approach).stream().mapToLong(o -> o.unsafeAlerts).sum();
        }

        boolean unsafeInAnySeed(final Approach approach) {
            return byApproach.get(approach).stream().anyMatch(o -> o.unsafeAlerts > 0);
        }

        Approach bestEqualBudgetBaseline() {
            final double random = EvalStats.median(sellThrough(Approach.RANDOM_K));
            final double recent = EvalStats.median(sellThrough(Approach.RECENT_K));
            return recent >= random ? Approach.RECENT_K : Approach.RANDOM_K;
        }
    }

    // The eval

    @Test
    public void rescueMatchingMeetsTheM0Claim() throws IOException {
        final boolean dev = "dev".equals(System.getProperty("rescue.eval.seeds"));
        final Results results = runSeeds(dev ? DEV_SEEDS : SCORED_SEEDS);
        final String report = buildReport(results, dev);
        final Path reportPath = writeReport(report);
        System.out.println(report);
        System.out.println("M0 rescue report written to " + reportPath.toAbsolutePath());

        final Approach best = results.bestEqualBudgetBaseline();
        final double ours = EvalStats.median(results.sellThrough(Approach.MATCHER));
        final double theirs = EvalStats.median(results.sellThrough(best));
        final double improvement = theirs == 0 ? Double.POSITIVE_INFINITY : ours / theirs - 1.0;
        final double alertsPerSale = EvalStats.median(results.alertsPerSale(Approach.MATCHER));
        final double delta = EvalStats.cliffsDelta(results.sellThrough(Approach.MATCHER), results.sellThrough(best));

        assertTrue(!results.unsafeInAnySeed(Approach.MATCHER), "M0 claim failed: rescue matching sent "
                + results.unsafeTotal(Approach.MATCHER) + " unsafe alerts. See " + reportPath);
        assertTrue(improvement >= MIN_IMPROVEMENT, String.format(Locale.ROOT,
                "M0 claim failed: sell-through improvement %.1f%% over %s is below %.0f%%. See %s",
                improvement * 100, best.label, MIN_IMPROVEMENT * 100, reportPath));
        assertTrue(alertsPerSale <= MAX_ALERTS_PER_SALE, String.format(Locale.ROOT,
                "M0 claim failed: %.2f alerts per sale is above %.1f. See %s", alertsPerSale, MAX_ALERTS_PER_SALE,
                reportPath));
        assertTrue(delta >= MIN_CLIFFS_DELTA, String.format(Locale.ROOT,
                "M0 claim failed: Cliff's delta %.3f is below %.2f. See %s", delta, MIN_CLIFFS_DELTA, reportPath));
    }

    @Test
    public void sameSeedGivesSameNumbers() {
        final Results first = runSeeds(new long[] {DEV_SEEDS[0]});
        final Results second = runSeeds(new long[] {DEV_SEEDS[0]});
        for (final Approach approach : Approach.values()) {
            final Outcome a = first.byApproach.get(approach).get(0);
            final Outcome b = second.byApproach.get(approach).get(0);
            assertEquals(a.sold, b.sold, approach.label);
            assertEquals(a.alerts, b.alerts, approach.label);
            assertEquals(a.unsafeAlerts, b.unsafeAlerts, approach.label);
        }
    }

    @Test
    public void menuMatchesTheTraceEvalMenu() {
        assertEquals(M0TraceEval.MENU.length, MENU_PRICE.length);
        assertEquals(M0TraceEval.MENU.length, MENU_CALORIES.length);
        assertEquals("COFFEE", M0TraceEval.MENU[0][0]);
        assertEquals("RIBEYE", M0TraceEval.MENU[22][0]);
        assertEquals(30, MENU_PRICE[22]);
        assertEquals(720, MENU_CALORIES[22]);
    }

    @Test
    public void everyApproachSeesTheSameListings() {
        final Results results = runSeeds(new long[] {DEV_SEEDS[0]});
        final long listed = results.byApproach.get(Approach.MATCHER).get(0).listed;
        assertTrue(listed > 0);
        for (final Approach approach : Approach.values()) {
            assertEquals(listed, results.byApproach.get(approach).get(0).listed, approach.label);
        }
        assertArrayEquals(new long[] {DEV_SEEDS[0]}, results.seeds);
    }

    // Simulation

    static Results runSeeds(final long[] seeds) {
        final Results results = new Results(seeds);
        for (final long seed : seeds) {
            final World world = buildWorld(seed);
            for (final Approach approach : Approach.values()) {
                results.byApproach.get(approach).add(run(world, approach));
            }
        }
        return results;
    }

    static World buildWorld(final long seed) {
        final Random rng = new Random(seed);
        final int dishes = M0TraceEval.MENU.length;

        final boolean[][] declares = new boolean[dishes][M0TraceEval.ALLERGENS.length];
        for (int d = 0; d < dishes; d++) {
            final List<String> tags = tags(d);
            for (int a = 0; a < M0TraceEval.ALLERGENS.length; a++) {
                declares[d][a] = AllergenMatcher.foodDeclaresAllergen(tags, M0TraceEval.ALLERGENS[a]);
            }
        }

        final List<Customer> customers = new ArrayList<>();
        final boolean[][] unsafe = new boolean[CUSTOMERS][dishes];
        for (int c = 0; c < CUSTOMERS; c++) {
            final Set<Integer> allergies = new LinkedHashSet<>();
            for (int a = 0; a < M0TraceEval.ALLERGENS.length; a++) {
                if (rng.nextDouble() < M0TraceEval.PREVALENCE[a]) {
                    allergies.add(a);
                }
            }
            final boolean freeText = rng.nextDouble() < M0TraceEval.FREE_TEXT_SHARE;
            final List<String> saved = new ArrayList<>();
            for (final int a : allergies) {
                if (freeText) {
                    final String[] phrases = M0TraceEval.FREE_TEXT[a];
                    saved.add(phrases[rng.nextInt(phrases.length)]);
                } else {
                    saved.add(M0TraceEval.CHECKBOX_VALUE[a]);
                }
            }
            final String costPreference = COST_PREFERENCE[pick(rng, COST_PREFERENCE_SHARE)];
            final boolean tracksCalories = rng.nextDouble() < CALORIE_TRACKER_SHARE;
            final double engagement = Math.pow(rng.nextDouble(), ENGAGEMENT_SKEW);

            final double[] taste = new double[dishes];
            double total = 0;
            for (int d = 0; d < dishes; d++) {
                double t = Math.pow(rng.nextDouble(), TASTE_SKEW);
                for (final int a : allergies) {
                    if (declares[d][a]) {
                        unsafe[c][d] = true;
                    }
                }
                if (unsafe[c][d] && rng.nextDouble() < AVOID_DECLARED_ALLERGEN) {
                    t = 0.0;
                }
                taste[d] = t;
                total += t;
            }
            for (int d = 0; d < dishes; d++) {
                taste[d] = total == 0 ? 1.0 / dishes : taste[d] / total;
            }

            final int[] pastOrders = new int[dishes];
            long lastOrder = Long.MIN_VALUE;
            for (int day = -HISTORY_DAYS; day < 0; day++) {
                if (rng.nextDouble() < DAILY_ORDER_RATE * engagement) {
                    pastOrders[sample(rng, taste)]++;
                    lastOrder = day * 1440L + 720 + rng.nextInt(600);
                }
            }
            customers.add(new Customer(String.format(Locale.ROOT, "c%03d", c),
                    saved.isEmpty() ? null : String.join(", ", saved), costPreference, tracksCalories, allergies,
                    taste, engagement, pastOrders, lastOrder));
        }

        final int[][] dailyCalories = new int[DAYS][CUSTOMERS];
        for (int day = 0; day < DAYS; day++) {
            for (int c = 0; c < CUSTOMERS; c++) {
                dailyCalories[day][c] = MIN_DAILY_CALORIES + rng.nextInt(MAX_DAILY_CALORIES - MIN_DAILY_CALORIES + 1);
            }
        }

        final List<Listing> listings = new ArrayList<>();
        for (int day = 0; day < DAYS; day++) {
            final List<Long> times = new ArrayList<>();
            for (int i = 0; i < LISTINGS_PER_DAY; i++) {
                times.add(day * 1440L + 11 * 60 + rng.nextInt(10 * 60));
            }
            Collections.sort(times);
            for (final long posted : times) {
                final int dish = rng.nextInt(dishes);
                final double discount = 0.30 + 0.40 * rng.nextDouble();
                final BigDecimal price = BigDecimal.valueOf(MENU_PRICE[dish])
                        .multiply(BigDecimal.valueOf(1.0 - discount)).setScale(2, RoundingMode.HALF_UP);
                final int portions = 2 + rng.nextInt(9);
                final int window = 30 + rng.nextInt(151);
                final double[] buy = new double[CUSTOMERS];
                final double[] browse = new double[CUSTOMERS];
                final double[] arrival = new double[CUSTOMERS];
                for (int c = 0; c < CUSTOMERS; c++) {
                    buy[c] = rng.nextDouble();
                    browse[c] = rng.nextDouble();
                    arrival[c] = rng.nextDouble();
                }
                listings.add(new Listing(day, posted, dish, discount, price, portions, window, buy, browse, arrival));
            }
        }
        return new World(seed, customers, listings, dailyCalories, unsafe);
    }

    static Outcome run(final World world, final Approach approach) {
        final List<Customer> customers = new ArrayList<>();
        for (final Customer customer : world.customers) {
            customers.add(customer.copy());
        }
        final int[][] calories = new int[DAYS][];
        for (int day = 0; day < DAYS; day++) {
            calories[day] = world.dailyCalories[day].clone();
        }
        final int[] alertsToday = new int[CUSTOMERS];
        int currentDay = -1;
        final Outcome outcome = new Outcome();

        for (int i = 0; i < world.listings.size(); i++) {
            final Listing listing = world.listings.get(i);
            if (listing.day != currentDay) {
                currentDay = listing.day;
                Arrays.fill(alertsToday, 0);
            }
            final List<RescueCandidate> candidates = new ArrayList<>();
            for (int c = 0; c < CUSTOMERS; c++) {
                final Customer customer = customers.get(c);
                candidates.add(new RescueCandidate(customer.username, customer.savedRestrictions,
                        customer.costPreference, customer.tracksCalories ? calories[listing.day][c] : null,
                        customer.pastOrders[listing.dish],
                        customer.lastOrderMinute == Long.MIN_VALUE ? null : minute(customer.lastOrderMinute),
                        alertsToday[c], false));
            }
            final Set<Integer> alerted = choose(approach, world.seed, i, listing, candidates, customers);

            final List<int[]> buyers = new ArrayList<>();
            for (int c = 0; c < CUSTOMERS; c++) {
                final boolean isAlerted = alerted.contains(c);
                if (isAlerted) {
                    outcome.alerts++;
                    if (world.unsafe[c][listing.dish]) {
                        outcome.unsafeAlerts++;
                    }
                }
                final boolean sees = isAlerted || listing.browseDraw[c] < BROWSE_RATE;
                final int fatigue = isAlerted ? alertsToday[c] : 0;
                if (isAlerted) {
                    alertsToday[c]++;
                }
                if (sees && wouldBuy(world, customers.get(c), c, listing, calories[listing.day][c], fatigue)) {
                    buyers.add(new int[] {c, (int) Math.round(listing.arrival[c] * 1_000_000)});
                }
            }
            buyers.sort(Comparator.comparingInt((int[] b) -> b[1]).thenComparingInt(b -> b[0]));
            final int sold = Math.min(listing.portions, buyers.size());
            for (int b = 0; b < sold; b++) {
                final int c = buyers.get(b)[0];
                final Customer customer = customers.get(c);
                customer.pastOrders[listing.dish]++;
                customer.lastOrderMinute = listing.postedMinute
                        + Math.round(listing.arrival[c] * listing.windowMinutes);
                calories[listing.day][c] -= MENU_CALORIES[listing.dish];
            }
            outcome.listed += listing.portions;
            outcome.sold += sold;
        }
        return outcome;
    }

    static Set<Integer> choose(final Approach approach, final long seed, final int listingIndex,
            final Listing listing, final List<RescueCandidate> candidates, final List<Customer> customers) {
        final Set<Integer> chosen = new HashSet<>();
        if (approach == Approach.EVERYONE) {
            for (int c = 0; c < CUSTOMERS; c++) {
                chosen.add(c);
            }
            return chosen;
        }
        final RescueListing view = new RescueListing((long) listingIndex, (long) listing.dish,
                M0TraceEval.MENU[listing.dish][0], tags(listing.dish), MENU_CALORIES[listing.dish],
                BigDecimal.valueOf(MENU_PRICE[listing.dish]).setScale(2), listing.price, listing.portions,
                minute(listing.postedMinute + listing.windowMinutes));
        final MatchResult result = RescueMatcher.match(view, candidates, KNOWN_ALLERGENS, minute(listing.postedMinute),
                RescuePolicy.DEFAULT);
        final List<RankedCandidate> eligible = new ArrayList<>(result.ranked());
        final int budget = result.alertBudget();
        if (approach == Approach.RANDOM_K) {
            Collections.shuffle(eligible, new Random(seed * 1_000_003L + listingIndex));
        } else if (approach == Approach.RECENT_K) {
            final Map<String, Long> last = new java.util.HashMap<>();
            for (final Customer customer : customers) {
                last.put(customer.username, customer.lastOrderMinute);
            }
            eligible.sort(Comparator.comparingLong((RankedCandidate r) -> last.get(r.username())).reversed()
                    .thenComparing(RankedCandidate::username));
        }
        for (final RankedCandidate match : eligible.subList(0, Math.min(budget, eligible.size()))) {
            chosen.add(Integer.parseInt(match.username().substring(1)));
        }
        return chosen;
    }

    /**
     * The truth the matcher never sees: would this customer reserve if they saw
     * the listing now, after {@code fatigue} earlier alerts today?
     */
    static boolean wouldBuy(final World world, final Customer customer, final int c, final Listing listing,
            final int caloriesLeft, final int fatigue) {
        if (world.unsafe[c][listing.dish] || !PriceCeiling.allows(customer.costPreference, listing.price)) {
            return false;
        }
        if (customer.tracksCalories && MENU_CALORIES[listing.dish] > caloriesLeft) {
            return false;
        }
        final double dishes = customer.taste.length;
        final double affinity = Math.min(1.0, customer.taste[listing.dish] * dishes / 3.0);
        final double probability = Math.min(MAX_BUY,
                BASE_BUY + TASTE_WEIGHT * affinity * (0.25 + 0.75 * customer.engagement) * (0.5 + listing.discount))
                * Math.pow(FATIGUE, fatigue);
        return listing.buyDraw[c] < probability;
    }

    // Helpers

    static List<String> tags(final int dish) {
        return Arrays.asList(M0TraceEval.MENU[dish]).subList(1, M0TraceEval.MENU[dish].length);
    }

    static Instant minute(final long minute) {
        return START.plusSeconds(minute * 60);
    }

    static int pick(final Random rng, final double[] shares) {
        double u = rng.nextDouble();
        for (int i = 0; i < shares.length; i++) {
            u -= shares[i];
            if (u < 0) {
                return i;
            }
        }
        return shares.length - 1;
    }

    static int sample(final Random rng, final double[] weights) {
        return pick(rng, weights);
    }

    // Report

    static String buildReport(final Results results, final boolean dev) {
        final Approach best = results.bestEqualBudgetBaseline();
        final double ours = EvalStats.median(results.sellThrough(Approach.MATCHER));
        final double theirs = EvalStats.median(results.sellThrough(best));
        final double improvement = theirs == 0 ? Double.POSITIVE_INFINITY : ours / theirs - 1.0;
        final double alertsPerSale = EvalStats.median(results.alertsPerSale(Approach.MATCHER));
        final double delta = EvalStats.cliffsDelta(results.sellThrough(Approach.MATCHER), results.sellThrough(best));
        final boolean safe = !results.unsafeInAnySeed(Approach.MATCHER);
        final boolean pass = safe && improvement >= MIN_IMPROVEMENT && alertsPerSale <= MAX_ALERTS_PER_SALE
                && delta >= MIN_CLIFFS_DELTA;

        final StringBuilder out = new StringBuilder();
        out.append("# M0 eval: surplus rescue matching\n\n");
        out.append(String.format(Locale.ROOT, "Claim: at least %.0f%% more surplus sold than the best equal-budget "
                + "baseline, at most %.1f alerts per sale, 0 unsafe alerts in every run, Cliff's delta >= %.2f.%n%n",
                MIN_IMPROVEMENT * 100, MAX_ALERTS_PER_SALE, MIN_CLIFFS_DELTA));
        out.append("Seeds: ").append(Arrays.toString(results.seeds)).append(dev ? " (development seeds, not scored)"
                : " (scored seeds)").append(". ");
        out.append(String.format(Locale.ROOT, "%d customers, %d days, %d listings per day per seed.%n%n", CUSTOMERS,
                DAYS, LISTINGS_PER_DAY));
        out.append("Result: **").append(pass ? "PASS" : "FAIL").append("**\n\n");
        out.append("| Check | Value | Target | Met |\n| :- | -: | -: | :-: |\n");
        out.append(String.format(Locale.ROOT, "| Sell-through vs %s | %+.1f%% | >= +%.0f%% | %s |%n", best.label,
                improvement * 100, MIN_IMPROVEMENT * 100, yes(improvement >= MIN_IMPROVEMENT)));
        out.append(String.format(Locale.ROOT, "| Alerts per sale (median) | %.2f | <= %.1f | %s |%n", alertsPerSale,
                MAX_ALERTS_PER_SALE, yes(alertsPerSale <= MAX_ALERTS_PER_SALE)));
        out.append(String.format(Locale.ROOT, "| Unsafe alerts (all seeds) | %d | 0 | %s |%n",
                results.unsafeTotal(Approach.MATCHER), yes(safe)));
        out.append(String.format(Locale.ROOT, "| Cliff's delta vs %s | %.3f (%s) | >= %.2f | %s |%n%n", best.label,
                delta, EvalStats.cliffsMagnitude(delta), MIN_CLIFFS_DELTA, yes(delta >= MIN_CLIFFS_DELTA)));

        out.append("Medians over seeds:\n\n");
        out.append("| Approach | Sell-through | Alerts per sale | Unsafe alerts (total) | Cliff's delta vs matcher |\n");
        out.append("| :- | -: | -: | -: | -: |\n");
        for (final Approach approach : Approach.values()) {
            final String deltaText = approach == Approach.MATCHER ? "–" : String.format(Locale.ROOT, "%.3f",
                    EvalStats.cliffsDelta(results.sellThrough(Approach.MATCHER), results.sellThrough(approach)));
            out.append(String.format(Locale.ROOT, "| %s | %.3f | %s | %d | %s |%n", approach.label,
                    EvalStats.median(results.sellThrough(approach)),
                    ratio(EvalStats.median(results.alertsPerSale(approach))), results.unsafeTotal(approach),
                    deltaText));
        }

        out.append("\nPer seed (sell-through, alerts per sale):\n\n| Seed |");
        for (final Approach approach : Approach.values()) {
            out.append(' ').append(approach.label).append(" |");
        }
        out.append("\n| -: |");
        for (int i = 0; i < Approach.values().length; i++) {
            out.append(" -: |");
        }
        out.append('\n');
        for (int s = 0; s < results.seeds.length; s++) {
            out.append("| ").append(results.seeds[s]).append(" |");
            for (final Approach approach : Approach.values()) {
                final Outcome o = results.byApproach.get(approach).get(s);
                out.append(String.format(Locale.ROOT, " %.3f, %s |", o.sellThrough(), ratio(o.alertsPerSale())));
            }
            out.append('\n');
        }
        out.append("\nReproduce with: mvn -Dtest=M0RescueEval -Dsurefire.failIfNoSpecifiedTests=false test\n");
        return out.toString();
    }

    private static String yes(final boolean met) {
        return met ? "yes" : "no";
    }

    private static String ratio(final double value) {
        return Double.isInfinite(value) ? "∞" : String.format(Locale.ROOT, "%.2f", value);
    }

    static Path writeReport(final String report) throws IOException {
        final Path dir = Paths.get("target", "m0-eval");
        Files.createDirectories(dir);
        final Path path = dir.resolve("m0-rescue-report.md");
        Files.write(path, report.getBytes(StandardCharsets.UTF_8));
        return path;
    }
}
