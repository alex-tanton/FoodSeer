package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import FoodSeer.dto.CalorieBudgetDto;
import FoodSeer.entity.Allergen;
import FoodSeer.entity.Food;
import FoodSeer.entity.Order;
import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.SurplusReservation;
import FoodSeer.entity.User;
import FoodSeer.repositories.AllergenRepository;
import FoodSeer.repositories.OrderRepository;
import FoodSeer.repositories.SurplusReservationRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.CalorieBudgetService;

class RescueCandidateFactoryTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final Instant NOW = Instant.parse("2026-11-17T18:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CalorieBudgetService budgets = mock(CalorieBudgetService.class);
    private final SurplusReservationRepository reservations = mock(SurplusReservationRepository.class);
    private final RescueAlertRepository alerts = mock(RescueAlertRepository.class);
    private final AllergenRepository allergens = mock(AllergenRepository.class);
    private final RescueCandidateFactory factory = new RescueCandidateFactory(users, orders, budgets, reservations,
            alerts, allergens);

    private SurplusOffer offer;
    private Food pasta;
    private Food salad;
    private User ann;

    @BeforeEach
    void setup() {
        pasta = new Food("PASTA", 10, 14, List.of("WHEAT"));
        pasta.setId(10L);
        salad = new Food("SALAD", 10, 10, List.of());
        salad.setId(11L);
        offer = new SurplusOffer();
        offer.setId(5L);
        offer.setFoodId(10L);
        offer.setFoodName("PASTA");
        offer.setAllergens(new ArrayList<>(List.of("WHEAT", "EGGS")));
        offer.setCalories(600);
        offer.setOriginalPrice(new BigDecimal("14.00"));
        offer.setDiscountedPrice(new BigDecimal("7.00"));
        offer.setRemainingPortions(4);
        offer.setExpiresAt(NOW.plusSeconds(3600));
        ann = User.builder().username("ann").role("ROLE_CUSTOMER").dietaryRestrictions("SOY")
                .costPreference("moderate").build();
        when(budgets.getBudgetFor(any())).thenReturn(budget(null));
    }

    private static CalorieBudgetDto budget(final Integer remaining) {
        return new CalorieBudgetDto(null, remaining != null, null, remaining != null, 0, List.of(), remaining);
    }

    private static Order order(final LocalDateTime at, final Food... foods) {
        Order order = new Order(null, "o");
        order.setCreatedAt(at);
        for (Food food : foods) {
            order.addFood(food);
        }
        return order;
    }

    private SurplusReservation reservation(final Instant at, final int quantity, final Integer calories,
            final SurplusReservation.Status status) {
        SurplusOffer reserved = new SurplusOffer();
        reserved.setId(99L);
        reserved.setCalories(calories);
        SurplusReservation reservation = new SurplusReservation();
        reservation.setOffer(reserved);
        reservation.setCreatedAt(at);
        reservation.setQuantity(quantity);
        reservation.setStatus(status);
        return reservation;
    }

    @Test
    void listingSnapshotsTheOffer() {
        RescueListing listing = factory.listingOf(offer);
        assertEquals(5L, listing.offerId());
        assertEquals(10L, listing.foodId());
        assertEquals(List.of("WHEAT", "EGGS"), listing.allergens());
        assertEquals(600, listing.calories());
        assertEquals(new BigDecimal("7.00"), listing.discountedPrice());
        assertEquals(4, listing.remainingPortions());
    }

    @Test
    void knownAllergensComeFromTheSharedList() {
        when(allergens.findAll()).thenReturn(List.of(new Allergen("MILK"), new Allergen("SOY")));
        assertEquals(List.of("MILK", "SOY"), factory.knownAllergens());
    }

    @Test
    void onlyCustomersBecomeCandidatesInUsernameOrder() {
        User staff = User.builder().username("sam").role("ROLE_STAFF").build();
        User driver = User.builder().username("dee").role("ROLE_DRIVER").build();
        User bob = User.builder().username("bob").role("ROLE_CUSTOMER").build();
        when(users.findAll()).thenReturn(List.of(staff, ann, driver, bob));
        List<RescueCandidate> result = factory.candidatesFor(offer, NOW, UTC);
        assertEquals(List.of("ann", "bob"), result.stream().map(RescueCandidate::username).toList());
    }

    @Test
    void candidateCarriesSavedPreferences() {
        when(users.findAll()).thenReturn(List.of(ann));
        RescueCandidate candidate = factory.candidatesFor(offer, NOW, UTC).get(0);
        assertEquals("SOY", candidate.dietaryRestrictions());
        assertEquals("moderate", candidate.costPreference());
        assertNull(candidate.remainingCalories());
    }

    @Test
    void countsPastOrdersOfTheDishAndTheLatestOrder() {
        LocalDateTime latest = LocalDateTime.of(2026, 11, 16, 12, 0);
        when(orders.findByUser(ann)).thenReturn(List.of(order(LocalDateTime.of(2026, 11, 1, 12, 0), pasta),
                order(latest, salad), order(LocalDateTime.of(2026, 11, 10, 12, 0), pasta, salad),
                order(null, pasta)));
        when(users.findAll()).thenReturn(List.of(ann));
        RescueCandidate candidate = factory.candidatesFor(offer, NOW, UTC).get(0);
        assertEquals(3, candidate.pastOrdersOfDish());
        assertEquals(latest.toInstant(ZoneOffset.UTC), candidate.lastOrderAt());
    }

    @Test
    void customerWithNoOrdersHasNoHistory() {
        when(users.findAll()).thenReturn(List.of(ann));
        RescueCandidate candidate = factory.candidatesFor(offer, NOW, UTC).get(0);
        assertEquals(0, candidate.pastOrdersOfDish());
        assertNull(candidate.lastOrderAt());
    }

    @Test
    void alertsTodayUseTheZonesDayBoundaries() {
        when(users.findAll()).thenReturn(List.of(ann));
        when(alerts.countByRecipientUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("ann",
                Instant.parse("2026-11-17T00:00:00Z"), Instant.parse("2026-11-18T00:00:00Z"))).thenReturn(2L);
        when(alerts.existsByOfferIdAndRecipientUsername(5L, "ann")).thenReturn(true);
        RescueCandidate candidate = factory.candidatesFor(offer, NOW, UTC).get(0);
        assertEquals(2, candidate.alertsToday());
        assertTrue(candidate.alreadyAlerted());
    }

    @Test
    void remainingCaloriesSubtractSurplusReservedToday() {
        when(budgets.getBudgetFor(ann)).thenReturn(budget(1500));
        when(reservations.findByCustomerUsernameOrderByCreatedAtDesc("ann")).thenReturn(List.of(
                reservation(NOW.minusSeconds(3600), 2, 300, SurplusReservation.Status.RESERVED),
                reservation(NOW.minusSeconds(7200), 1, 200, SurplusReservation.Status.COLLECTED),
                reservation(NOW.minusSeconds(60), 1, 999, SurplusReservation.Status.CANCELLED),
                reservation(Instant.parse("2026-11-16T23:59:59Z"), 1, 999, SurplusReservation.Status.RESERVED),
                reservation(NOW.minusSeconds(30), 1, null, SurplusReservation.Status.RESERVED)));
        Integer remaining = factory.remainingCalories(ann, Instant.parse("2026-11-17T00:00:00Z"),
                Instant.parse("2026-11-18T00:00:00Z"));
        assertEquals(1500 - 600 - 200, remaining);
    }

    @Test
    void customersWhoDoNotTrackCaloriesStayUntracked() {
        when(reservations.findByCustomerUsernameOrderByCreatedAtDesc("ann")).thenReturn(
                List.of(reservation(NOW, 1, 300, SurplusReservation.Status.RESERVED)));
        assertNull(factory.remainingCalories(ann, NOW.minusSeconds(60), NOW.plusSeconds(60)));
        verifyNoInteractions(reservations);
    }
}
