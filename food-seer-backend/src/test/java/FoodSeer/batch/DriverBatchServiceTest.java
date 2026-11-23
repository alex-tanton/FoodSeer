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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverBatchServiceTest {

    @Mock
    private DeliveryBatchRepository deliveryBatchRepository;

    @Mock
    private BatchStopRepository batchStopRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserService userService;

    @Mock
    private DriverStatsRepository driverStatsRepository;

    @Mock
    private DriverStatsService driverStatsService;

    @InjectMocks
    private DriverBatchServiceImpl batchService;

    private User driverUser;
    private DriverStats driverStats;

    @BeforeEach
    void setUp() {
        driverUser = new User();
        driverUser.setUsername("driver_bob");
        driverUser.setRole("ROLE_DRIVER");

        driverStats = new DriverStats();
        driverStats.setUsername("driver_bob");
        driverStats.setTotalEarnings(BigDecimal.valueOf(20.00));
        driverStats.setActiveOrders(0);
        driverStats.setTotalDeliveries(2);
    }

    @Test
    void getSuggestedBatchesReturnsEmptyWhenNoPlacedOrders() {
        when(deliveryBatchRepository.findByStatus("PROPOSED")).thenReturn(Collections.emptyList());
        when(orderRepository.findByStatus("Placed")).thenReturn(Collections.emptyList());

        List<BatchDtos.BatchDto> suggestions = batchService.getSuggestedBatches();
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void getSuggestedBatchesClustersPlacedOrders() {
        when(deliveryBatchRepository.findByStatus("PROPOSED")).thenReturn(Collections.emptyList());

        Food hotPizza = new Food("Hot Margherita Pizza", 10, 15, List.of("GLUTEN"));
        Order order1 = new Order();
        order1.setId(101L);
        order1.setName("Order #101");
        order1.setStatus("Placed");
        order1.setDeliveryCost(BigDecimal.valueOf(4.00));
        order1.setFoods(List.of(hotPizza));

        Food coldIceCream = new Food("Vanilla Gelato Sundae", 5, 8, List.of("DAIRY"));
        Order order2 = new Order();
        order2.setId(102L);
        order2.setName("Order #102");
        order2.setStatus("Placed");
        order2.setDeliveryCost(BigDecimal.valueOf(3.50));
        order2.setFoods(List.of(coldIceCream));

        when(orderRepository.findByStatus("Placed")).thenReturn(List.of(order1, order2));
        when(deliveryBatchRepository.save(any(DeliveryBatch.class))).thenAnswer(invocation -> {
            DeliveryBatch b = invocation.getArgument(0);
            b.setId(1L);
            return b;
        });

        List<BatchDtos.BatchDto> result = batchService.getSuggestedBatches();

        assertEquals(1, result.size());
        BatchDtos.BatchDto batch = result.get(0);
        assertEquals("PROPOSED", batch.getStatus());
        assertEquals(BigDecimal.valueOf(7.50), batch.getBasePayout());
        assertEquals(BigDecimal.valueOf(2.50), batch.getBatchBonus());
        assertEquals(BigDecimal.valueOf(10.00), batch.getTotalPayout());
        assertEquals(4, batch.getStops().size());
    }

    @Test
    void claimBatchRejectsNonDriver() {
        User customer = new User();
        customer.setUsername("cust1");
        customer.setRole("ROLE_CUSTOMER");
        when(userService.getCurrentUser()).thenReturn(customer);

        assertThrows(SecurityException.class, () -> batchService.claimBatch(1L));
    }

    @Test
    void claimBatchRejectsAlreadyClaimedBatch() {
        when(userService.getCurrentUser()).thenReturn(driverUser);
        DeliveryBatch batch = new DeliveryBatch();
        batch.setId(1L);
        batch.setStatus("CLAIMED");
        when(deliveryBatchRepository.findById(1L)).thenReturn(Optional.of(batch));

        assertThrows(IllegalStateException.class, () -> batchService.claimBatch(1L));
    }

    @Test
    void claimBatchSuccessfullyAssignsDriverAndUpdatesOrders() {
        when(userService.getCurrentUser()).thenReturn(driverUser);

        DeliveryBatch batch = new DeliveryBatch();
        batch.setId(1L);
        batch.setStatus("PROPOSED");

        BatchStop s1 = new BatchStop();
        s1.setOrderId(101L);
        s1.setStopType("PICKUP");
        BatchStop s2 = new BatchStop();
        s2.setOrderId(101L);
        s2.setStopType("DROPOFF");
        batch.setStops(List.of(s1, s2));

        Order order = new Order();
        order.setId(101L);
        order.setStatus("Placed");

        when(deliveryBatchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(driverStatsRepository.findByUsername("driver_bob")).thenReturn(Optional.of(driverStats));
        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(deliveryBatchRepository.save(any(DeliveryBatch.class))).thenAnswer(i -> i.getArgument(0));

        BatchDtos.BatchDto claimed = batchService.claimBatch(1L);

        assertEquals("CLAIMED", claimed.getStatus());
        assertEquals("driver_bob", claimed.getDriverUsername());
        assertEquals("Claimed", order.getStatus());
        assertEquals("driver_bob", order.getDriver().getUsername());
        verify(orderRepository).save(order);
        verify(driverStatsRepository).save(driverStats);
    }

    @Test
    void advanceBatchStepHandlesPickupAndTransitionsToInTransit() {
        when(userService.getCurrentUser()).thenReturn(driverUser);

        DeliveryBatch batch = new DeliveryBatch();
        batch.setId(1L);
        batch.setStatus("CLAIMED");
        batch.setDriver(driverStats);
        batch.setCurrentStopIndex(0);

        BatchStop s1 = new BatchStop();
        s1.setId(11L);
        s1.setOrderId(101L);
        s1.setOrderName("Order #101");
        s1.setLocationName("Tuscany Bistro");
        s1.setStopType("PICKUP");
        s1.setStatus("PENDING");

        BatchStop s2 = new BatchStop();
        s2.setId(12L);
        s2.setOrderId(101L);
        s2.setOrderName("Order #101");
        s2.setLocationName("Talley Union");
        s2.setStopType("DROPOFF");
        s2.setStatus("PENDING");

        batch.setStops(List.of(s1, s2));

        Order order = new Order();
        order.setId(101L);
        order.setStatus("Claimed");

        when(deliveryBatchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));

        BatchDtos.BatchStepResponseDto resp = batchService.advanceBatchStep(1L);

        assertEquals(1, resp.getCurrentStopIndex());
        assertEquals("IN_TRANSIT", batch.getStatus());
        assertEquals("Picked Up", order.getStatus());
        assertEquals("COMPLETED", s1.getStatus());
        assertFalse(resp.isBatchCompleted());
        verify(batchStopRepository).save(s1);
    }

    @Test
    void advanceBatchStepCompletesBatchAndAwardsBonus() {
        when(userService.getCurrentUser()).thenReturn(driverUser);

        DeliveryBatch batch = new DeliveryBatch();
        batch.setId(1L);
        batch.setStatus("IN_TRANSIT");
        batch.setDriver(driverStats);
        batch.setCurrentStopIndex(1);
        batch.setBatchBonus(BigDecimal.valueOf(5.00));

        BatchStop s1 = new BatchStop();
        s1.setId(11L);
        s1.setOrderId(101L);
        s1.setStopType("PICKUP");
        s1.setStatus("COMPLETED");

        BatchStop s2 = new BatchStop();
        s2.setId(12L);
        s2.setOrderId(101L);
        s2.setOrderName("Order #101");
        s2.setLocationName("Talley Union");
        s2.setStopType("DROPOFF");
        s2.setStatus("PENDING");

        batch.setStops(List.of(s1, s2));

        Order order = new Order();
        order.setId(101L);
        order.setStatus("Picked Up");
        order.setDeliveryCost(BigDecimal.valueOf(4.00));

        when(deliveryBatchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(driverStatsRepository.findByUsername("driver_bob")).thenReturn(Optional.of(driverStats));

        BatchDtos.BatchStepResponseDto resp = batchService.advanceBatchStep(1L);

        assertEquals(2, resp.getCurrentStopIndex());
        assertTrue(resp.isBatchCompleted());
        assertEquals("COMPLETED", batch.getStatus());
        assertEquals("Delivered", order.getStatus());
        assertTrue(order.getIsFulfilled());
        assertEquals(BigDecimal.valueOf(5.00), resp.getTotalEarningsAwarded());

        verify(driverStatsService).updateTotalEarnings("driver_bob", BigDecimal.valueOf(4.00));
        assertEquals(BigDecimal.valueOf(25.00), driverStats.getTotalEarnings()); // 20 + 5 bonus
    }

    @Test
    void getActiveBatchForDriverReturnsActiveBatch() {
        DeliveryBatch batch = new DeliveryBatch();
        batch.setId(2L);
        batch.setStatus("IN_TRANSIT");
        batch.setDriver(driverStats);

        when(deliveryBatchRepository.findFirstByDriverUsernameAndStatusInOrderByClaimedAtDesc(
                eq("driver_bob"), any())).thenReturn(Optional.of(batch));

        BatchDtos.BatchDto active = batchService.getActiveBatchForDriver("driver_bob");
        assertNotNull(active);
        assertEquals(2L, active.getId());
        assertEquals("IN_TRANSIT", active.getStatus());
    }

    @Test
    void getBatchByIdThrowsWhenNotFound() {
        when(deliveryBatchRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> batchService.getBatchById(999L));
    }
}
