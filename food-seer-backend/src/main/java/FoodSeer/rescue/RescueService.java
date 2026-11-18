package FoodSeer.rescue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import FoodSeer.dto.SurplusOfferDto;
import FoodSeer.entity.Notification;
import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.SurplusReservation;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.NotificationRepository;
import FoodSeer.repositories.SurplusOfferRepository;
import FoodSeer.repositories.SurplusReservationRepository;

/**
 * Runs the rescue matcher against real offers and customers, sends alerts
 * through the inherited notification channel, and lists a customer's deals.
 */
@Service
@Transactional
public class RescueService {

    private final SurplusOfferRepository offers;
    private final SurplusReservationRepository reservations;
    private final RescueAlertRepository alerts;
    private final NotificationRepository notifications;
    private final RescueCandidateFactory candidates;
    private final RescuePolicy policy;
    private final Clock clock;

    @Autowired
    public RescueService(final SurplusOfferRepository offers, final SurplusReservationRepository reservations,
            final RescueAlertRepository alerts, final NotificationRepository notifications,
            final RescueCandidateFactory candidates) {
        this(offers, reservations, alerts, notifications, candidates, RescuePolicy.DEFAULT,
                Clock.systemDefaultZone());
    }

    RescueService(final SurplusOfferRepository offers, final SurplusReservationRepository reservations,
            final RescueAlertRepository alerts, final NotificationRepository notifications,
            final RescueCandidateFactory candidates, final RescuePolicy policy, final Clock clock) {
        this.offers = offers;
        this.reservations = reservations;
        this.alerts = alerts;
        this.notifications = notifications;
        this.candidates = candidates;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Shows staff who would be alerted and why, without sending anything.
     *
     * @param offerId the surplus offer
     * @return ranked and excluded customers
     */
    @Transactional(readOnly = true)
    public RescueDtos.Preview preview(final Long offerId) {
        final SurplusOffer offer = offers.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Surplus offer not found."));
        return toPreview(offer, match(offer));
    }

    /**
     * Alerts the top-ranked customers for an offer. Customers already alerted for
     * it are skipped, so calling this twice never alerts anyone twice.
     *
     * @param offerId the surplus offer
     * @return who was alerted
     */
    public RescueDtos.NotifyResult notify(final Long offerId) {
        final SurplusOffer offer = offers.findLockedById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Surplus offer not found."));
        final MatchResult result = match(offer);
        final Instant now = clock.instant();
        final List<String> alerted = new ArrayList<>();
        for (final RankedCandidate match : result.toAlert()) {
            if (alerts.existsByOfferIdAndRecipientUsername(offer.getId(), match.username())) {
                continue;
            }
            final Notification notification = new Notification();
            notification.setKind(Notification.KIND_RESCUE);
            notification.setRecipientUsername(match.username());
            notification.setMessage(message(offer, match));
            notification.setCreatedAt(LocalDateTime.now(clock));
            final Notification saved = notifications.save(notification);

            final RescueAlert alert = RescueAlert.of(offer.getId(), match, now);
            alert.setNotificationId(saved.getId());
            alerts.save(alert);
            alerted.add(match.username());
        }
        return new RescueDtos.NotifyResult(offer.getId(), result.alertBudget(), alerted, result.excluded().size());
    }

    /**
     * The customer's rescue deals that can still be reserved, best first.
     *
     * @param username the customer
     * @return open deals with a reason line refreshed to the current time left
     */
    @Transactional(readOnly = true)
    public List<RescueDtos.Deal> deals(final String username) {
        final Instant now = clock.instant();
        final Set<Long> reservedOffers = new HashSet<>();
        for (final SurplusReservation reservation : reservations.findByCustomerUsernameOrderByCreatedAtDesc(username)) {
            if (reservation.getStatus() != SurplusReservation.Status.CANCELLED) {
                reservedOffers.add(reservation.getOffer().getId());
            }
        }
        final List<RescueDtos.Deal> deals = new ArrayList<>();
        for (final RescueAlert alert : alerts.findByRecipientUsernameOrderByCreatedAtDesc(username)) {
            final Optional<SurplusOffer> offer = offers.findById(alert.getOfferId());
            if (offer.isEmpty() || offer.get().getRemainingPortions() <= 0
                    || !offer.get().getExpiresAt().isAfter(now)) {
                continue;
            }
            final long minutesLeft = java.time.Duration.between(now, offer.get().getExpiresAt()).toMinutes();
            final String reason = RescueReasons.describe(alert.getPastOrdersOfDish(), alert.getDiscountPercent(),
                    minutesLeft);
            deals.add(new RescueDtos.Deal(alert.getId(), SurplusOfferDto.from(offer.get()), reason, alert.getScore(),
                    alert.getCreatedAt(), reservedOffers.contains(alert.getOfferId())));
        }
        deals.sort(Comparator.comparingDouble(RescueDtos.Deal::score).reversed()
                .thenComparing(deal -> deal.offer().expiresAt()));
        return deals;
    }

    private MatchResult match(final SurplusOffer offer) {
        final Instant now = clock.instant();
        return RescueMatcher.match(candidates.listingOf(offer), candidates.candidatesFor(offer, now, clock.getZone()),
                candidates.knownAllergens(), now, policy);
    }

    private static RescueDtos.Preview toPreview(final SurplusOffer offer, final MatchResult result) {
        final Set<String> willAlert = new HashSet<>();
        result.toAlert().forEach(match -> willAlert.add(match.username()));
        final List<RescueDtos.Match> ranked = result.ranked().stream()
                .map(match -> RescueDtos.Match.of(match, willAlert.contains(match.username()))).toList();
        final List<RescueDtos.Excluded> excluded = new ArrayList<>();
        for (final Map.Entry<String, Exclusion> entry : result.excluded().entrySet()) {
            excluded.add(new RescueDtos.Excluded(entry.getKey(), entry.getValue(), entry.getValue().description()));
        }
        return new RescueDtos.Preview(offer.getId(), offer.getFoodName(), result.alertBudget(), ranked, excluded);
    }

    static String message(final SurplusOffer offer, final RankedCandidate match) {
        return "Rescue deal: " + offer.getFoodName() + " for $" + offer.getDiscountedPrice() + " instead of $"
                + offer.getOriginalPrice() + " at " + offer.getPickupLocation() + ". Why you: " + match.reason()
                + ". Open Rescue Deals to reserve.";
    }
}
