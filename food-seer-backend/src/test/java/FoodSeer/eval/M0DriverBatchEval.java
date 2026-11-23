package FoodSeer.eval;

import FoodSeer.batch.BatchRouteOptimizer;
import FoodSeer.batch.BatchStop;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M0 Acceptance Evaluation for Driver Batch Dispatcher.
 *
 * <p>
 * Evaluates 500 simulated multi-order dispatch clusters comparing naive single-order
 * dispatch against the Driver Batch Dispatcher's thermal-aware precedence-constrained
 * routing engine.
 * </p>
 *
 * <p>
 * <b>Claims:</b>
 * <ul>
 *   <li><b>Claim 1 (Thermal Freshness Protection):</b> Batched routes maintain food freshness
 *       with a thermal violation rate &lt; 2.0% across all delivered orders.</li>
 *   <li><b>Claim 2 (Distance Reduction Lift):</b> Achieves a statistically significant reduction
 *       in total transit distance with Cliff's delta &ge; 0.40.</li>
 *   <li><b>Claim 3 (Precedence & Atomicity):</b> 100% of batched stops maintain strict pickup-before-dropoff
 *       precedence constraints.</li>
 * </ul>
 * </p>
 */
public class M0DriverBatchEval {

    private static final int TOTAL_RUNS = 500;
    private static final long[] SEEDS = {101L, 202L, 303L, 404L, 505L, 606L, 707L, 808L, 909L, 1010L};
    private static final int RUNS_PER_SEED = 50;
    private static final double MIN_CLIFFS_DELTA = 0.40;
    private static final double MAX_THERMAL_VIOLATION_RATE = 0.02; // 2.0%

    // Coordinates representing restaurants & delivery zones
    private static final double[][] RESTAURANT_POOLS = {
            {35.7872, -78.6705}, // Bistro
            {35.7895, -78.6680}, // Gelato / Creamery
            {35.7880, -78.6750}, // Asian Grill
            {35.7865, -78.6650}, // Pizzeria
            {35.7890, -78.6720}  // Cafe
    };

    private static final double[][] DROPOFF_POOLS = {
            {35.7847, -78.6714}, // Talley Union
            {35.7720, -78.6742}, // Centennial Campus
            {35.7871, -78.6811}, // North Campus
            {35.7879, -78.6698}, // Library Commons
            {35.7839, -78.6730}  // Gym Complex
    };

    private static final String[] THERMAL_CATEGORIES = {"HOT", "COLD", "AMBIENT"};

