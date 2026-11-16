package FoodSeer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import FoodSeer.dto.*;
import FoodSeer.entity.*;
import FoodSeer.entity.SurplusReservation.Status;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

class SurplusServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-16T18:00:00Z");
    private final SurplusOfferRepository offers = mock(SurplusOfferRepository.class);
    private final SurplusReservationRepository reservations = mock(SurplusReservationRepository.class);
    private final FoodRepository foods = mock(FoodRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SurplusService service = new SurplusService(offers, reservations, foods, users,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private SurplusOffer offer;
    private Food food;
    private User customer;

    @BeforeEach
    void setup() {
        food = new Food("Soup", 20, 10, new ArrayList<>(List.of("MILK")));
        food.setId(1L);
        food.setCalories(300);
        customer = User.builder().username("alice").build();
        when(foods.findById(1L)).thenReturn(Optional.of(food));
        when(users.findByUsername("alice")).thenReturn(Optional.of(customer));
        when(offers.save(any())).thenAnswer(call -> {
            SurplusOffer saved = call.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(reservations.save(any())).thenAnswer(call -> {
            SurplusReservation saved = call.getArgument(0);
            saved.setId(3L);
            return saved;
        });
        offer = new SurplusOffer();
        offer.setId(2L);
        offer.setFoodName("Soup");
        offer.setAllergens(new ArrayList<>(List.of("MILK")));
        offer.setDiscountedPrice(new BigDecimal("3.25"));
        offer.setRemainingPortions(5);
        offer.setPickupStart(NOW);
        offer.setPickupEnd(NOW.plusSeconds(7200));
        offer.setExpiresAt(NOW.plusSeconds(3600));
        when(offers.findLockedById(2L)).thenReturn(Optional.of(offer));
    }

    private SurplusOfferRequest request(BigDecimal price, Integer portions, Instant start, Instant end, Instant expiry) {
        return new SurplusOfferRequest(1L, " Milk, vegetables ", price, portions, " Front desk ", start, end, expiry);
    }

    private SurplusOfferRequest validRequest() {
        return request(new BigDecimal("3.25"), 5, NOW, NOW.plusSeconds(7200), NOW.plusSeconds(3600));
    }

    @Test
    void publishSnapshotsFoodWithoutTouchingRegularStock() {
        var dto = service.publish(validRequest());
        assertEquals("SOUP", dto.foodName());
        assertEquals("Milk, vegetables", dto.ingredients());
        assertEquals("Front desk", dto.pickupLocation());
        assertEquals(new BigDecimal("10.00"), dto.originalPrice());
        assertEquals(300, dto.calories());
        food.getAllergies().clear();
        assertEquals(List.of("MILK"), dto.allergens());
        assertEquals(20, food.getAmount());
        verify(foods, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "10", "11", "1.001"})
    void rejectInvalidPrices(String price) {
        assertThrows(IllegalArgumentException.class, () -> service.publish(
                request(price == null ? null : new BigDecimal(price), 5, NOW, NOW.plusSeconds(7200), NOW.plusSeconds(1))));
        verify(offers, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 10001})
    void rejectInvalidQuantities(Integer quantity) {
        assertThrows(IllegalArgumentException.class, () -> service.reserve(2L, "alice",
                new SurplusReservationRequest(quantity, NOW.plusSeconds(100))));
        assertThrows(IllegalArgumentException.class, () -> service.publish(
                request(new BigDecimal("2"), quantity, NOW, NOW.plusSeconds(7200), NOW.plusSeconds(1))));
    }

    static Stream<Instant[]> invalidWindows() {
        return Stream.of(new Instant[] {null, NOW.plusSeconds(2), NOW.plusSeconds(1)},
                new Instant[] {NOW, null, NOW.plusSeconds(1)}, new Instant[] {NOW, NOW.plusSeconds(2), null},
                new Instant[] {NOW, NOW, NOW.plusSeconds(1)},
                new Instant[] {NOW.plusSeconds(3), NOW.plusSeconds(2), NOW.plusSeconds(1)},
                new Instant[] {NOW, NOW.plusSeconds(2), NOW},
                new Instant[] {NOW, NOW.plusSeconds(2), NOW.plusSeconds(3)});
    }

    @ParameterizedTest
    @MethodSource("invalidWindows")
    void rejectInvalidWindows(Instant start, Instant end, Instant expiry) {
        assertThrows(IllegalArgumentException.class, () -> service.publish(
                request(new BigDecimal("2"), 5, start, end, expiry)));
    }

    @Test
    void rejectMissingAndOversizedText() {
        for (String ingredients : Arrays.asList(null, " ", "x".repeat(2001))) {
            assertThrows(IllegalArgumentException.class, () -> service.publish(new SurplusOfferRequest(
                    1L, ingredients, new BigDecimal("2"), 5, "Desk", NOW, NOW.plusSeconds(2), NOW.plusSeconds(1))));
        }
        for (String location : Arrays.asList(null, " ", "x".repeat(256))) {
            assertThrows(IllegalArgumentException.class, () -> service.publish(new SurplusOfferRequest(
                    1L, "Milk", new BigDecimal("2"), 5, location, NOW, NOW.plusSeconds(2), NOW.plusSeconds(1))));
        }
        assertThrows(IllegalArgumentException.class, () -> service.publish(null));
        when(foods.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.publish(validRequest()));
    }

    @Test
    void reserveUsesPublishedPriceAndDecrementsStock() {
        var result = service.reserve(2L, "alice", new SurplusReservationRequest(3, NOW.plusSeconds(100)));
        assertEquals(new BigDecimal("9.75"), result.totalPrice());
        assertEquals("alice", result.customerUsername());
        assertEquals("RESERVED", result.status());
        assertEquals(2, offer.getRemainingPortions());
    }

    @Test
    void expiredSoldOutAndInsufficientOffersRejectReservation() {
        offer.setExpiresAt(NOW);
        assertThrows(IllegalStateException.class, () -> service.reserve(2L, "alice",
                new SurplusReservationRequest(1, NOW.plusSeconds(100))));
        offer.setExpiresAt(NOW.plusSeconds(1000));
        offer.setRemainingPortions(0);
        assertThrows(IllegalStateException.class, () -> service.reserve(2L, "alice",
                new SurplusReservationRequest(1, NOW.plusSeconds(100))));
        offer.setRemainingPortions(1);
        assertThrows(IllegalStateException.class, () -> service.reserve(2L, "alice",
                new SurplusReservationRequest(2, NOW.plusSeconds(100))));
        verifyNoInteractions(reservations);
    }

    @Test
    void invalidPickupTimesRejectReservation() {
        offer.setPickupStart(NOW.plusSeconds(100));
        for (Instant pickup : Arrays.asList(null, NOW, NOW.minusSeconds(1), NOW.plusSeconds(99), NOW.plusSeconds(7201))) {
            assertThrows(IllegalArgumentException.class, () -> service.reserve(2L, "alice",
                    new SurplusReservationRequest(1, pickup)));
        }
        assertEquals(5, offer.getRemainingPortions());
    }

    @Test
    void pickupWindowEndpointsAreAccepted() {
        offer.setPickupStart(NOW.plusSeconds(1));
        service.reserve(2L, "alice", new SurplusReservationRequest(1, offer.getPickupStart()));
        service.reserve(2L, "alice", new SurplusReservationRequest(1, offer.getPickupEnd()));
        assertEquals(3, offer.getRemainingPortions());
    }

    @Test
    void savedAllergyAliasesBlockReservation() {
        customer.setDietaryRestrictions("dairy");
        assertThrows(IllegalArgumentException.class, () -> service.reserve(2L, "alice",
                new SurplusReservationRequest(1, NOW.plusSeconds(100))));
        assertEquals(5, offer.getRemainingPortions());
        verifyNoInteractions(reservations);
    }

    private SurplusReservation reservation() {
        SurplusReservation reservation = new SurplusReservation();
        reservation.setId(3L);
        reservation.setOffer(offer);
        reservation.setCustomerUsername("alice");
        reservation.setQuantity(2);
        reservation.setPickupAt(NOW.plusSeconds(100));
        when(reservations.findLockedById(3L)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    @Test
    void cancelRestoresStockExactlyOnce() {
        var reservation = reservation();
        assertEquals("CANCELLED", service.cancel(3L, "alice").status());
        assertEquals(7, offer.getRemainingPortions());
        assertEquals(Status.CANCELLED, reservation.getStatus());
        assertThrows(IllegalStateException.class, () -> service.cancel(3L, "alice"));
        assertEquals(7, offer.getRemainingPortions());
    }

    @Test
    void cannotCancelAnotherCustomersReservationOrCancelAfterPickup() {
        var reservation = reservation();
        assertThrows(AccessDeniedException.class, () -> service.cancel(3L, "bob"));
        reservation.setPickupAt(NOW);
        assertThrows(IllegalStateException.class, () -> service.cancel(3L, "alice"));
        assertEquals(Status.RESERVED, reservation.getStatus());
        assertEquals(5, offer.getRemainingPortions());
    }

    @Test
    void collectExactlyOnceWithoutReturningStock() {
        reservation();
        assertEquals("COLLECTED", service.collect(3L).status());
        assertThrows(IllegalStateException.class, () -> service.collect(3L));
        assertThrows(IllegalStateException.class, () -> service.cancel(3L, "alice"));
        assertEquals(5, offer.getRemainingPortions());
    }

    @Test
    void collectionOutsideWindowIsRejected() {
        reservation();
        offer.setPickupStart(NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> service.collect(3L));
        offer.setPickupStart(NOW.minusSeconds(100));
        offer.setPickupEnd(NOW.minusSeconds(1));
        assertThrows(IllegalStateException.class, () -> service.collect(3L));
    }

    @Test
    void browsingAndReservationListsUseAppropriateFilters() {
        when(offers.findByExpiresAtAfterAndRemainingPortionsGreaterThanOrderByExpiresAtAsc(NOW, 0))
                .thenReturn(List.of(offer));
        assertEquals(1, service.browse(false).size());
        service.browse(true);
        verify(offers).findAllByOrderByCreatedAtDesc();
        service.listReservations("alice", false);
        verify(reservations).findByCustomerUsernameOrderByCreatedAtDesc("alice");
        service.listReservations("staff", true);
        verify(reservations).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void missingOffersReservationsAndCustomerAreRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.reserve(99L, "alice",
                new SurplusReservationRequest(1, NOW.plusSeconds(100))));
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(99L, "alice"));
        assertThrows(ResourceNotFoundException.class, () -> service.collect(99L));
        assertThrows(AccessDeniedException.class, () -> service.reserve(2L, "unknown",
                new SurplusReservationRequest(1, NOW.plusSeconds(100))));
    }
}
