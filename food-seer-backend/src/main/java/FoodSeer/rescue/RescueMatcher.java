package FoodSeer.rescue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import FoodSeer.service.AllergenMatcher;

/**
 * Decides which customers to alert about one surplus listing.
 *
 * <p>
 * Pure and deterministic: the result depends only on the arguments. It reads no
 * clock, database, or random source, and ties are broken by username, so the
 * same input always gives the same output.
 * </p>
 *
 * <p>
 * Hard filters run first and are never traded off against the score (see
 * {@link Exclusion} for the order). The allergy check is
 * {@link AllergenMatcher#restrictionsAffectedBy}, the same check the
 * marketplace applies when a customer reserves.
 * </p>
 */
public final class RescueMatcher {

    private RescueMatcher() {
        // static entry point only
    }

    /**
     * Runs the filters and ranks whoever is left.
     *
     * @param listing the surplus listing
     * @param candidates the customers to consider
     * @param knownAllergens every allergen name FoodSeer knows; a saved restriction
     *        that matches none of them counts as unrecognized
     * @param now the current time
     * @param policy limits and weights
     * @return ranked and excluded customers plus the alert budget
     */
    public static MatchResult match(final RescueListing listing, final Collection<RescueCandidate> candidates,
            final Collection<String> knownAllergens, final Instant now, final RescuePolicy policy) {
        final List<RankedCandidate> ranked = new ArrayList<>();
        final Map<String, Exclusion> excluded = new LinkedHashMap<>();
        final boolean declaresAllergens = declaresAnyAllergen(listing);
        final long minutesLeft = listing.expiresAt() == null ? 0
                : Math.max(0, Duration.between(now, listing.expiresAt()).toMinutes());

        for (final RescueCandidate candidate : candidates) {
            final Exclusion reason = firstExclusion(listing, candidate, knownAllergens, declaresAllergens, now,
                    policy);
            if (reason != null) {
                excluded.put(candidate.username(), reason);
            } else {
                ranked.add(rank(listing, candidate, now, minutesLeft, policy));
            }
        }

        ranked.sort(Comparator.comparingDouble((RankedCandidate r) -> r.parts().total()).reversed()
                .thenComparing(RankedCandidate::username));
        return new MatchResult(ranked, excluded, policy.alertBudget(listing.remainingPortions()));
    }

    /**
     * @return the first filter that excludes the candidate, or null if none does
     */
    static Exclusion firstExclusion(final RescueListing listing, final RescueCandidate candidate,
            final Collection<String> knownAllergens, final boolean declaresAllergens, final Instant now,
            final RescuePolicy policy) {
        if (listing.remainingPortions() <= 0 || listing.expiresAt() == null
                || now.plus(policy.minLead()).isAfter(listing.expiresAt())) {
            return Exclusion.TIME;
        }
        for (final String allergen : listing.allergens()) {
            if (AllergenMatcher.restrictionsAffectedBy(candidate.dietaryRestrictions(), allergen)) {
                return Exclusion.ALLERGEN;
            }
        }
        if (declaresAllergens && hasUnrecognizedRestriction(candidate.dietaryRestrictions(), knownAllergens)) {
            return Exclusion.UNCERTAIN_RESTRICTION;
        }
        if (candidate.remainingCalories() != null
                && (listing.calories() == null || listing.calories() > candidate.remainingCalories())) {
            return Exclusion.CALORIES;
        }
        if (!PriceCeiling.allows(candidate.costPreference(), listing.discountedPrice())) {
            return Exclusion.PRICE;
        }
        if (candidate.alreadyAlerted()) {
            return Exclusion.ALREADY_ALERTED;
        }
        if (candidate.alertsToday() >= policy.dailyCap()) {
            return Exclusion.DAILY_CAP;
        }
        return null;
    }

    /**
     * A restriction is recognized when it overlaps at least one known allergen
     * under {@link AllergenMatcher}'s synonym and group tables. "almonds" is
     * recognized through TREE-NUTS; "soy sauce" and "egg whites" are not.
     *
     * @param restrictionsText saved restrictions, may be null
     * @param knownAllergens allergen names FoodSeer knows
     * @return true if any saved restriction matches no known allergen
     */
    static boolean hasUnrecognizedRestriction(final String restrictionsText,
            final Collection<String> knownAllergens) {
        for (final String term : AllergenMatcher.parseTerms(restrictionsText)) {
            boolean recognized = false;
            for (final String known : knownAllergens) {
                if (AllergenMatcher.restrictionsAffectedBy(term, known)) {
                    recognized = true;
                    break;
                }
            }
            if (!recognized) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaresAnyAllergen(final RescueListing listing) {
        for (final String tag : listing.allergens()) {
            if (!AllergenMatcher.normalizeTerm(tag).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static RankedCandidate rank(final RescueListing listing, final RescueCandidate candidate,
            final Instant now, final long minutesLeft, final RescuePolicy policy) {
        final int pastOrders = Math.max(0, candidate.pastOrdersOfDish());
        final double affinity = Math.min(pastOrders, policy.affinityCap()) / (double) policy.affinityCap();
        final double activity = activity(candidate.lastOrderAt(), now, policy.activityHorizon());
        final double discount = discountShare(listing.originalPrice(), listing.discountedPrice());
        final double urgency = clamp(1.0 - minutesLeft / (double) policy.urgencyHorizon().toMinutes());
        final double total = policy.affinityWeight() * affinity + policy.activityWeight() * activity
                + policy.discountWeight() * discount + policy.urgencyWeight() * urgency;
        final ScoreParts parts = new ScoreParts(affinity, activity, discount, urgency, total);
        final int discountPercent = (int) Math.round(discount * 100);
        return new RankedCandidate(candidate.username(), parts, pastOrders, discountPercent, minutesLeft,
                RescueReasons.describe(pastOrders, discountPercent, minutesLeft));
    }

    private static double activity(final Instant lastOrderAt, final Instant now, final Duration horizon) {
        if (lastOrderAt == null) {
            return 0.0;
        }
        final long age = Math.max(0, Duration.between(lastOrderAt, now).toMinutes());
        return clamp(1.0 - age / (double) horizon.toMinutes());
    }

    static double discountShare(final BigDecimal original, final BigDecimal discounted) {
        if (original == null || discounted == null || original.signum() <= 0) {
            return 0.0;
        }
        final BigDecimal off = original.subtract(discounted).divide(original, 4, RoundingMode.HALF_UP);
        return clamp(off.doubleValue());
    }

    private static double clamp(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