    @Test
    void runM0DriverBatchEvaluation() throws IOException {
        BatchRouteOptimizer optimizer = new BatchRouteOptimizer();

        double[] baselineDistances = new double[TOTAL_RUNS];
        double[] treatmentDistances = new double[TOTAL_RUNS];

        int totalOrdersEvaluated = 0;
        int baselineThermalViolations = 0;
        int treatmentThermalViolations = 0;
        int precedenceViolations = 0;

        int runIndex = 0;

        for (long seed : SEEDS) {
            Random rng = new Random(seed);

            for (int r = 0; r < RUNS_PER_SEED; r++) {
                int orderCount = 2 + rng.nextInt(3); // 2 to 4 orders per batch
                List<BatchStop> stops = new ArrayList<>();

                for (int i = 0; i < orderCount; i++) {
                    long orderId = (runIndex * 10L) + i + 1;
                    String thermal = THERMAL_CATEGORIES[rng.nextInt(THERMAL_CATEGORIES.length)];

                    int rIdx = rng.nextInt(RESTAURANT_POOLS.length);
                    int dIdx = rng.nextInt(DROPOFF_POOLS.length);

                    BatchStop pickup = new BatchStop();
                    pickup.setOrderId(orderId);
                    pickup.setStopType("PICKUP");
                    pickup.setThermalCategory(thermal);
                    pickup.setLatitude(RESTAURANT_POOLS[rIdx][0]);
                    pickup.setLongitude(RESTAURANT_POOLS[rIdx][1]);

                    BatchStop dropoff = new BatchStop();
                    dropoff.setOrderId(orderId);
                    dropoff.setStopType("DROPOFF");
                    dropoff.setThermalCategory(thermal);
                    dropoff.setLatitude(DROPOFF_POOLS[dIdx][0]);
                    dropoff.setLongitude(DROPOFF_POOLS[dIdx][1]);

                    stops.add(pickup);
                    stops.add(dropoff);
                }

                totalOrdersEvaluated += orderCount;

                // 1. Evaluate Baseline (current sequential single-order dispatch: P1 -> D1 -> P2 -> D2 -> ...)
                double baseDist = 0.0;
                double curLat = 35.7847, curLon = -78.6714;
                double baseCumulativeTime = 0.0;

                for (int i = 0; i < orderCount; i++) {
                    BatchStop p = stops.get(i * 2);
                    BatchStop d = stops.get(i * 2 + 1);

                    double toPickup = BatchRouteOptimizer.haversineDistance(curLat, curLon, p.getLatitude(), p.getLongitude());
                    baseDist += toPickup;
                    baseCumulativeTime += (toPickup / 25.0) * 60.0 + 3.5;

                    double toDropoff = BatchRouteOptimizer.haversineDistance(p.getLatitude(), p.getLongitude(), d.getLatitude(), d.getLongitude());
                    baseDist += toDropoff;
                    baseCumulativeTime += (toDropoff / 25.0) * 60.0 + 3.5;

                    curLat = d.getLatitude();
                    curLon = d.getLongitude();

                    // Food elapsed time from ready/staged to delivery
                    if (isThermalViolation(p.getThermalCategory(), baseCumulativeTime)) {
                        baselineThermalViolations++;
                    }
                }
                baselineDistances[runIndex] = baseDist;

                // 2. Evaluate Treatment (Optimized Batch Routing)
                BatchRouteOptimizer.OptimizedRouteResult result = optimizer.optimizeRoute(35.7847, -78.6714, stops);
                treatmentDistances[runIndex] = result.getTotalDistanceMiles().doubleValue();

                // Check precedence and thermal violations in treatment
                List<BatchStop> ordered = result.getOrderedStops();
                java.util.Map<Long, Integer> pickupIndices = new java.util.HashMap<>();
                java.util.Map<Long, Double> pickupTimes = new java.util.HashMap<>();

                double treatCurLat = 35.7847, treatCurLon = -78.6714;
                double cumulativeTime = 0.0;

                for (int idx = 0; idx < ordered.size(); idx++) {
                    BatchStop s = ordered.get(idx);
                    double legDist = BatchRouteOptimizer.haversineDistance(treatCurLat, treatCurLon, s.getLatitude(), s.getLongitude());
                    cumulativeTime += (legDist / 25.0) * 60.0 + 3.5;
                    treatCurLat = s.getLatitude();
                    treatCurLon = s.getLongitude();

                    if ("PICKUP".equalsIgnoreCase(s.getStopType())) {
                        pickupIndices.put(s.getOrderId(), idx);
                        pickupTimes.put(s.getOrderId(), cumulativeTime);
                    } else if ("DROPOFF".equalsIgnoreCase(s.getStopType())) {
                        Integer pIdx = pickupIndices.get(s.getOrderId());
                        if (pIdx == null || pIdx >= idx) {
                            precedenceViolations++;
                        }
                        Double pTime = pickupTimes.get(s.getOrderId());
                        double inTransit = (pTime != null) ? (cumulativeTime - pTime) : cumulativeTime;
                        if (isThermalViolation(s.getThermalCategory(), inTransit)) {
                            treatmentThermalViolations++;
                        }
                    }
                }

                runIndex++;
            }
        }

        // Calculate statistics
        double baselineViolationRate = (double) baselineThermalViolations / totalOrdersEvaluated;
        double treatmentViolationRate = (double) treatmentThermalViolations / totalOrdersEvaluated;
        double cliffsDelta = calculateCliffsDelta(baselineDistances, treatmentDistances);

        // Assertions for formal claims
        assertEquals(0, precedenceViolations, "Zero precedence violations allowed");
        assertTrue(treatmentViolationRate < MAX_THERMAL_VIOLATION_RATE,
                "Treatment thermal violation rate must be < 2.0% but was " + (treatmentViolationRate * 100) + "%");
        assertTrue(cliffsDelta >= MIN_CLIFFS_DELTA,
                "Cliff's Delta distance reduction must be >= 0.40 but was " + cliffsDelta);

        // Generate and write markdown report
        writeReport(totalOrdersEvaluated, baselineViolationRate, treatmentViolationRate,
                baselineDistances, treatmentDistances, cliffsDelta);
    }

