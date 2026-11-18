package FoodSeer.rescue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.SurplusReservation;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.SurplusOfferRepository;
import FoodSeer.repositories.SurplusReservationRepository;

/**
 * Read-only rescue results per surplus offer, from the marketplace's
 * reservations and the rescue alerts. Cancelled reservations never count.
 */
@Service
@Transactional(readOnly = true)
public class RescueStatsService {

    private final SurplusOfferRepository offers;
    private final SurplusReservationRepository reservations;
    private final RescueAlertRepository alerts;

    public RescueStatsService(final SurplusOfferRepository offers, final SurplusReservationRepository reservations,
            final RescueAlertRepository alerts) {
        this.offers = offers;
        this.reservations = reservations;
        this.alerts = alerts;
    }

    /** @return stats for every offer, newest offer first */
    public List<RescueDtos.Stats> all() {
        final Map<Long, List<SurplusReservation>> byOffer = new HashMap<>();
        for (final SurplusReservation reservation : reservations.findAllByOrderByCreatedAtDesc()) {
            byOffer.computeIfAbsent(reservation.getOffer().getId(), id -> new ArrayList<>()).add(reservation);
        }
        final List<RescueDtos.Stats> result = new ArrayList<>();
        for (final SurplusOffer offer : offers.findAllByOrderByCreatedAtDesc()) {
            result.add(statsOf(offer, byOffer.getOrDefault(offer.getId(), List.of()), alerts.findByOfferId(offer.getId())));
        }
        return result;
    }

    /**
     * @param offerId the surplus offer
     * @return its stats
     */
    public RescueDtos.Stats forOffer(final Long offerId) {
        final SurplusOffer offer = offers.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Surplus offer not found."));
        final List<SurplusReservation> offerReservations = reservations.findAllByOrderByCreatedAtDesc().stream()
                .filter(reservation -> offerId.equals(reservation.getOffer().getId())).toList();
        return statsOf(offer, offerReservations, alerts.findByOfferId(offerId));
    }

    /**
     * An alerted customer counts as converted when they hold a reservation on the
     * offer, made at or after their alert, that was not cancelled.
     */
    static RescueDtos.Stats statsOf(final SurplusOffer offer, final List<SurplusReservation> offerReservations,
            final List<RescueAlert> offerAlerts) {
        final Map<String, RescueAlert> alertByCustomer = new HashMap<>();
        for (final RescueAlert alert : offerAlerts) {
            alertByCustomer.put(alert.getRecipientUsername(), alert);
        }
        final Set<String> converted = new HashSet<>();
        int rescued = 0;
        int collected = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal savings = BigDecimal.ZERO;
        final BigDecimal perPortionSaving = offer.getOriginalPrice().subtract(offer.getDiscountedPrice());
        for (final SurplusReservation reservation : offerReservations) {
            if (reservation.getStatus() == SurplusReservation.Status.CANCELLED) {
                continue;
            }
            rescued += reservation.getQuantity();
            if (reservation.getStatus() == SurplusReservation.Status.COLLECTED) {
                collected += reservation.getQuantity();
            }
            revenue = revenue.add(reservation.getTotalPrice());
            savings = savings.add(perPortionSaving.multiply(BigDecimal.valueOf(reservation.getQuantity())));
            final RescueAlert alert = alertByCustomer.get(reservation.getCustomerUsername());
            if (alert != null && !reservation.getCreatedAt().isBefore(alert.getCreatedAt())) {
                converted.add(reservation.getCustomerUsername());
            }
        }
        return new RescueDtos.Stats(offer.getId(), offer.getFoodName(), offer.getExpiresAt(),
                offer.getRemainingPortions(), alertByCustomer.size(), converted.size(), rescued, collected, revenue,
                savings);
    }
}
