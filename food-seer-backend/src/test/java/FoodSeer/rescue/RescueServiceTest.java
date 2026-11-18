package FoodSeer.rescue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import FoodSeer.entity.Notification;
import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.SurplusReservation;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.NotificationRepository;
import FoodSeer.repositories.SurplusOfferRepository;
import FoodSeer.repositories.SurplusReservationRepository;

class RescueServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-17T18:00:00Z");

    private final SurplusOfferRepository offers = mock(SurplusOfferRepository.class);
    private final SurplusReservationRepository reservations = mock(SurplusReservationRepository.class);
    private final RescueAlertRepository alerts = mock(RescueAlertRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final RescueCandidateFactory factory = mock(RescueCandidateFactory.class);
    private final RescueService service = new RescueService(offers, reservations, alerts, notifications, factory,
            RescuePolicy.DEFAULT, Clock.fixed(NOW, ZoneOffset.UTC));

    private SurplusOffer offer;

    @BeforeEach
    void setup() {
        offer = offer(5L, "PASTA", 1, NOW.plusSeconds(25 * 60));
        when(offers.findById(5L)).thenReturn(Optional.of(offer));
        when(offers.findLockedById(5L)).thenReturn(Optional.of(offer));
        when(factory.listingOf(any())).thenCallRealMethod();
        when(factory.knownAllergens()).thenReturn(RescueFixtures.KNOWN);
        when(notifications.save(any())).thenAnswer(call -> {
            Notification saved = call.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private static SurplusOffer offer(final Long id, final String name, final int portions, final Instant expires) {
        SurplusOffer offer = new SurplusOffer();
        offer.setId(id);
        offer.setFoodId(10L);
        offer.setFoodName(name);
        offer.setIngredients("stuff");
        offer.setAllergens(new ArrayList<>(List.of("EGGS")));
        offer.setCalories(500);
        offer.setOriginalPrice(new BigDecimal("10.00"));
        offer.setDiscountedPrice(new BigDecimal("6.00"));
        offer.setRemainingPortions(portions);
        offer.setPickupLocation("Front desk");
        offer.setPickupStart(NOW);
        offer.setPickupEnd(expires.plusSeconds(3600));
        offer.setExpiresAt(expires);
        return offer;
    }

    private void candidates(final RescueCandidate... list) {
        when(factory.candidatesFor(eq(offer), eq(NOW), eq(ZoneOffset.UTC))).thenReturn(List.of(list));
    }

    private static RescueCandidate fan(final String name, final int pastOrders) {
        return new RescueCandidate(name, null, null, null, pastOrders, null, 0, false);
    }

    private static RescueAlert alert(final Long id, final Long offerId, final double score) {
        RescueAlert alert = new RescueAlert();
        alert.setId(id);
        alert.setOfferId(offerId);
        alert.setRecipientUsername("ann");
        alert.setScore(score);
        alert.setPastOrdersOfDish(2);
        alert.setDiscountPercent(40);
        alert.setReason("old reason");
        alert.setCreatedAt(NOW.minusSeconds(600));
        return alert;
    }

    @Test
    void previewListsRankedAndExcludedWithoutSending() {
        candidates(fan("a", 3), fan("b", 2), fan("c", 1), fan("d", 0),
                new RescueCandidate("eggy", "eggs", null, null, 3, null, 0, false));

        RescueDtos.Preview preview = service.preview(5L);

        assertEquals(2, preview.alertBudget());
        assertEquals(List.of("a", "b", "c", "d"), preview.ranked().stream().map(RescueDtos.Match::username).toList());
        assertEquals(List.of(true, true, false, false),
                preview.ranked().stream().map(RescueDtos.Match::willAlert).toList());
        assertEquals(Exclusion.ALLERGEN, preview.excluded().get(0).reason());
        assertEquals(Exclusion.ALLERGEN.description(), preview.excluded().get(0).description());
        verifyNoInteractions(notifications);
        verify(alerts, never()).save(any());
    }

    @Test
    void previewOfMissingOfferIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.preview(404L));
    }

    @Test
    void notifySendsRescueNotificationsAndRecordsAlerts() {
        candidates(fan("a", 2), fan("b", 0));

        RescueDtos.NotifyResult result = service.notify(5L);

        assertEquals(List.of("a", "b"), result.alerted());
        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(sent.capture());
        Notification first = sent.getAllValues().get(0);
        assertEquals(Notification.KIND_RESCUE, first.getKind());
        assertEquals("a", first.getRecipientUsername());
        assertNull(first.getIncidentId());
        assertEquals("Rescue deal: PASTA for $6.00 instead of $10.00 at Front desk. Why you: you ordered this twice"
                + " · 40% off · 25 min left. Open Rescue Deals to reserve.", first.getMessage());

        ArgumentCaptor<RescueAlert> saved = ArgumentCaptor.forClass(RescueAlert.class);
        verify(alerts, times(2)).save(saved.capture());
        RescueAlert alert = saved.getAllValues().get(0);
        assertEquals(5L, alert.getOfferId());
        assertEquals(100L, alert.getNotificationId());
        assertEquals(NOW, alert.getCreatedAt());
        assertEquals(2.0 / 3.0, alert.getAffinity(), 1e-9);
        assertEquals(40, alert.getDiscountPercent());
    }

    @Test
    void notifyStopsAtTheAlertBudget() {
        candidates(fan("a", 3), fan("b", 2), fan("c", 1), fan("d", 0), fan("e", 0));
        RescueDtos.NotifyResult result = service.notify(5L);
        assertEquals(List.of("a", "b"), result.alerted());
        verify(notifications, times(2)).save(any());
    }

    @Test
    void notifySkipsCustomersAlertedSinceTheMatchRan() {
        candidates(fan("a", 1), fan("b", 0));
        when(alerts.existsByOfferIdAndRecipientUsername(5L, "a")).thenReturn(true);
        assertEquals(List.of("b"), service.notify(5L).alerted());
        verify(notifications, times(1)).save(any());
    }

    @Test
    void notifyReportsExcludedCount() {
        candidates(fan("a", 1), new RescueCandidate("capped", null, null, null, 0, null, 2, false));
        assertEquals(1, service.notify(5L).excludedCount());
    }

    @Test
    void notifyOfMissingOfferIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.notify(404L));
        verifyNoInteractions(notifications);
    }

    @Test
    void dealsHideExpiredSoldOutAndDeletedOffers() {
        SurplusOffer expired = offer(6L, "SOUP", 3, NOW.minusSeconds(1));
        SurplusOffer soldOut = offer(7L, "CAKE", 0, NOW.plusSeconds(3600));
        when(offers.findById(6L)).thenReturn(Optional.of(expired));
        when(offers.findById(7L)).thenReturn(Optional.of(soldOut));
        when(offers.findById(8L)).thenReturn(Optional.empty());
        when(alerts.findByRecipientUsernameOrderByCreatedAtDesc("ann")).thenReturn(
                List.of(alert(1L, 5L, 0.5), alert(2L, 6L, 0.9), alert(3L, 7L, 0.9), alert(4L, 8L, 0.9)));

        List<RescueDtos.Deal> deals = service.deals("ann");

        assertEquals(1, deals.size());
        assertEquals("PASTA", deals.get(0).offer().foodName());
    }

    @Test
    void dealsRefreshTheTimeLeftInTheReason() {
        when(alerts.findByRecipientUsernameOrderByCreatedAtDesc("ann")).thenReturn(List.of(alert(1L, 5L, 0.5)));
        assertEquals("you ordered this twice · 40% off · 25 min left", service.deals("ann").get(0).reason());
    }

    @Test
    void dealsSortByScoreThenDeadline() {
        SurplusOffer later = offer(6L, "SOUP", 3, NOW.plusSeconds(7200));
        SurplusOffer sooner = offer(7L, "CAKE", 3, NOW.plusSeconds(1800));
        when(offers.findById(6L)).thenReturn(Optional.of(later));
        when(offers.findById(7L)).thenReturn(Optional.of(sooner));
        when(alerts.findByRecipientUsernameOrderByCreatedAtDesc("ann")).thenReturn(
                List.of(alert(1L, 5L, 0.2), alert(2L, 6L, 0.8), alert(3L, 7L, 0.8)));
        assertEquals(List.of("CAKE", "SOUP", "PASTA"),
                service.deals("ann").stream().map(deal -> deal.offer().foodName()).toList());
    }

    @Test
    void dealsMarkOffersTheCustomerAlreadyReserved() {
        SurplusReservation active = new SurplusReservation();
        active.setOffer(offer);
        SurplusOffer other = offer(6L, "SOUP", 3, NOW.plusSeconds(3600));
        SurplusReservation cancelled = new SurplusReservation();
        cancelled.setOffer(other);
        cancelled.setStatus(SurplusReservation.Status.CANCELLED);
        when(offers.findById(6L)).thenReturn(Optional.of(other));
        when(reservations.findByCustomerUsernameOrderByCreatedAtDesc("ann")).thenReturn(List.of(active, cancelled));
        when(alerts.findByRecipientUsernameOrderByCreatedAtDesc("ann")).thenReturn(
                List.of(alert(1L, 5L, 0.9), alert(2L, 6L, 0.1)));

        List<RescueDtos.Deal> deals = service.deals("ann");

        assertTrue(deals.get(0).reserved());
        assertFalse(deals.get(1).reserved());
    }
}
