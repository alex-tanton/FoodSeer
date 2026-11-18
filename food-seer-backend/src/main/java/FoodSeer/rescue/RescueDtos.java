package FoodSeer.rescue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import FoodSeer.dto.SurplusOfferDto;

/** Response bodies for the rescue endpoints. */
public final class RescueDtos {

    private RescueDtos() {
        // holder for nested records
    }

    /**
     * One ranked customer in a staff preview.
     *
     * @param willAlert true when the customer is inside the alert budget
     */
    public record Match(String username, double score, double affinity, double activity, double discount,
            double urgency, String reason, boolean willAlert) {

        static Match of(final RankedCandidate ranked, final boolean willAlert) {
            final ScoreParts p = ranked.parts();
            return new Match(ranked.username(), p.total(), p.affinity(), p.activity(), p.discount(), p.urgency(),
                    ranked.reason(), willAlert);
        }
    }

    /** One excluded customer in a staff preview. */
    public record Excluded(String username, Exclusion reason, String description) {
    }

    /** What sending would do, or did, for one offer. */
    public record Preview(Long offerId, String foodName, int alertBudget, List<Match> ranked,
            List<Excluded> excluded) {
    }

    /** The outcome of sending alerts for one offer. */
    public record NotifyResult(Long offerId, int alertBudget, List<String> alerted, int excludedCount) {
    }

    /** One rescue deal on the customer's page. */
    public record Deal(Long alertId, SurplusOfferDto offer, String reason, double score, Instant alertedAt,
            boolean reserved) {
    }

    /**
     * Rescue results for one offer.
     *
     * @param alerted customers alerted
     * @param alertedWhoReserved alerted customers with a reservation that was not cancelled
     * @param portionsRescued portions reserved or collected, by anyone
     * @param portionsCollected portions already collected
     * @param revenueRecovered money the restaurant takes in for those portions
     * @param customerSavings what customers saved against the menu price
     */
    public record Stats(Long offerId, String foodName, Instant expiresAt, int remainingPortions, int alerted,
            int alertedWhoReserved, int portionsRescued, int portionsCollected, BigDecimal revenueRecovered,
            BigDecimal customerSavings) {
    }
}
