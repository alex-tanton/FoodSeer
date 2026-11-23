package FoodSeer.batch;

import FoodSeer.entity.DriverStats;
import FoodSeer.entity.Food;
import FoodSeer.entity.Order;
import FoodSeer.entity.User;
import FoodSeer.repositories.DriverStatsRepository;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.OrderRepository;
import FoodSeer.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds driver, customer, food items, and placed orders for the Driver Batch Dispatcher demo.
 * Runs only under the {@code demo} profile.
 */
@Component
@Profile("demo")
public class DriverBatchDemoSeeder {

    public static final String DRIVER_USERNAME = "demo_driver";
    public static final String CUSTOMER_USERNAME = "demo_customer";
    public static final String PASSWORD = "password";

    private final UserRepository userRepository;
    private final DriverStatsRepository driverStatsRepository;
    private final FoodRepository foodRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final DriverBatchService batchService;

    @Autowired
    public DriverBatchDemoSeeder(
            final UserRepository userRepository,
            final DriverStatsRepository driverStatsRepository,
            final FoodRepository foodRepository,
            final OrderRepository orderRepository,
            final PasswordEncoder passwordEncoder,
            final DriverBatchService batchService) {
        this.userRepository = userRepository;
        this.driverStatsRepository = driverStatsRepository;
        this.foodRepository = foodRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
        this.batchService = batchService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        // Seed driver
        User driverUser = userRepository.findByUsername(DRIVER_USERNAME).orElse(null);
        if (driverUser == null) {
            User newDriver = new User();
            newDriver.setUsername(DRIVER_USERNAME);
            newDriver.setEmail("driver@foodseer.test");
            newDriver.setPassword(passwordEncoder.encode(PASSWORD));
            newDriver.setRole("ROLE_DRIVER");
            userRepository.save(newDriver);
        }

        if (driverStatsRepository.findByUsername(DRIVER_USERNAME).isEmpty()) {
            DriverStats stats = new DriverStats();
            stats.setUsername(DRIVER_USERNAME);
            stats.setTotalEarnings(BigDecimal.valueOf(45.50));
            stats.setTotalDeliveries(6);
            stats.setActiveOrders(0);
            stats.setAverageRating(BigDecimal.valueOf(4.9));
            driverStatsRepository.save(stats);
        }

        // Seed customer
        User customer = userRepository.findByUsername(CUSTOMER_USERNAME).orElse(null);
        if (customer == null) {
            User newCustomer = new User();
            newCustomer.setUsername(CUSTOMER_USERNAME);
            newCustomer.setEmail("customer@foodseer.test");
            newCustomer.setPassword(passwordEncoder.encode(PASSWORD));
            newCustomer.setRole("ROLE_USER");
            userRepository.save(newCustomer);
        }

        // Seed food items with distinct thermal profiles
        Food ramen = seedFood("Spicy Beef Ramen", 15, 14, List.of("GLUTEN", "SOY"));
        Food gelato = seedFood("Artisan Gelato Sundae", 20, 8, List.of("DAIRY"));
        Food pizza = seedFood("Loaded Margherita Pizza", 25, 16, List.of("DAIRY", "GLUTEN"));
        Food salad = seedFood("Organic Acai Superfood Bowl", 18, 11, List.of("NUTS"));
        Food burrito = seedFood("Grilled Chicken Hot Burrito", 30, 12, List.of("GLUTEN"));

        // Seed placed orders if none exist
        if (orderRepository.findByStatus("Placed").isEmpty()) {
            createPlacedOrder("Campus Lunch Order A", customer, List.of(ramen, pizza),
                    BigDecimal.valueOf(30.00), BigDecimal.valueOf(4.50), "DAIRY, GLUTEN");

            createPlacedOrder("Dessert Delivery B", customer, List.of(gelato),
                    BigDecimal.valueOf(8.00), BigDecimal.valueOf(3.75), "DAIRY");

            createPlacedOrder("Library Snack Order C", customer, List.of(salad),
                    BigDecimal.valueOf(11.00), BigDecimal.valueOf(4.00), "NUTS");

            createPlacedOrder("Evening Hot Burrito Order D", customer, List.of(burrito),
                    BigDecimal.valueOf(12.00), BigDecimal.valueOf(4.25), null);

            // Pre-generate suggested batches
            batchService.getSuggestedBatches();
        }
    }

    private Food seedFood(final String name, final int amount, final int price, final List<String> allergies) {
        Food existing = foodRepository.findByFoodName(name.toUpperCase()).orElse(null);
        if (existing != null) {
            return existing;
        }
        Food food = new Food(name, amount, price, allergies);
        return foodRepository.save(food);
    }

    private void createPlacedOrder(
            final String name, final User user, final List<Food> foods,
            final BigDecimal cost, final BigDecimal deliveryFee, final String allergies) {
        Order order = new Order();
        order.setName(name);
        order.setUser(user);
        order.setFoods(new ArrayList<>(foods));
        order.setCost(cost);
        order.setDeliveryCost(deliveryFee);
        order.setStatus("Placed");
        order.setIsFulfilled(false);
        order.setAllergyRestrictions(allergies);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        orderRepository.save(order);
    }
}
