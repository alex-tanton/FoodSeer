package FoodSeer.rescue;

import static FoodSeer.rescue.RescueFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RescueMatcherFilterTest {

    private static RescueCandidate calorieTracker(final String name, final Integer remaining) {
        return new RescueCandidate(name, null, null, remaining, 0, null, 0, false);
    }

    private static RescueCandidate pricePreference(final String name, final String preference) {
        return new RescueCandidate(name, null, preference, null, 0, null, 0, false);
    }

    @Test
    void customerWithNoRestrictionsIsMatched() {
        MatchResult result = run(listing(List.of("WHEAT", "EGGS")), customer("ann"));
        assertEquals(1, result.ranked().size());
        assertTrue(result.excluded().isEmpty());
    }

    @Test
    void allergicCustomerIsExcluded() {
        MatchResult result = run(listing(List.of("WHEAT", "EGGS")), restricted("ann", "EGGS"));
        assertEquals(Exclusion.ALLERGEN, result.excluded().get("ann"));
        assertTrue(result.ranked().isEmpty());
    }

    @Test
    void unrelatedAllergyDoesNotExclude() {
        MatchResult result = run(listing(List.of("WHEAT", "EGGS")), restricted("ann", "SHELLFISH"));
        assertEquals("ann", result.ranked().get(0).username());
    }

    @Test
    void unrecognizedRestrictionIsExcludedWhenListingDeclaresAllergens() {
        MatchResult result = run(listing(List.of("WHEAT")), restricted("ann", "soy sauce"));
        assertEquals(Exclusion.UNCERTAIN_RESTRICTION, result.excluded().get("ann"));
    }

    @Test
    void unrecognizedRestrictionIsMatchedWhenListingDeclaresNothing() {
        MatchResult result = run(listing(List.of()), restricted("ann", "egg whites"));
        assertEquals(1, result.ranked().size());
    }

    @Test
    void blankAllergenTagsCountAsNoDeclaredAllergens() {
        MatchResult result = run(listing(List.of(" ", "")), restricted("ann", "tahini"));
        assertEquals(1, result.ranked().size());
    }

    @Test
    void calorieTrackerOverBudgetIsExcluded() {
        MatchResult result = run(listing(List.of()), calorieTracker("ann", 399));
        assertEquals(Exclusion.CALORIES, result.excluded().get("ann"));
    }

    @Test
    void calorieTrackerExactlyAtBudgetIsMatched() {
        MatchResult result = run(listing(List.of()), calorieTracker("ann", 400));
        assertEquals(1, result.ranked().size());
    }

    @Test
    void calorieTrackerWithNegativeRemainingIsExcluded() {
        MatchResult result = run(listing(List.of()), calorieTracker("ann", -50));
        assertEquals(Exclusion.CALORIES, result.excluded().get("ann"));
    }

    @Test
    void unknownCaloriesExcludeTrackersButNotOthers() {
        RescueListing noCalories = listing(List.of(), null, "12.00", "6.00", 5, NOW.plusSeconds(3600));
        MatchResult result = run(noCalories, calorieTracker("tracker", 2000), customer("free"));
        assertEquals(Exclusion.CALORIES, result.excluded().get("tracker"));
        assertEquals("free", result.ranked().get(0).username());
    }

    @ParameterizedTest
    @CsvSource({"budget,10.00,true", "budget,10.01,false", "moderate,20.00,true", "moderate,20.50,false",
        "premium,35.00,true", "premium,35.01,false", "no-limit,99.00,true", "BUDGET,9.00,true",
        "unknown,99.00,true"})
    void priceCeilingFollowsCostPreference(final String preference, final String price, final boolean matched) {
        RescueListing priced = listing(List.of(), 400, "120.00", price, 5, NOW.plusSeconds(3600));
        MatchResult result = run(priced, pricePreference("ann", preference));
        assertEquals(matched, result.excluded().isEmpty());
        if (!matched) {
            assertEquals(Exclusion.PRICE, result.excluded().get("ann"));
        }
    }

    @Test
    void deadlineExactlyAtLeadTimeIsStillMatched() {
        RescueListing listing = listing(List.of(), 400, "12.00", "6.00", 5, NOW.plusSeconds(10 * 60));
        assertEquals(1, run(listing, customer("ann")).ranked().size());
    }

    @Test
    void deadlineInsideLeadTimeExcludesEveryone() {
        RescueListing listing = listing(List.of(), 400, "12.00", "6.00", 5, NOW.plusSeconds(10 * 60 - 1));
        MatchResult result = run(listing, customer("ann"), customer("bob"));
        assertEquals(Exclusion.TIME, result.excluded().get("ann"));
        assertEquals(Exclusion.TIME, result.excluded().get("bob"));
    }

    @Test
    void expiredListingExcludesEveryone() {
        RescueListing listing = listing(List.of(), 400, "12.00", "6.00", 5, NOW.minusSeconds(1));
        assertEquals(Exclusion.TIME, run(listing, customer("ann")).excluded().get("ann"));
    }

    @Test
    void soldOutListingExcludesEveryoneAndHasNoBudget() {
        RescueListing listing = listing(List.of(), 400, "12.00", "6.00", 0, NOW.plusSeconds(3600));
        MatchResult result = run(listing, customer("ann"));
        assertEquals(Exclusion.TIME, result.excluded().get("ann"));
        assertEquals(0, result.alertBudget());
    }

    @Test
    void missingDeadlineExcludesEveryone() {
        RescueListing listing = listing(List.of(), 400, "12.00", "6.00", 5, null);
        assertEquals(Exclusion.TIME, run(listing, customer("ann")).excluded().get("ann"));
    }

    @Test
    void customerAlreadyAlertedAboutListingIsExcluded() {
        RescueCandidate alerted = new RescueCandidate("ann", null, null, null, 0, null, 0, true);
        assertEquals(Exclusion.ALREADY_ALERTED, run(listing(List.of()), alerted).excluded().get("ann"));
    }

    @Test
    void customerBelowDailyCapIsMatched() {
        RescueCandidate once = new RescueCandidate("ann", null, null, null, 0, null, 1, false);
        assertEquals(1, run(listing(List.of()), once).ranked().size());
    }

    @Test
    void customerAtDailyCapIsExcluded() {
        RescueCandidate capped = new RescueCandidate("ann", null, null, null, 0, null, 2, false);
        assertEquals(Exclusion.DAILY_CAP, run(listing(List.of()), capped).excluded().get("ann"));
    }

    @Test
    void safetyIsReportedBeforeCapOrPrice() {
        RescueCandidate everything = new RescueCandidate("ann", "eggs", "budget", 10, 0, null, 5, true);
        RescueListing listing = listing(List.of("EGGS"), 400, "40.00", "30.00", 5, NOW.plusSeconds(3600));
        assertEquals(Exclusion.ALLERGEN, run(listing, everything).excluded().get("ann"));
    }

    @Test
    void excludedCustomersKeepInputOrder() {
        Instant soon = NOW.plusSeconds(60);
        RescueListing listing = listing(List.of(), 400, "12.00", "6.00", 5, soon);
        MatchResult result = run(listing, customer("zed"), customer("amy"), customer("kim"));
        assertEquals(List.of("zed", "amy", "kim"), List.copyOf(result.excluded().keySet()));
    }
}
