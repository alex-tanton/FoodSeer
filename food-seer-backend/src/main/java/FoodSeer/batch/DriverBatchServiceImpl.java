package FoodSeer.batch;

import FoodSeer.entity.DriverStats;
import FoodSeer.entity.Food;
import FoodSeer.entity.Order;
import FoodSeer.entity.User;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.DriverStatsRepository;
import FoodSeer.repositories.OrderRepository;
import FoodSeer.service.DriverStatsService;
import FoodSeer.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DriverBatchServiceImpl implements DriverBatchService {

    private final DeliveryBatchRepository deliveryBatchRepository;
    private final BatchStopRepository batchStopRepository;
    private final OrderRepository orderRepository;
    private final UserService userService;
    private final DriverStatsRepository driverStatsRepository;
    private final DriverStatsService driverStatsService;
    private final BatchRouteOptimizer optimizer;

    private static final double DRIVER_BASE_LAT = 35.7847; // Talley Center base
    private static final double DRIVER_BASE_LON = -78.6714;
    private static final BigDecimal DEFAULT_DELIVERY_FEE = BigDecimal.valueOf(3.50);
    private static final BigDecimal BONUS_PER_BATCH_ORDER = BigDecimal.valueOf(2.50);

    // Preset restaurant locations for realistic clustering
    private static final String[][] RESTAURANTS = {
            {"Tuscany Bistro", "35.7872", "-78.6705"},
            {"Frosty Freeze & Creamery", "35.7895", "-78.6680"},
            {"Dragon Bowl Asian Grill", "35.7880", "-78.6750"},
            {"Red Pepper Pizzeria", "35.7865", "-78.6650"},
            {"Green Leaf Salads", "35.7890", "-78.6720"}
    };

    // Preset dropoff locations across campus
    private static final String[][] DROPOFFS = {
            {"Talley Student Union", "35.7847", "-78.6714"},
            {"Centennial Campus - EB2", "35.7720", "-78.6742"},
            {"North Campus - Sullivan Hall", "35.7871", "-78.6811"},
            {"Hill Library Commons", "35.7879", "-78.6698"},
            {"Carmichael Gym Complex", "35.7839", "-78.6730"}
    };

    public DriverBatchServiceImpl(
            final DeliveryBatchRepository deliveryBatchRepository,
            final BatchStopRepository batchStopRepository,
            final OrderRepository orderRepository,
            final UserService userService,
            final DriverStatsRepository driverStatsRepository,
            final DriverStatsService driverStatsService) {
        this.deliveryBatchRepository = deliveryBatchRepository;
        this.batchStopRepository = batchStopRepository;
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.driverStatsRepository = driverStatsRepository;
        this.driverStatsService = driverStatsService;
        this.optimizer = new BatchRouteOptimizer();
    }

    @Override
    @Transactional
    public List<BatchDtos.BatchDto> getSuggestedBatches() {
        // Return existing active proposed batches or generate new suggestions
        List<DeliveryBatch> proposedBatches = deliveryBatchRepository.findByStatus("PROPOSED");
        if (!proposedBatches.isEmpty()) {
            return proposedBatches.stream().map(this::mapToDto).collect(Collectors.toList());
        }

        List<Order> placedOrders = orderRepository.findByStatus("Placed");
        if (placedOrders.isEmpty()) {
            return Collections.emptyList();
        }

        // Group unbatched placed orders in clusters of 2 or 3
        List<BatchDtos.BatchDto> generated = new ArrayList<>();
        int clusterSize = 2;
        for (int i = 0; i < placedOrders.size(); i += clusterSize) {
            List<Order> cluster = placedOrders.subList(i, Math.min(i + clusterSize, placedOrders.size()));
            if (cluster.size() >= 2) {
                DeliveryBatch batch = createBatchFromOrders(cluster);
                deliveryBatchRepository.save(batch);
                generated.add(mapToDto(batch));
            }
        }

        return generated;
    }

    private DeliveryBatch createBatchFromOrders(final List<Order> orders) {
        DeliveryBatch batch = new DeliveryBatch();
        batch.setBatchCode("BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        batch.setStatus("PROPOSED");
        batch.setCreatedAt(LocalDateTime.now());

        List<BatchStop> rawStops = new ArrayList<>();
        BigDecimal totalDeliveryFees = BigDecimal.ZERO;

        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            BigDecimal fee = order.getDeliveryCost() != null ? order.getDeliveryCost() : DEFAULT_DELIVERY_FEE;
            totalDeliveryFees = totalDeliveryFees.add(fee);

            String thermal = determineThermalCategory(order);
            int restIdx = (int) (Math.abs(order.getId() != null ? order.getId() : i) % RESTAURANTS.length);
            int dropIdx = (int) (Math.abs((order.getId() != null ? order.getId() : i) + 2) % DROPOFFS.length);

            // Pickup Stop
            BatchStop pickup = new BatchStop();
            pickup.setBatch(batch);
            pickup.setOrderId(order.getId());
            pickup.setOrderName(order.getName() != null ? order.getName() : "Order #" + order.getId());
            pickup.setStopType("PICKUP");
            pickup.setLocationName(RESTAURANTS[restIdx][0]);
            pickup.setLatitude(Double.parseDouble(RESTAURANTS[restIdx][1]));
            pickup.setLongitude(Double.parseDouble(RESTAURANTS[restIdx][2]));
            pickup.setThermalCategory(thermal);
            pickup.setStatus("PENDING");
            pickup.setAllergyNotice(order.getAllergyRestrictions());
            rawStops.add(pickup);

            // Dropoff Stop
            BatchStop dropoff = new BatchStop();
            dropoff.setBatch(batch);
            dropoff.setOrderId(order.getId());
            dropoff.setOrderName(order.getName() != null ? order.getName() : "Order #" + order.getId());
            dropoff.setStopType("DROPOFF");
            dropoff.setLocationName(DROPOFFS[dropIdx][0]);
            dropoff.setLatitude(Double.parseDouble(DROPOFFS[dropIdx][1]));
            dropoff.setLongitude(Double.parseDouble(DROPOFFS[dropIdx][2]));
            dropoff.setThermalCategory(thermal);
            dropoff.setStatus("PENDING");
            dropoff.setAllergyNotice(order.getAllergyRestrictions());
            rawStops.add(dropoff);
        }

        // Optimize stop sequence and compute metrics
        BatchRouteOptimizer.OptimizedRouteResult opt = optimizer.optimizeRoute(
                DRIVER_BASE_LAT, DRIVER_BASE_LON, rawStops);

        batch.setStops(opt.getOrderedStops());
        batch.setTotalDistanceMiles(opt.getTotalDistanceMiles());
        batch.setEstimatedDurationMinutes(opt.getEstimatedDurationMinutes());
        batch.setThermalDegradationRisk(opt.getThermalDegradationRisk());

        batch.setBasePayout(totalDeliveryFees);
        BigDecimal bonus = BONUS_PER_BATCH_ORDER.multiply(BigDecimal.valueOf(orders.size() - 1));
        batch.setBatchBonus(bonus);
        batch.setTotalPayout(totalDeliveryFees.add(bonus));

        return batch;
    }

    private String determineThermalCategory(final Order order) {
        if (order.getFoods() != null) {
            for (Food food : order.getFoods()) {
                String name = food.getFoodName() != null ? food.getFoodName().toLowerCase() : "";
                if (name.contains("ice cream") || name.contains("salad") || name.contains("cold")
                        || name.contains("smoothie") || name.contains("frozen") || name.contains("shake")) {
                    return "COLD";
                }
                if (name.contains("soup") || name.contains("steak") || name.contains("pizza")
                        || name.contains("burger") || name.contains("hot") || name.contains("bowl")
                        || name.contains("burrito") || name.contains("curry")) {
                    return "HOT";
                }
            }
        }
        return "AMBIENT";
    }

    @Override
    @Transactional
    public BatchDtos.BatchDto claimBatch(final Long batchId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null || (!"ROLE_DRIVER".equals(currentUser.getRole()) && !"ROLE_ADMIN".equals(currentUser.getRole()))) {
            throw new SecurityException("Only drivers can claim delivery batches");
        }

        DeliveryBatch batch = deliveryBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id " + batchId));

        if (!"PROPOSED".equals(batch.getStatus())) {
            throw new IllegalStateException("Batch is not available to claim; current status: " + batch.getStatus());
        }

        DriverStats driverStats = driverStatsRepository.findByUsername(currentUser.getUsername())
                .orElseGet(() -> {
                    DriverStats ds = new DriverStats();
                    ds.setUsername(currentUser.getUsername());
                    ds.setTotalEarnings(BigDecimal.ZERO);
                    ds.setActiveOrders(0);
                    ds.setTotalDeliveries(0);
                    return driverStatsRepository.save(ds);
                });

        // Atomically claim all constituent orders
        Set<Long> orderIds = batch.getStops().stream()
                .map(BatchStop::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Long oId : orderIds) {
            Order order = orderRepository.findById(oId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + oId));
            if (!"Placed".equals(order.getStatus()) && !"Claimed".equals(order.getStatus())) {
                throw new IllegalStateException("Order #" + oId + " is no longer available to claim (status: " + order.getStatus() + ")");
            }
            order.setStatus("Claimed");
            order.setDriver(driverStats);
            orderRepository.save(order);
        }

        batch.setStatus("CLAIMED");
        batch.setDriver(driverStats);
        batch.setClaimedAt(LocalDateTime.now());
        batch.setCurrentStopIndex(0);

        driverStats.setActiveOrders(driverStats.getActiveOrders() + orderIds.size());
        driverStatsRepository.save(driverStats);

        DeliveryBatch saved = deliveryBatchRepository.save(batch);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public BatchDtos.BatchStepResponseDto advanceBatchStep(final Long batchId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null || (!"ROLE_DRIVER".equals(currentUser.getRole()) && !"ROLE_ADMIN".equals(currentUser.getRole()))) {
            throw new SecurityException("Only assigned drivers can advance delivery batch steps");
        }

        DeliveryBatch batch = deliveryBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id " + batchId));

        if (!"CLAIMED".equals(batch.getStatus()) && !"IN_TRANSIT".equals(batch.getStatus())) {
            throw new IllegalStateException("Cannot advance step for batch in status: " + batch.getStatus());
        }

        if (batch.getDriver() != null && !currentUser.getUsername().equals(batch.getDriver().getUsername())) {
            throw new SecurityException("You are not assigned to this delivery batch");
        }

        List<BatchStop> stops = batch.getStops();
        int currentIndex = batch.getCurrentStopIndex() != null ? batch.getCurrentStopIndex() : 0;

        if (currentIndex >= stops.size()) {
            BatchDtos.BatchStepResponseDto resp = new BatchDtos.BatchStepResponseDto();
            resp.setBatchId(batchId);
            resp.setStatus(batch.getStatus());
            resp.setMessage("All stops have already been completed.");
            resp.setBatchCompleted(true);
            return resp;
        }

        BatchStop currentStop = stops.get(currentIndex);
        currentStop.setStatus("COMPLETED");
        currentStop.setCompletedAt(LocalDateTime.now());
        batchStopRepository.save(currentStop);

        Long orderId = currentStop.getOrderId();
        Order order = orderId != null ? orderRepository.findById(orderId).orElse(null) : null;

        String stepMessage;
        if ("PICKUP".equalsIgnoreCase(currentStop.getStopType())) {
            if (order != null) {
                order.setStatus("Picked Up");
                orderRepository.save(order);
            }
            stepMessage = "Picked up " + currentStop.getOrderName() + " at " + currentStop.getLocationName();
        } else {
            // DROPOFF
            if (order != null) {
                order.setStatus("Delivered");
                order.setIsFulfilled(true);
                orderRepository.save(order);
                BigDecimal fee = order.getDeliveryCost() != null ? order.getDeliveryCost() : DEFAULT_DELIVERY_FEE;
                driverStatsService.updateTotalEarnings(currentUser.getUsername(), fee);
            }
            stepMessage = "Delivered " + currentStop.getOrderName() + " to " + currentStop.getLocationName();
        }

        int nextIndex = currentIndex + 1;
        batch.setCurrentStopIndex(nextIndex);

        // Check if all pickups done -> switch to IN_TRANSIT
        boolean allPickupsDone = stops.stream()
                .filter(s -> "PICKUP".equalsIgnoreCase(s.getStopType()))
                .allMatch(s -> "COMPLETED".equalsIgnoreCase(s.getStatus()));
        if (allPickupsDone && "CLAIMED".equals(batch.getStatus())) {
            batch.setStatus("IN_TRANSIT");
        }

        boolean batchCompleted = nextIndex >= stops.size();
        BigDecimal totalBonusAwarded = BigDecimal.ZERO;

        if (batchCompleted) {
            batch.setStatus("COMPLETED");
            batch.setCompletedAt(LocalDateTime.now());

            // Award batch bonus to driver
            if (batch.getBatchBonus() != null && batch.getBatchBonus().compareTo(BigDecimal.ZERO) > 0) {
                DriverStats ds = driverStatsRepository.findByUsername(currentUser.getUsername()).orElse(null);
                if (ds != null) {
                    ds.setTotalEarnings(ds.getTotalEarnings().add(batch.getBatchBonus()));
                    driverStatsRepository.save(ds);
                    totalBonusAwarded = batch.getBatchBonus();
                }
            }
        }

        deliveryBatchRepository.save(batch);

        BatchDtos.BatchStepResponseDto resp = new BatchDtos.BatchStepResponseDto();
        resp.setBatchId(batch.getId());
        resp.setCurrentStopIndex(nextIndex);
        resp.setTotalStops(stops.size());
        resp.setStatus(batch.getStatus());
        resp.setMessage(stepMessage);
        resp.setCompletedStop(new BatchDtos.BatchStopDto(currentStop));
        resp.setNextStop(nextIndex < stops.size() ? new BatchDtos.BatchStopDto(stops.get(nextIndex)) : null);
        resp.setBatchCompleted(batchCompleted);
        resp.setTotalEarningsAwarded(totalBonusAwarded);

        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public BatchDtos.BatchDto getActiveBatchForDriver(final String username) {
        return deliveryBatchRepository.findFirstByDriverUsernameAndStatusInOrderByClaimedAtDesc(
                        username, List.of("CLAIMED", "IN_TRANSIT"))
                .map(this::mapToDto)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchDtos.BatchDto getBatchById(final Long batchId) {
        return deliveryBatchRepository.findById(batchId)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id " + batchId));
    }

    private BatchDtos.BatchDto mapToDto(final DeliveryBatch batch) {
        BatchDtos.BatchDto dto = new BatchDtos.BatchDto();
        dto.setId(batch.getId());
        dto.setBatchCode(batch.getBatchCode());
        dto.setDriverUsername(batch.getDriver() != null ? batch.getDriver().getUsername() : null);
        dto.setStatus(batch.getStatus());
        dto.setTotalDistanceMiles(batch.getTotalDistanceMiles());
        dto.setEstimatedDurationMinutes(batch.getEstimatedDurationMinutes());
        dto.setBasePayout(batch.getBasePayout());
        dto.setBatchBonus(batch.getBatchBonus());
        dto.setTotalPayout(batch.getTotalPayout());
        dto.setThermalDegradationRisk(batch.getThermalDegradationRisk());
        dto.setCurrentStopIndex(batch.getCurrentStopIndex());
        if (batch.getStops() != null) {
            dto.setStops(batch.getStops().stream().map(BatchDtos.BatchStopDto::new).collect(Collectors.toList()));
        }
        return dto;
    }
}
