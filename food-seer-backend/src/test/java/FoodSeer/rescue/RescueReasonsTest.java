package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RescueReasonsTest {

    @ParameterizedTest
    @CsvSource({"1,once", "2,twice", "3,3 times", "12,12 times"})
    void ordersAreCountedInPlainWords(final int count, final String words) {
        assertEquals(words, RescueReasons.times(count));
    }

    @ParameterizedTest
    @CsvSource({"0,0 min left", "25,25 min left", "59,59 min left", "60,1 h left", "70,1 h 10 min left",
        "120,2 h left", "-5,0 min left"})
    void timeLeftIsReadable(final long minutes, final String text) {
        assertEquals(text, RescueReasons.timeLeft(minutes));
    }

    @Test
    void firstTimeCustomerSeesDiscountAndTime() {
        assertEquals("40% off · 25 min left", RescueReasons.describe(0, 40, 25));
    }

    @Test
    void repeatCustomerSeesHistoryFirst() {
        assertEquals("you ordered this once · 50% off · 1 h 30 min left", RescueReasons.describe(1, 50, 90));
    }

    @Test
    void noDiscountLeavesOnlyTime() {
        assertEquals("2 h left", RescueReasons.describe(0, 0, 120));
    }
}
