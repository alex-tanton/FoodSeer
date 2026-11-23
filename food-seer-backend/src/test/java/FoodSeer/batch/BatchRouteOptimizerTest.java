package FoodSeer.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchRouteOptimizerTest {

    private BatchRouteOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new BatchRouteOptimizer();
    }

    @Test
    void haversineDistanceCalculatesCorrectly() {
        // Talley Student Union (35.7847, -78.6714) to Centennial Campus EB2 (35.7720, -78.6742)
        // Distance is ~0.89 miles
        double distance = BatchRouteOptimizer.haversineDistance(35.7847, -78.6714, 35.7720, -78.6742);
        assertTrue(distance > 0.8 && distance < 1.1, "Distance should be approx 0.9 miles but was " + distance);
    }

    @Test
    void emptyStopsReturnsZero() {
        BatchRouteOptimizer.OptimizedRouteResult result = optimizer.optimizeRoute(35.7847, -78.6714, List.of());
        assertEquals(0, result.getOrderedStops().size());
        assertEquals(BigDecimal.ZERO, result.getTotalDistanceMiles());
        assertEquals(0, result.getEstimatedDurationMinutes());
    }

    @Test
    void singleOrderGuaranteesPickupPrecedesDropoff() {
        List<BatchStop> stops = new ArrayList<>();
        BatchStop dropoff = createStop(1L, "DROPOFF", "HOT", 35.7871, -78.6811);
        BatchStop pickup = createStop(1L, "PICKUP", "HOT", 35.7872, -78.6705);
        stops.add(dropoff);
        stops.add(pickup);

        BatchRouteOptimizer.OptimizedRouteResult result = optimizer.optimizeRoute(35.7847, -78.6714, stops);
        List<BatchStop> ordered = result.getOrderedStops();

        assertEquals(2, ordered.size());
        assertEquals("PICKUP", ordered.get(0).getStopType());
        assertEquals("DROPOFF", ordered.get(1).getStopType());
        assertEquals(1, ordered.get(0).getStopSequence());
        assertEquals(2, ordered.get(1).getStopSequence());
        assertTrue(result.getTotalDistanceMiles().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void multiOrderEnforcesPrecedenceForEveryOrder() {
        List<BatchStop> stops = new ArrayList<>();
        stops.add(createStop(1L, "DROPOFF", "HOT", 35.7871, -78.6811));
        stops.add(createStop(2L, "DROPOFF", "COLD", 35.7720, -78.6742));
        stops.add(createStop(1L, "PICKUP", "HOT", 35.7872, -78.6705));
        stops.add(createStop(2L, "PICKUP", "COLD", 35.7895, -78.6680));

        BatchRouteOptimizer.OptimizedRouteResult result = optimizer.optimizeRoute(35.7847, -78.6714, stops);
        List<BatchStop> ordered = result.getOrderedStops();

        assertEquals(4, ordered.size());

        int pickupIdx1 = -1, dropoffIdx1 = -1;
        int pickupIdx2 = -1, dropoffIdx2 = -1;

        for (int i = 0; i < ordered.size(); i++) {
            BatchStop s = ordered.get(i);
            if (s.getOrderId().equals(1L)) {
                if ("PICKUP".equals(s.getStopType())) pickupIdx1 = i;
                else dropoffIdx1 = i;
            } else if (s.getOrderId().equals(2L)) {
                if ("PICKUP".equals(s.getStopType())) pickupIdx2 = i;
                else dropoffIdx2 = i;
            }
        }

        assertTrue(pickupIdx1 < dropoffIdx1, "Order 1 pickup must precede dropoff");
        assertTrue(pickupIdx2 < dropoffIdx2, "Order 2 pickup must precede dropoff");
        assertTrue(result.getThermalDegradationRisk() >= 0.0 && result.getThermalDegradationRisk() <= 1.0);
    }

    @Test
    void thermalSensitivityPrioritizesSensitiveItems() {
        // When one order is delicate ice cream (COLD) and another is dry biscuits (AMBIENT),
        // optimizer thermal penalty guides sequences to minimize perishable degradation
        List<BatchStop> stops = new ArrayList<>();
        stops.add(createStop(1L, "PICKUP", "COLD", 35.7895, -78.6680));
        stops.add(createStop(1L, "DROPOFF", "COLD", 35.7847, -78.6714));
        stops.add(createStop(2L, "PICKUP", "AMBIENT", 35.7872, -78.6705));
        stops.add(createStop(2L, "DROPOFF", "AMBIENT", 35.7720, -78.6742));

        BatchRouteOptimizer.OptimizedRouteResult result = optimizer.optimizeRoute(35.7847, -78.6714, stops);
        assertNotNull(result);
        assertTrue(result.getThermalDegradationRisk() < 0.25, "Thermal degradation risk should remain low for optimized route");
    }

    private BatchStop createStop(Long orderId, String stopType, String thermal, double lat, double lon) {
        BatchStop stop = new BatchStop();
        stop.setOrderId(orderId);
        stop.setStopType(stopType);
        stop.setThermalCategory(thermal);
        stop.setLatitude(lat);
        stop.setLongitude(lon);
        stop.setLocationName("Location-" + orderId + "-" + stopType);
        return stop;
    }
}