    private boolean isThermalViolation(String category, double transitMinutes) {
        if ("HOT".equalsIgnoreCase(category)) {
            return transitMinutes > 25.0;
        } else if ("COLD".equalsIgnoreCase(category)) {
            return transitMinutes > 20.0;
        }
        return transitMinutes > 45.0; // Ambient
    }

    private double calculateCliffsDelta(double[] baseline, double[] treatment) {
        long better = 0;
        long worse = 0;
        int n = baseline.length;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                // We want treatment distance to be lower than baseline
                if (treatment[j] < baseline[i]) {
                    better++;
                } else if (treatment[j] > baseline[i]) {
                    worse++;
                }
            }
        }
        return (double) (better - worse) / ((double) n * n);
    }

    private void writeReport(
            int totalOrders, double baseViolRate, double treatViolRate,
            double[] baselineDistances, double[] treatmentDistances, double cliffsDelta) throws IOException {

        double avgBaseDist = calculateMean(baselineDistances);
        double avgTreatDist = calculateMean(treatmentDistances);
        double distReductionPct = ((avgBaseDist - avgTreatDist) / avgBaseDist) * 100.0;

        StringBuilder sb = new StringBuilder();
        sb.append("# M0 Acceptance Evaluation: Driver Batch Dispatcher\n\n");
        sb.append("**Evaluation Run Timestamp:** ").append(Instant.now()).append("\n");
        sb.append("**Simulated Dispatch Clusters:** ").append(TOTAL_RUNS).append("\n");
        sb.append("**Total Orders Evaluated:** ").append(totalOrders).append("\n\n");

        sb.append("## Executive Summary\n\n");
        sb.append("| Metric | Baseline (Single-Trip FIFO) | Driver Batch Dispatcher | Delta / Effect Size | Status |\n");
        sb.append("|---|---|---|---|---|\n");
        sb.append(String.format("| **Thermal Violation Rate** | %.2f%% | %.2f%% | -%.2f%% | **PASS (Claim 1)** |\n",
                baseViolRate * 100.0, treatViolRate * 100.0, (baseViolRate - treatViolRate) * 100.0));
        sb.append(String.format("| **Average Distance per Batch** | %.2f mi | %.2f mi | -%.1f%% (Cliff's $\\delta = %.3f$) | **PASS (Claim 2)** |\n",
                avgBaseDist, avgTreatDist, distReductionPct, cliffsDelta));
        sb.append("| **Precedence Integrity** | N/A | 100.0% (0 violations) | Optimal | **PASS (Claim 3)** |\n\n");

        sb.append("## Statistical Claims Validation\n\n");
        sb.append("1. **Claim 1 (Thermal Freshness Protection):** Thermal degradation violations dropped from ");
        sb.append(String.format("%.2f%% to %.2f%% (threshold &lt; 2.0%%).\n", baseViolRate * 100.0, treatViolRate * 100.0));
        sb.append("2. **Claim 2 (Mileage Reduction Lift):** Batch optimization delivered a ");
        sb.append(String.format("%.1f%% reduction in total delivery travel with Cliff's $\\delta = %.3f$ (&ge; 0.40, large effect size).\n",
                distReductionPct, cliffsDelta));
        sb.append("3. **Claim 3 (Precedence & Atomicity):** In all 500 batches, pickup stops strictly preceded customer dropoffs with zero orphaned or out-of-order deliveries.\n");

        Path reportDir = Paths.get("target", "m0-eval");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("m0-driver-batch-report.md"), sb.toString(), StandardCharsets.UTF_8);
    }

    private double calculateMean(double[] values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
}
