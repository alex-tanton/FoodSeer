package FoodSeer.rescue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

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

/**
 * Looks up everything the pure {@link RescueMatcher} needs, using the inherited
 * repositories and the inherited calorie budget. Read-only.
 */
@Component
public class RescueCandidateFactory {

    static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";

    private final UserRepository users;
    private final OrderRepository orders;
    private final CalorieBudgetService calorieBudgets;
    private final SurplusReservationRepository reservations;
    private final RescueAlertRepository alerts;
    private final AllergenRepository allergens;

    public RescueCandidateFactory(final UserRepository users, final OrderRepository orders,
            final CalorieBudgetService calorieBudgets, final SurplusReservationRepository reservations,
            final RescueAlertRepository alerts, final AllergenRepository allergens) {
        this.users = users;
        this.orders = orders;
        this.calorieBudgets = calorieBudgets;
        this.reservations = reservations;
        this.alerts = alerts;
        this.allergens = allergens;
    }

    /**
     * @param offer a surplus offer
     * @return the matcher's view of it
     */
    public RescueListing listingOf(final SurplusOffer offer) {
        return new RescueListing(offer.getId(), offer.getFoodId(), offer.getFoodName(), offer.getAllergens(),
                offer.getCalories(), offer.getOriginalPrice(), offer.getDiscountedPrice(),
                offer.getRemainingPortions(), offer.getExpiresAt());
    }

    /** @return every allergen name in the shared allergen list */
    public List<String> knownAllergens() {
        return allergens.findAll().stream().map(Allergen::getName).filter(Objects::nonNull).toList();
    }

    /**
     * Builds one candidate per customer account.
     *
     * @param offer the offer being matched
     * @param now the current time
     * @param zone the zone that decides where "today" starts
     * @return candidates in username order
     */
    public List<RescueCandidate> candidatesFor(final SurplusOffer offer, final Instant now, final ZoneId zone) {
        final Instant dayStart = LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
        final Instant dayEnd = LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone).toInstant();
        final List<RescueCandidate> result = new ArrayList<>();
        for (final User user : users.findAll()) {
            if (!CUSTOMER_ROLE.equals(user.getRole())) {
                continue;
            }
            result.add(candidateOf(user, offer, dayStart, dayEnd, zone));
        }
        result.sort((a, b) -> a.username().compareTo(b.username()));
        return result;
    }

    RescueCandidate candidateOf(final User user, final SurplusOffer offer, final Instant dayStart,
            final Instant dayEnd, final ZoneId zone) {
        final String username = user.getUsername();
        int pastOrdersOfDish = 0;
        LocalDateTime lastOrder = null;
        for (final Order order : orders.findByUser(user)) {
            if (containsFood(order, offer.getFoodId())) {
                pastOrdersOfDish++;
            }
            if (order.getCreatedAt() != null && (lastOrder == null || order.getCreatedAt().isAfter(lastOrder))) {
                lastOrder = order.getCreatedAt();
            }
        }
        final Instant lastOrderAt = lastOrder == null ? null : lastOrder.atZone(zone).toInstant();
        final int alertsToday = (int) alerts
                .countByRecipientUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(username, dayStart, dayEnd);
        final boolean alreadyAlerted = offer.getId() != null
                && alerts.existsByOfferIdAndRecipientUsername(offer.getId(), username);
        return new RescueCandidate(username, user.getDietaryRestrictions(), user.getCostPreference(),
                remainingCalories(user, dayStart, dayEnd), pastOrdersOfDish, lastOrderAt, alertsToday,
                alreadyAlerted);
    }

    /**
     * The inherited budget does not know about surplus reservations, so calories
     * from surplus reserved today are subtracted here.
     *
     * @return calories left today, or null when the customer does not track them
     */
    Integer remainingCalories(final User user, final Instant dayStart, final Instant dayEnd) {
        final Integer remaining = calorieBudgets.getBudgetFor(user).remainingCalories();
        if (remaining == null) {
            return null;
        }
        int reservedToday = 0;
        for (final SurplusReservation reservation : reservations
                .findByCustomerUsernameOrderByCreatedAtDesc(user.getUsername())) {
            final Instant created = reservation.getCreatedAt();
            if (reservation.getStatus() == SurplusReservation.Status.CANCELLED || created == null
                    || created.isBefore(dayStart) || !created.isBefore(dayEnd)) {
                continue;
            }
            final Integer calories = reservation.getOffer().getCalories();
            if (calories != null) {
                reservedToday += calories * reservation.getQuantity();
            }
        }
        return remaining - reservedToday;
    }

    private static boolean containsFood(final Order order, final Long foodId) {
        if (order.getFoods() == null || foodId == null) {
            return false;
        }
        for (final Food food : order.getFoods()) {
            if (food != null && foodId.equals(food.getId())) {
                return true;
            }
        }
        return false;
    }
}
