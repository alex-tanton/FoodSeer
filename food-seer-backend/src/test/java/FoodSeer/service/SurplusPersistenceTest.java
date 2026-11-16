package FoodSeer.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import FoodSeer.dto.SurplusOfferRequest;
import FoodSeer.dto.SurplusReservationRequest;
import FoodSeer.entity.Food;
import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.User;
import FoodSeer.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Uses real transactions and row locks rather than mocked repositories. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(SurplusService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SurplusPersistenceTest {
    @Autowired private SurplusService service;
    @Autowired private SurplusOfferRepository offers;
    @Autowired private SurplusReservationRepository reservations;
    @Autowired private FoodRepository foods;
    @Autowired private UserRepository users;
    private Instant now;
    private Long offerId;

    @BeforeEach
    void setup() {
        reservations.deleteAll();
        offers.deleteAll();
        if (!users.existsByUsername("surplus-alice")) {
            users.save(User.builder().username("surplus-alice").email("surplus-alice@test.example")
                    .password("unused").build());
        }
        now = Instant.now();
        Food food = foods.save(new Food("Surplus soup", 10, 10, List.of("MILK")));
        offerId = service.publish(new SurplusOfferRequest(food.getId(), "Milk and vegetables",
                new BigDecimal("3.25"), 1, "Front desk", now.minusSeconds(60), now.plusSeconds(7200),
                now.plusSeconds(3600))).id();
    }

    @Test
    void twoCustomersCompetingForLastPortionCannotOversell() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                service.reserve(offerId, "surplus-alice", new SurplusReservationRequest(1, now.plusSeconds(600)));
                return true;
            } catch (IllegalStateException expected) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(0, offers.findById(offerId).orElseThrow().getRemainingPortions());
            assertEquals(1, reservations.count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancellationPersistsAndMakesLastPortionAvailableAgain() {
        var reservation = service.reserve(offerId, "surplus-alice", new SurplusReservationRequest(1, now.plusSeconds(600)));
        assertTrue(service.browse(false).stream().noneMatch(offer -> offer.id().equals(offerId)));
        service.cancel(reservation.id(), "surplus-alice");
        assertEquals(1, offers.findById(offerId).orElseThrow().getRemainingPortions());
        assertThrows(IllegalStateException.class, () -> service.cancel(reservation.id(), "surplus-alice"));
        assertEquals(1, offers.findById(offerId).orElseThrow().getRemainingPortions());
        var next = service.reserve(offerId, "surplus-alice", new SurplusReservationRequest(1, now.plusSeconds(600)));
        service.collect(next.id());
        assertEquals("COLLECTED", service.listReservations("surplus-alice", false).getFirst().status());
        assertEquals(0, offers.findById(offerId).orElseThrow().getRemainingPortions());
    }

    @Test
    void browseFiltersExpiredAndSoldOutOffersInDatabase() {
        SurplusOffer expired = offers.findById(offerId).orElseThrow();
        expired.setExpiresAt(now.minusSeconds(1));
        offers.save(expired);
        assertTrue(service.browse(false).stream().noneMatch(offer -> offer.id().equals(offerId)));
        assertTrue(service.browse(true).stream().anyMatch(offer -> offer.id().equals(offerId)));
    }

    @Test
    void knownAllergyConflictRollsBackWithoutConsumingStock() {
        User user = users.findByUsername("surplus-alice").orElseThrow();
        user.setDietaryRestrictions("dairy");
        users.save(user);
        try {
            assertThrows(IllegalArgumentException.class, () -> service.reserve(offerId, "surplus-alice",
                    new SurplusReservationRequest(1, now.plusSeconds(600))));
            assertEquals(1, offers.findById(offerId).orElseThrow().getRemainingPortions());
            assertEquals(0, reservations.count());
        } finally {
            user.setDietaryRestrictions(null);
            users.save(user);
        }
    }
}
