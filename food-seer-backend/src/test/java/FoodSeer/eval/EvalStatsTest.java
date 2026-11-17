package FoodSeer.eval;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvalStatsTest {

    @Test
    void medianOfOddCountIsTheMiddleValue() {
        assertEquals(3.0, EvalStats.median(new double[] {5, 1, 3}));
    }

    @Test
    void medianOfEvenCountAveragesTheMiddlePair() {
        assertEquals(2.5, EvalStats.median(new double[] {4, 1, 3, 2}));
    }

    @Test
    void medianDoesNotReorderTheInput() {
        double[] values = {3, 1, 2};
        EvalStats.median(values);
        assertArrayEquals(new double[] {3, 1, 2}, values);
    }

    @Test
    void medianRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> EvalStats.median(new double[0]));
    }

    @Test
    void cliffsDeltaIsOneWhenEveryXWins() {
        assertEquals(1.0, EvalStats.cliffsDelta(new double[] {5, 6, 7}, new double[] {1, 2}));
    }

    @Test
    void cliffsDeltaIsMinusOneWhenEveryYWins() {
        assertEquals(-1.0, EvalStats.cliffsDelta(new double[] {1}, new double[] {2, 3}));
    }

    @Test
    void cliffsDeltaIsZeroForIdenticalSamples() {
        assertEquals(0.0, EvalStats.cliffsDelta(new double[] {1, 2, 3}, new double[] {1, 2, 3}));
    }

    @Test
    void cliffsDeltaCountsTiesAsNeither() {
        // pairs: (1,1) tie, (1,2) lose, (3,1) win, (3,2) win -> (2 - 1) / 4
        assertEquals(0.25, EvalStats.cliffsDelta(new double[] {1, 3}, new double[] {1, 2}));
    }

    @Test
    void cliffsDeltaRejectsEmptySamples() {
        assertThrows(IllegalArgumentException.class, () -> EvalStats.cliffsDelta(new double[0], new double[] {1}));
    }

    @ParameterizedTest
    @CsvSource({"0.0,negligible", "0.146,negligible", "0.147,small", "-0.2,small", "0.33,medium",
        "0.473,medium", "0.474,large", "-1.0,large"})
    void magnitudeUsesTheRomanoBands(final double delta, final String magnitude) {
        assertEquals(magnitude, EvalStats.cliffsMagnitude(delta));
    }
}
