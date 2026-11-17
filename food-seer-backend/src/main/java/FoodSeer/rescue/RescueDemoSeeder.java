package FoodSeer.rescue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import FoodSeer.dto.SurplusOfferRequest;
import FoodSeer.entity.Food;
import FoodSeer.entity.Order;
import FoodSeer.entity.User;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.OrderRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.SurplusService;
import FoodSeer.service.impl.DataInitializer;

/**
 * Seeds a ready-to-present rescue matching demo when the {@code demo} profile
 * is active. One STEAK portion is left over; the customers are chosen so the
 * preview shows every outcome:
 *
 * <ul>
 * <li>maya: ordered steak three times, recently: top match.</li>
 * <li>leo: ordered it once: second match.</li>
 * <li>ana: new customer: eligible but over the alert budget.</li>
 * <li>vic: vegetarian ("MEAT"): excluded, steak declares MEAT and BEEF.</li>
 * <li>tara: "tahini", which FoodSeer cannot map: excluded while unsure.</li>
 * <li>ben: budget preference ($10): excluded, the deal costs $12.</li>
 * </ul>
 *
 * Running it again changes nothing.
 */
@Component
@Profile("demo")
public class RescueDemoSeeder {

    static final String STAFF = "demo_staff";
    static final String PASSWORD = "rescue123";
    static final String DISH = "STEAK";

    private final UserRepository users;
    private final FoodRepository foods;
    private final OrderRepository orders;
    private final SurplusService surplus;
    private final DataInitializer dataInitializer;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public RescueDemoSeeder(final UserRepository users, final FoodRepository foods, final OrderRepository orders,
            final SurplusService surplus, final DataInitializer dataInitializer,
            final PasswordEncoder passwordEncoder) {
        this(users, foods, orders, surplus, dataInitializer, passwordEncoder, Clock.systemDefaultZone());
    }

    RescueDemoSeeder(final UserRepository users, final FoodRepository foods, final OrderRepository orders,
            final SurplusService surplus, final DataInitializer dataInitializer, final PasswordEncoder passwordEncoder,
            final Clock clock) {
        this.users = users;
        this.foods = foods;
        this.orders = orders;
        this.surplus = surplus;
        this.dataInitializer = dataInitializer;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        seed();
    }

    /**
     * Seeds the demo once.
     *
     * @return true if anything was created
     */
    public boolean seed() {
        if (users.existsByUsername(STAFF)) {
            return false;
        }
        // Both listeners run on ApplicationReadyEvent in no fixed order, so make
        // sure the inherited menu exists first. DataInitializer is idempotent.
        if (foods.count() == 0) {
            dataInitializer.onApplicationReady();
        }
        final Optional<Food> steak = foods.findAll().stream().filter(food -> DISH.equals(food.getFoodName()))
                .findFirst();
        if (steak.isEmpty()) {
            return false;
        }
        user(STAFF, "ROLE_STAFF", null, null);
        final User maya = user("maya", "ROLE_CUSTOMER", null, "premium");
        final User leo = user("leo", "ROLE_CUSTOMER", null, "moderate");
        user("ana", "ROLE_CUSTOMER", null, "no-limit");
        user("vic", "ROLE_CUSTOMER", "MEAT", "premium");
        user("tara", "ROLE_CUSTOMER", "tahini", "moderate");
        user("ben", "ROLE_CUSTOMER", null, "budget");

        final LocalDateTime now = LocalDateTime.now(clock);
        order(maya, steak.get(), now.minusDays(2));
        order(maya, steak.get(), now.minusDays(9));
        order(maya, steak.get(), now.minusDays(20));
        order(leo, steak.get(), now.minusDays(5));

        final Instant start = clock.instant();
        surplus.publish(new SurplusOfferRequest(steak.get().getId(), "Grilled steak, butter, herbs",
                new BigDecimal("12.00"), 1, "Front counter", start, start.plus(Duration.ofHours(2)),
                start.plus(Duration.ofMinutes(90))));
        return true;
    }

    private User user(final String username, final String role, final String restrictions,
            final String costPreference) {
        return users.save(User.builder().username(username).email(username + "@demo.foodseer")
                .password(passwordEncoder.encode(PASSWORD)).role(role).dietaryRestrictions(restrictions)
                .costPreference(costPreference).build());
    }

    private void order(final User user, final Food food, final LocalDateTime at) {
        final Order order = new Order(null, "Demo order");
        order.setUser(user);
        order.addFood(food);
        order.setCreatedAt(at);
        order.setStatus("Delivered");
        order.setIsFulfilled(true);
        order.setCost(BigDecimal.valueOf(food.getPrice()));
        orders.save(order);
    }
}
