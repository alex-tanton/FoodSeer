package FoodSeer.rescue;

import static FoodSeer.rescue.RescueFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class RescueMatcherRankingTest {

    private static List<String> order(final MatchResult result) {
        return result.ranked().stream().map(RankedCandidate::username).toList();
    }

    @Test
    void moreOrdersOfTheDishRankHigher() {
        MatchResult result = run(listing(List.of()), withHistory("once", 1, null), withHistory("never", 0, null),
                withHistory("thrice", 3, null));
        assertEquals(List.of("thrice", "once", "never"), order(result));
    }

    @Test
    void affinityStopsGrowingAtTheCap() {
        MatchResult result = run(listing(List.of()), withHistory("three", 3, null), withHistory("nine", 9, null));
        assertEquals(result.ranked().get(0).parts().affinity(), result.ranked().get(1).parts().affinity());
        assertEquals(1.0, result.ranked().get(0).parts().affinity());
    }

    @Test
    void recentCustomersRankAboveLapsedOnes() {
        MatchResult result = run(listing(List.of()), withHistory("lapsed", 0, NOW.minus(Duration.ofDays(30))),
                withHistory("recent", 0, NOW.minus(Duration.ofHours(2))));
        assertEquals(List.of("recent", "lapsed"), order(result));
        assertEquals(0.0, result.ranked().get(1).parts().activity());
    }

    @Test
    void activityDecaysLinearlyOverTheHorizon() {
        MatchResult result = run(listing(List.of()), withHistory("ann", 0, NOW.minus(Duration.ofDays(7))));
        assertEquals(0.5, result.ranked().get(0).parts().activity(), 1e-9);
    }

    @Test
    void lastOrderInTheFutureCountsAsFullyActive() {
        MatchResult result = run(listing(List.of()), withHistory("ann", 0, NOW.plusSeconds(30)));
        assertEquals(1.0, result.ranked().get(0).parts().activity());
    }

    @Test
    void tiesBreakByUsername() {
        MatchResult result = run(listing(List.of()), customer("cara"), customer("abe"), customer("bo"));
        assertEquals(List.of("abe", "bo", "cara"), order(result));
    }

    @Test
    void inputOrderDoesNotChangeTheResult() {
        List<RescueCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candidates.add(withHistory("c" + i, i % 4, NOW.minus(Duration.ofHours(i * 7L))));
        }
        MatchResult first = RescueMatcher.match(listing(List.of()), candidates, KNOWN, NOW, RescuePolicy.DEFAULT);
        Collections.reverse(candidates);
        MatchResult second = RescueMatcher.match(listing(List.of()), candidates, KNOWN, NOW, RescuePolicy.DEFAULT);
        assertEquals(first.ranked(), second.ranked());
    }

    @Test
    void scorePartsUseListingDiscountAndUrgency() {
        RescueListing listing = listing(List.of(), 400, "10.00", "6.00", 5, NOW.plus(Duration.ofMinutes(45)));
        ScoreParts parts = run(listing, customer("ann")).ranked().get(0).parts();
        assertEquals(0.4, parts.discount(), 1e-9);
        assertEquals(0.75, parts.urgency(), 1e-9);
        assertEquals(0.1 * 0.4 + 0.1 * 0.75, parts.total(), 1e-9);
    }

    @Test
    void deadlineBeyondTheHorizonHasNoUrgency() {
        RescueListing listing = listing(List.of(), 400, "10.00", "6.00", 5, NOW.plus(Duration.ofHours(5)));
        assertEquals(0.0, run(listing, customer("ann")).ranked().get(0).parts().urgency());
    }

    @Test
    void discountShareHandlesMissingOrZeroPrices() {
        assertEquals(0.0, RescueMatcher.discountShare(null, new java.math.BigDecimal("3")));
        assertEquals(0.0, RescueMatcher.discountShare(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE));
        assertEquals(0.0, RescueMatcher.discountShare(java.math.BigDecimal.ONE, new java.math.BigDecimal("2")));
    }

    @Test
    void customWeightsChangeTheOrder() {
        RescuePolicy recencyOnly = new RescuePolicy(20, 3, 2, Duration.ofMinutes(10), 3, Duration.ofDays(14),
                Duration.ofMinutes(180), 0.0, 1.0, 0.0, 0.0);
        List<RescueCandidate> candidates = List.of(withHistory("fan", 3, NOW.minus(Duration.ofDays(13))),
                withHistory("fresh", 0, NOW.minus(Duration.ofHours(1))));
        MatchResult result = RescueMatcher.match(listing(List.of()), candidates, KNOWN, NOW, recencyOnly);
        assertEquals(List.of("fresh", "fan"), order(result));
    }

    @Test
    void toAlertTakesTheTopOfTheBudget() {
        RescueListing onePortion = listing(List.of(), 400, "12.00", "6.00", 1, NOW.plusSeconds(3600));
        MatchResult result = run(onePortion, withHistory("a", 3, null), withHistory("b", 2, null),
                withHistory("c", 1, null), withHistory("d", 0, null));
        assertEquals(3, result.alertBudget());
        assertEquals(List.of("a", "b", "c"),
                result.toAlert().stream().map(RankedCandidate::username).toList());
    }

    @Test
    void toAlertReturnsEveryoneWhenFewerThanTheBudget() {
        MatchResult result = run(listing(List.of()), customer("a"), customer("b"));
        assertEquals(15, result.alertBudget());
        assertEquals(2, result.toAlert().size());
    }

    @Test
    void excludedCustomersAreNeverAlerted() {
        MatchResult result = run(listing(List.of("PEANUTS")), withHistory("fan", 3, NOW),
                new RescueCandidate("allergic", "peanuts", null, null, 3, NOW, 0, false));
        assertEquals(List.of("fan"), result.toAlert().stream().map(RankedCandidate::username).toList());
    }

    @Test
    void reasonLineIsAttachedToEachMatch() {
        RescueListing listing = listing(List.of(), 400, "10.00", "6.00", 5, NOW.plus(Duration.ofMinutes(25)));
        RankedCandidate match = run(listing, withHistory("ann", 2, null)).ranked().get(0);
        assertEquals("you ordered this twice · 40% off · 25 min left", match.reason());
        assertEquals(2, match.pastOrdersOfDish());
        assertEquals(40, match.discountPercent());
        assertEquals(25, match.minutesLeft());
    }
}
