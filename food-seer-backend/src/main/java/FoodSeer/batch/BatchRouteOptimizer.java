package FoodSeer.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Route and thermal freshness optimization engine for driver order batching.
 * Solves constrained Traveling Salesperson Problem (precedence constraint:
 * pickup must precede dropoff for each order) while minimizing route distance
 * and food temperature degradation.
 */
public class BatchRouteOptimizer {

    private static final double EARTH_RADIUS_MILES = 3958.8;
    private static final double AVERAGE_DRIVE_SPEED_MPH = 25.0;
    private static final double STOP_HANDOFF_MINUTES = 3.5;
    private static final double THERMAL_WEIGHT = 2.5; // Weight multiplier for thermal penalty in miles equivalent

    public static class OptimizedRouteResult {
        private final List<BatchStop> orderedStops;
        private final BigDecimal totalDistanceMiles;
        private final int estimatedDurationMinutes;
        private final double thermalDegradationRisk;

        public OptimizedRouteResult(
                final List<BatchStop> orderedStops,
                final BigDecimal totalDistanceMiles,
                final int estimatedDurationMinutes,
                final double thermalDegradationRisk) {
            this.orderedStops = orderedStops;
            this.totalDistanceMiles = totalDistanceMiles;
            this.estimatedDurationMinutes = estimatedDurationMinutes;
            this.thermalDegradationRisk = thermalDegradationRisk;
        }

        public List<BatchStop> getOrderedStops() {
            return orderedStops;
        }

        public BigDecimal getTotalDistanceMiles() {
            return totalDistanceMiles;
        }

        public int getEstimatedDurationMinutes() {
            return estimatedDurationMinutes;
        }

        public double getThermalDegradationRisk() {
            return thermalDegradationRisk;
        }
    }

    /**
     * Optimizes a collection of stops (pickups and dropoffs) starting from a base driver coordinate.
     */
    public OptimizedRouteResult optimizeRoute(
            final double startLat,
            final double startLon,
            final List<BatchStop> rawStops) {

        if (rawStops == null || rawStops.isEmpty()) {
            return new OptimizedRouteResult(new ArrayList<>(), BigDecimal.ZERO, 0, 0.0);
        }

        // Find best sequence via constrained permutation search
        List<BatchStop> bestSequence = null;
        double bestCost = Double.MAX_VALUE;
        double bestDistance = 0.0;
        int bestDuration = 0;
        double bestThermalRisk = 0.0;

        List<List<BatchStop>> validSequences = generateValidSequences(rawStops);

        for (List<BatchStop> sequence : validSequences) {
            double distance = calculateTotalDistance(startLat, startLon, sequence);
            int duration = calculateTotalDuration(distance, sequence.size());
            double thermalRisk = calculateThermalRisk(startLat, startLon, sequence);

            double totalCost = distance + (THERMAL_WEIGHT * thermalRisk * 10.0);

            if (totalCost < bestCost) {
                bestCost = totalCost;
                bestSequence = sequence;
                bestDistance = distance;
                bestDuration = duration;
                bestThermalRisk = thermalRisk;
            }
        }

        if (bestSequence == null) {
            bestSequence = new ArrayList<>(rawStops);
        }

        // Re-assign 1-based stop sequences
        for (int i = 0; i < bestSequence.size(); i++) {
            bestSequence.get(i).setStopSequence(i + 1);
        }

        BigDecimal finalDistance = BigDecimal.valueOf(bestDistance).setScale(2, RoundingMode.HALF_UP);
        double finalThermalRisk = Math.round(bestThermalRisk * 100.0) / 100.0;

        return new OptimizedRouteResult(bestSequence, finalDistance, bestDuration, finalThermalRisk);
    }

    /**
     * Calculates distance between two latitude/longitude points in miles using Haversine formula.
     */
    public static double haversineDistance(
            final double lat1, final double lon1,
            final double lat2, final double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_MILES * c;
    }

    private double calculateTotalDistance(
            final double startLat, final double startLon,
            final List<BatchStop> sequence) {
        double currentLat = startLat;
        double currentLon = startLon;
        double totalDist = 0.0;

        for (BatchStop stop : sequence) {
            double stopLat = stop.getLatitude() != null ? stop.getLatitude() : startLat;
            double stopLon = stop.getLongitude() != null ? stop.getLongitude() : startLon;
            totalDist += haversineDistance(currentLat, currentLon, stopLat, stopLon);
            currentLat = stopLat;
            currentLon = stopLon;
        }

        return totalDist;
    }

