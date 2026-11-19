package FoodSeer.group;

import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import FoodSeer.entity.Food;
import FoodSeer.entity.User;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.UserRepository;

/**
 * Pre-seeds a ready-to-demo group dining room (code DEMO26) under the "demo" profile.
 * Zero-setup presentation for tutors and reviewers.
 */
@Component
@Profile("demo")
public class GroupDiningDemoSeeder {

    public static final String DEMO_ROOM_CODE = "DEMO26";
    private static final String PASSWORD = "rescue123";

    private final GroupSessionRepository sessionRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupCartItemRepository cartItemRepository;
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public GroupDiningDemoSeeder(final GroupSessionRepository sessionRepository,
            final GroupMemberRepository memberRepository, final GroupCartItemRepository cartItemRepository,
            final FoodRepository foodRepository, final UserRepository userRepository,
            final PasswordEncoder passwordEncoder) {
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
        this.cartItemRepository = cartItemRepository;
        this.foodRepository = foodRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoRoom() {
        if (sessionRepository.existsBySessionCode(DEMO_ROOM_CODE)) {
            return;
        }

        // Ensure host_alex exists
        final User alex = ensureUser("host_alex", "alex@foodseer.demo", "ROLE_CUSTOMER", null, "moderate");
        final User leo = ensureUser("leo", "leo@foodseer.demo", "ROLE_CUSTOMER", "PEANUTS", "budget");
        final User maya = ensureUser("maya", "maya@foodseer.demo", "ROLE_CUSTOMER", "GLUTEN", "moderate");

        final GroupSession session = new GroupSession(DEMO_ROOM_CODE, "Library Study Group", alex.getUsername());
        final GroupSession savedSession = sessionRepository.save(session);

        final GroupMember memberAlex = new GroupMember(savedSession, alex.getUsername(), "Alex (Host)", null, "moderate");
        final GroupMember memberLeo = new GroupMember(savedSession, leo.getUsername(), "Leo", "PEANUTS", "budget");
        final GroupMember memberMaya = new GroupMember(savedSession, maya.getUsername(), "Maya", "GLUTEN", "moderate");

        memberRepository.save(memberAlex);
        memberRepository.save(memberLeo);
        memberRepository.save(memberMaya);

        // Pre-populate tray items demonstrating safety and cross-contamination alerts
        final Optional<Food> saladOpt = foodRepository.findAll().stream()
                .filter(f -> f.getFoodName().equalsIgnoreCase("GARDEN SALAD")).findFirst();
        final Optional<Food> pizzaOpt = foodRepository.findAll().stream()
                .filter(f -> f.getFoodName().equalsIgnoreCase("PIZZA SLICE")).findFirst();
        final Optional<Food> cookiesOpt = foodRepository.findAll().stream()
                .filter(f -> f.getFoodName().equalsIgnoreCase("COOKIES")).findFirst();

        if (saladOpt.isPresent()) {
            final Food salad = saladOpt.get();
            cartItemRepository.save(new GroupCartItem(savedSession, memberAlex, salad.getId(), salad.getFoodName(),
                    salad.getPrice(), 1, ""));
        }

        if (pizzaOpt.isPresent()) {
            final Food pizza = pizzaOpt.get();
            cartItemRepository.save(new GroupCartItem(savedSession, memberAlex, pizza.getId(), pizza.getFoodName(),
                    pizza.getPrice(), 2, "Extra crispy"));
        }

        if (cookiesOpt.isPresent()) {
            final Food cookies = cookiesOpt.get();
            // Cookies contain PEANUTS & GLUTEN, triggering cross-contamination alert for Leo!
            cartItemRepository.save(new GroupCartItem(savedSession, memberAlex, cookies.getId(), cookies.getFoodName(),
                    cookies.getPrice(), 1, "Dessert"));
        }
    }

    private User ensureUser(final String username, final String email, final String role,
            final String restrictions, final String costPref) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            final User u = new User();
            u.setUsername(username);
            u.setEmail(email);
            u.setPassword(passwordEncoder.encode(PASSWORD));
            u.setRole(role);
            u.setDietaryRestrictions(restrictions);
            u.setCostPreference(costPref);
            return userRepository.save(u);
        });
    }
}
