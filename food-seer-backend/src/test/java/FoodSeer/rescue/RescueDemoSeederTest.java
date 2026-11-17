package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import FoodSeer.dto.CalorieBudgetDto;
import FoodSeer.entity.SurplusOffer;
import FoodSeer.repositories.SurplusOfferRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.CalorieBudgetService;
import FoodSeer.service.SurplusService;
import FoodSeer.service.impl.DataInitializer;

/** The demo profile seeds a world whose preview shows every matcher outcome. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("demo")
@Import({RescueDemoSeeder.class, SurplusService.class, DataInitializer.class, RescueService.class,
    RescueCandidateFactory.class, RescueDemoSeederTest.Encoder.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RescueDemoSeederTest {

    @TestConfiguration
    static class Encoder {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired private RescueDemoSeeder seeder;
    @Autowired private RescueService rescue;
    @Autowired private SurplusOfferRepository offers;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @MockBean private CalorieBudgetService budgets;

    private SurplusOffer offer;

    @BeforeEach
    void setup() {
        when(budgets.getBudgetFor(any()))
                .thenReturn(new CalorieBudgetDto(null, false, null, false, 0, List.of(), null));
        seeder.seed();
        offer = offers.findAll().stream().filter(o -> "STEAK".equals(o.getFoodName())).findFirst().orElseThrow();
    }

    @Test
    void seedsStaffCustomersAndOneLiveListing() {
        assertEquals("ROLE_STAFF", users.findByUsername("demo_staff").orElseThrow().getRole());
        for (String customer : List.of("maya", "leo", "ana", "vic", "tara", "ben")) {
            assertEquals("ROLE_CUSTOMER", users.findByUsername(customer).orElseThrow().getRole(), customer);
        }
        assertTrue(encoder.matches("rescue123", users.findByUsername("maya").orElseThrow().getPassword()));
        assertEquals(1, offer.getRemainingPortions());
        assertEquals(0, new java.math.BigDecimal("12.00").compareTo(offer.getDiscountedPrice()));
    }

    @Test
    void previewShowsEveryOutcome() {
        RescueDtos.Preview preview = rescue.preview(offer.getId());

        assertEquals(List.of("maya", "leo", "ana"),
                preview.ranked().stream().map(RescueDtos.Match::username).toList());
        assertEquals(List.of(true, true, false),
                preview.ranked().stream().map(RescueDtos.Match::willAlert).toList());
        assertTrue(preview.ranked().get(0).reason().startsWith("you ordered this 3 times · 57% off"));
        Map<String, Exclusion> excluded = preview.excluded().stream()
                .collect(Collectors.toMap(RescueDtos.Excluded::username, RescueDtos.Excluded::reason));
        assertEquals(Exclusion.ALLERGEN, excluded.get("vic"));
        assertEquals(Exclusion.UNCERTAIN_RESTRICTION, excluded.get("tara"));
        assertEquals(Exclusion.PRICE, excluded.get("ben"));
    }

    @Test
    void seedingTwiceChangesNothing() {
        long before = users.count();
        assertFalse(seeder.seed());
        assertEquals(before, users.count());
        assertEquals(1, offers.findAll().stream().filter(o -> "STEAK".equals(o.getFoodName())).count());
    }
}
