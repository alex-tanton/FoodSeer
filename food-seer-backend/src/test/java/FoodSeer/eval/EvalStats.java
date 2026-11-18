package FoodSeer.eval;

import java.util.Arrays;

/** Small, dependency-free statistics for comparing eval runs across seeds. */
public final class EvalStats {

    private EvalStats() {
        // static helpers only
    }

    /**
     * @param values one number per seed; must not be empty
     * @return the median (mean of the middle two for an even count)
     */
    public static double median(final double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Need at least one value.");
        }
        final double[] sorted = values.clone();
        Arrays.sort(sorted);
        final int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    /**
     * Cliff's delta: how often a value from {@code xs} beats a value from
     * {@code ys}, minus how often it loses, over all pairs. 1 means every x is
     * larger, -1 every y, 0 no difference.
     *
     * @param xs first sample
     * @param ys second sample
     * @return delta in [-1, 1]
     */
    public static double cliffsDelta(final double[] xs, final double[] ys) {
        if (xs.length == 0 || ys.length == 0) {
            throw new IllegalArgumentException("Need at least one value in each sample.");
        }
        long greater = 0;
        long less = 0;
        for (final double x : xs) {
            for (final double y : ys) {
                if (x > y) {
                    greater++;
                } else if (x < y) {
                    less++;
                }
            }
        }
        return (greater - less) / (double) ((long) xs.length * ys.length);
    }

    /**
     * Romano et al. (2006) bands, the usual reading of Cliff's delta.
     *
     * @param delta Cliff's delta
     * @return negligible, small, medium or large
     */
    public static String cliffsMagnitude(final double delta) {
        final double size = Math.abs(delta);
        if (size < 0.147) {
            return "negligible";
        }
        if (size < 0.33) {
            return "small";
        }
        if (size < 0.474) {
            return "medium";
        }
        return "large";
    }
}
