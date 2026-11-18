package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.SurplusReservation;
import FoodSeer.entity.SurplusReservation.Status;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.SurplusOfferRepository;
import FoodSeer.repositories.SurplusReservationRepository;

class RescueStatsServiceTest {
    private static final Instant ALERTED = Instant.parse("2026-11-17T18:00:00Z");

    private final SurplusOfferRepository offers = mock(SurplusOfferRepository.class);
    private final SurplusReservationRepository reservations = mock(SurplusReservationRepository.class);
    private final RescueAlertRepository alerts = mock(RescueAlertRepository.class);
    private final RescueStatsService service = new RescueStatsService(offers, reservations, alerts);

    private SurplusOffer pasta;
    private SurplusOffer soup;

    @BeforeEach
    void setup() {
        pasta = offer(1L, "PASTA", "12.00", "6.00");
        soup = offer(2L, "SOUP", "9.00", "4.50");
        when(offers.findById(1L)).thenReturn(Optional.of(pasta));
        when(offers.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(soup, pasta));
    }

    private static SurplusOffer offer(final Long id, final String name, final String original, final String price) {
        SurplusOffer offer = new SurplusOffer();
        offer.setId(id);
        offer.setFoodName(name);
        offer.setOriginalPrice(new BigDecimal(original));
        offer.setDiscountedPrice(new BigDecimal(price));
        offer.setRemainingPortions(1);
        offer.setExpiresAt(ALERTED.plusSeconds(3600));
        return offer;
    }

    private static SurplusReservation reservation(final SurplusOffer offer, final String customer,
            final int quantity, final Status status, final Instant at) {
        SurplusReservation reservation = new SurplusReservation();
        reservation.setOffer(offer);
        reservation.setCustomerUsername(customer);
        reservation.setQuantity(quantity);
        reservation.setStatus(status);
        reservation.setCreatedAt(at);
        reservation.setTotalPrice(offer.getDiscountedPrice().multiply(BigDecimal.valueOf(quantity)));
        return reservation;
    }

    private static RescueAlert alert(final Long offerId, final String customer) {
        RescueAlert alert = new RescueAlert();
        alert.setOfferId(offerId);
        alert.setRecipientUsername(customer);
        alert.setCreatedAt(ALERTED);
        return alert;
    }

    @Test
    void countsAlertsConversionsPortionsAndMoney() {
        when(alerts.findByOfferId(1L)).thenReturn(List.of(alert(1L, "ann"), alert(1L, "bob"), alert(1L, "cy")));
        when(reservations.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                reservation(pasta, "ann", 2, Status.RESERVED, ALERTED.plusSeconds(60)),
                reservation(pasta, "bob", 1, Status.COLLECTED, ALERTED.plusSeconds(120)),
                reservation(pasta, "walk-in", 1, Status.RESERVED, ALERTED.plusSeconds(30)),
                reservation(soup, "cy", 1, Status.RESERVED, ALERTED.plusSeconds(60))));

        RescueDtos.Stats stats = service.forOffer(1L);

        assertEquals(3, stats.alerted());
        assertEquals(2, stats.alertedWhoReserved());
        assertEquals(4, stats.portionsRescued());
        assertEquals(1, stats.portionsCollected());
        assertEquals(new BigDecimal("24.00"), stats.revenueRecovered());
        assertEquals(new BigDecimal("24.00"), stats.customerSavings());
    }

    @Test
    void cancelledReservationsNeverCount() {
        when(alerts.findByOfferId(1L)).thenReturn(List.of(alert(1L, "ann")));
        when(reservations.findAllByOrderByCreatedAtDesc()).thenReturn(
                List.of(reservation(pasta, "ann", 3, Status.CANCELLED, ALERTED.plusSeconds(60))));

        RescueDtos.Stats stats = service.forOffer(1L);

        assertEquals(1, stats.alerted());
        assertEquals(0, stats.alertedWhoReserved());
        assertEquals(0, stats.portionsRescued());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.revenueRecovered()));
    }

    @Test
    void reservationsMadeBeforeTheAlertAreNotCreditedToIt() {
        when(alerts.findByOfferId(1L)).thenReturn(List.of(alert(1L, "ann")));
        when(reservations.findAllByOrderByCreatedAtDesc()).thenReturn(
                List.of(reservation(pasta, "ann", 1, Status.RESERVED, ALERTED.minusSeconds(1))));

        RescueDtos.Stats stats = service.forOffer(1L);

        assertEquals(0, stats.alertedWhoReserved());
        assertEquals(1, stats.portionsRescued());
    }

    @Test
    void allListsEveryOfferNewestFirstIncludingQuietOnes() {
        when(alerts.findByOfferId(anyLong())).thenReturn(List.of());
        when(reservations.findAllByOrderByCreatedAtDesc()).thenReturn(
                List.of(reservation(soup, "ann", 2, Status.RESERVED, ALERTED)));

        List<RescueDtos.Stats> all = service.all();

        assertEquals(List.of("SOUP", "PASTA"), all.stream().map(RescueDtos.Stats::foodName).toList());
        assertEquals(new BigDecimal("9.00"), all.get(0).revenueRecovered());
        assertEquals(new BigDecimal("9.00"), all.get(0).customerSavings());
        assertEquals(0, all.get(1).portionsRescued());
    }

    @Test
    void unknownOfferIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.forOffer(9L));
    }
}
