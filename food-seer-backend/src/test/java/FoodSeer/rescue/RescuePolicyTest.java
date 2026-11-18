package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RescuePolicyTest {

    private static RescuePolicy policy(final int max, final int perPortion, final int cap, final Duration lead,
            final double weight) {
        return new RescuePolicy(max, perPortion, cap, lead, 3, Duration.ofDays(14), Duration.ofMinutes(180), weight,
                0.3, 0.1, 0.1);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "-3,0", "1,2", "5,10", "7,14", "10,20", "1000,20"})
    void alertBudgetScalesWithPortionsUpToTheMaximum(final int portions, final int budget) {
        assertEquals(budget, RescuePolicy.DEFAULT.alertBudget(portions));
    }

    @Test
    void alertBudgetDoesNotOverflow() {
        assertEquals(20, RescuePolicy.DEFAULT.alertBudget(Integer.MAX_VALUE));
    }

    @Test
    void rejectsLimitsBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> policy(0, 3, 2, Duration.ofMinutes(10), 0.5));
        assertThrows(IllegalArgumentException.class, () -> policy(20, 0, 2, Duration.ofMinutes(10), 0.5));
        assertThrows(IllegalArgumentException.class, () -> policy(20, 3, 0, Duration.ofMinutes(10), 0.5));
    }

    @Test
    void rejectsNegativeLeadOrWeights() {
        assertThrows(IllegalArgumentException.class, () -> policy(20, 3, 2, Duration.ofMinutes(-1), 0.5));
        assertThrows(IllegalArgumentException.class, () -> policy(20, 3, 2, null, 0.5));
        assertThrows(IllegalArgumentException.class, () -> policy(20, 3, 2, Duration.ofMinutes(10), -0.1));
    }

    @Test
    void zeroLeadIsAllowed() {
        assertEquals(Duration.ZERO, policy(20, 3, 2, Duration.ZERO, 0.5).minLead());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"budget,10", "Moderate,20", " premium ,35", "no-limit,null",
        "null,null", "cheap,null"})
    void priceCeilingMatchesTheRecommendationsPage(final String preference, final String ceiling) {
        BigDecimal expected = ceiling == null ? null : new BigDecimal(ceiling);
        assertEquals(expected, PriceCeiling.of(preference));
    }

    @Test
    void missingPriceFailsAnyCeiling() {
        assertFalse(PriceCeiling.allows("budget", null));
        assertTrue(PriceCeiling.allows(null, null));
    }
}