    private int calculateTotalDuration(final double totalDistanceMiles, final int stopCount) {
        double driveTimeHours = totalDistanceMiles / AVERAGE_DRIVE_SPEED_MPH;
        double driveTimeMinutes = driveTimeHours * 60.0;
        double handoffMinutes = stopCount * STOP_HANDOFF_MINUTES;
        return (int) Math.round(driveTimeMinutes + handoffMinutes);
    }

    /**
     * Evaluates thermal freshness degradation risk across all dropoffs in the route.
     * Orders with HOT or COLD items penalized if transit time from pickup to dropoff is prolonged.
     */
    private double calculateThermalRisk(
            final double startLat, final double startLon,
            final List<BatchStop> sequence) {
        double currentLat = startLat;
        double currentLon = startLon;
        double cumulativeMinutes = 0.0;

        java.util.Map<Long, Double> pickupTimes = new java.util.HashMap<>();
        List<Double> orderRisks = new ArrayList<>();

        for (BatchStop stop : sequence) {
            double stopLat = stop.getLatitude() != null ? stop.getLatitude() : startLat;
            double stopLon = stop.getLongitude() != null ? stop.getLongitude() : startLon;
            double legDistance = haversineDistance(currentLat, currentLon, stopLat, stopLon);
            double legMinutes = (legDistance / AVERAGE_DRIVE_SPEED_MPH) * 60.0 + STOP_HANDOFF_MINUTES;
            cumulativeMinutes += legMinutes;
            currentLat = stopLat;
            currentLon = stopLon;

            if ("PICKUP".equalsIgnoreCase(stop.getStopType())) {
                pickupTimes.put(stop.getOrderId(), cumulativeMinutes);
            } else if ("DROPOFF".equalsIgnoreCase(stop.getStopType())) {
                Double pickupTime = pickupTimes.get(stop.getOrderId());
                double inTransitMinutes = (pickupTime != null) ? (cumulativeMinutes - pickupTime) : cumulativeMinutes;
                orderRisks.add(evaluateThermalDecay(stop.getThermalCategory(), inTransitMinutes));
            }
        }

        if (orderRisks.isEmpty()) {
            return 0.0;
        }

        double sumRisk = 0.0;
        for (double r : orderRisks) {
            sumRisk += r;
        }
        return sumRisk / orderRisks.size();
    }

    private double evaluateThermalDecay(final String category, final double transitMinutes) {
        if ("HOT".equalsIgnoreCase(category)) {
            if (transitMinutes <= 15.0) {
                return 0.02 * (transitMinutes / 15.0);
            }
            double excess = transitMinutes - 15.0;
            return Math.min(1.0, 0.02 + 0.04 * excess + (0.002 * excess * excess));
        } else if ("COLD".equalsIgnoreCase(category)) {
            if (transitMinutes <= 12.0) {
                return 0.03 * (transitMinutes / 12.0);
            }
            double excess = transitMinutes - 12.0;
            return Math.min(1.0, 0.03 + 0.05 * excess + (0.003 * excess * excess));
        } else {
            // AMBIENT
            return Math.min(1.0, 0.01 * (transitMinutes / 25.0));
        }
    }

    /**
     * Generates all permutations of stops where each order's PICKUP precedes its DROPOFF.
     */
    private List<List<BatchStop>> generateValidSequences(final List<BatchStop> stops) {
        List<List<BatchStop>> results = new ArrayList<>();
        backtrackSequences(stops, new boolean[stops.size()], new ArrayList<>(), results, new java.util.HashSet<>());
        return results;
    }

    private void backtrackSequences(
            final List<BatchStop> stops,
            final boolean[] used,
            final List<BatchStop> current,
            final List<List<BatchStop>> results,
            final java.util.Set<Long> pickedUpOrders) {

        if (current.size() == stops.size()) {
            results.add(new ArrayList<>(current));
            return;
        }

        for (int i = 0; i < stops.size(); i++) {
            if (used[i]) {
                continue;
            }

            BatchStop candidate = stops.get(i);
            Long orderId = candidate.getOrderId();

            // DROPOFF requires order to have been picked up already
            if ("DROPOFF".equalsIgnoreCase(candidate.getStopType()) && !pickedUpOrders.contains(orderId)) {
                continue;
            }

            used[i] = true;
            current.add(candidate);
            boolean addedPickup = false;
            if ("PICKUP".equalsIgnoreCase(candidate.getStopType())) {
                pickedUpOrders.add(orderId);
                addedPickup = true;
            }

            backtrackSequences(stops, used, current, results, pickedUpOrders);

            // Backtrack
            used[i] = false;
            current.remove(current.size() - 1);
            if (addedPickup) {
                pickedUpOrders.remove(orderId);
            }
        }
    }
}
