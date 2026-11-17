package FoodSeer.rescue;

import java.time.Duration;

/**
 * The tunable numbers behind rescue matching. Passed into the matcher so tests
 * and the eval can vary them without touching the matcher.
 *
 * @param maxAlertsPerListing never alert more customers than this for one listing
 * @param alertsPerPortion alerts sent per remaining portion, before the maximum applies
 * @param dailyCap rescue alerts one customer may receive per day
 * @param minLead the least time that must remain before the booking deadline
 * @param affinityCap past orders of the dish beyond this count add nothing more
 * @param activityHorizon an order older than this no longer counts as recent activity
 * @param urgencyHorizon deadlines further away than this have zero urgency
 * @param affinityWeight weight of dish affinity in the score
 * @param activityWeight weight of recent activity in the score
 * @param discountWeight weight of the discount in the score
 * @param urgencyWeight weight of urgency in the score
 */
public record RescuePolicy(int maxAlertsPerListing, int alertsPerPortion, int dailyCap, Duration minLead,
        int affinityCap, Duration activityHorizon, Duration urgencyHorizon, double affinityWeight,
        double activityWeight, double discountWeight, double urgencyWeight) {

    /**
     * The policy the application uses. Any tuning of these numbers happens on the
     * eval's dev seeds (101 to 110) only, never on the scored seeds.
     */
    public static final RescuePolicy DEFAULT = new RescuePolicy(20, 2, 2, Duration.ofMinutes(10), 3,
            Duration.ofDays(14), Duration.ofMinutes(180), 0.5, 0.3, 0.1, 0.1);

    public RescuePolicy {
        if (maxAlertsPerListing < 1 || alertsPerPortion < 1 || dailyCap < 1 || affinityCap < 1) {
            throw new IllegalArgumentException("Alert limits and the affinity cap must be at least 1.");
        }
        if (minLead == null || minLead.isNegative() || activityHorizon == null || !activityHorizon.isPositive()
                || urgencyHorizon == null || !urgencyHorizon.isPositive()) {
            throw new IllegalArgumentException("Lead time must be non-negative and horizons positive.");
        }
        if (affinityWeight < 0 || activityWeight < 0 || discountWeight < 0 || urgencyWeight < 0) {
            throw new IllegalArgumentException("Score weights cannot be negative.");
        }
    }

    /**
     * How many customers to alert for a listing with this many portions left.
     *
     * @param remainingPortions portions still available
     * @return the alert budget K, zero when nothing is left
     */
    public int alertBudget(final int remainingPortions) {
        if (remainingPortions <= 0) {
            return 0;
        }
        final long wanted = (long) alertsPerPortion * remainingPortions;
        return (int) Math.min(maxAlertsPerListing, wanted);
    }
}
