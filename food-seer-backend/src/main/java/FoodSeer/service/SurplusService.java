package FoodSeer.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import FoodSeer.dto.*;
import FoodSeer.entity.Food;
import FoodSeer.entity.SurplusOffer;
import FoodSeer.entity.SurplusReservation;
import FoodSeer.entity.SurplusReservation.Status;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SurplusService {
    private final SurplusOfferRepository offers;
    private final SurplusReservationRepository reservations;
    private final FoodRepository foods;
    private final UserRepository users;
    private final Clock clock;

    @Autowired
    public SurplusService(SurplusOfferRepository offers, SurplusReservationRepository reservations,
            FoodRepository foods, UserRepository users) {
        this(offers, reservations, foods, users, Clock.systemUTC());
    }

    SurplusService(SurplusOfferRepository offers, SurplusReservationRepository reservations,
            FoodRepository foods, UserRepository users, Clock clock) {
        this.offers = offers;
        this.reservations = reservations;
        this.foods = foods;
        this.users = users;
        this.clock = clock;
    }

    public SurplusOfferDto publish(SurplusOfferRequest request) {
        if (request == null || request.foodId() == null) {
            throw new IllegalArgumentException("Choose a menu item.");
        }
        requireText(request.ingredients(), 2000, "Ingredients");
        requireText(request.pickupLocation(), 255, "Pickup location");
        requireQuantity(request.portions());
        Instant now = clock.instant();
        if (request.pickupStart() == null || request.pickupEnd() == null || request.expiresAt() == null
                || !request.pickupStart().isBefore(request.pickupEnd())
                || !request.expiresAt().isAfter(now) || request.expiresAt().isAfter(request.pickupEnd())) {
            throw new IllegalArgumentException("Provide a valid pickup window and a future booking deadline no later than pickup end.");
        }
        Food food = foods.findById(request.foodId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found."));
        BigDecimal price = request.discountedPrice();
        BigDecimal original = BigDecimal.valueOf(food.getPrice());
        if (price == null || price.signum() <= 0 || price.stripTrailingZeros().scale() > 2
                || price.compareTo(original) >= 0) {
            throw new IllegalArgumentException("Discount price must be positive, below the menu price, and have at most two decimal places.");
        }
        SurplusOffer offer = new SurplusOffer();
        offer.setFoodId(food.getId());
        offer.setFoodName(food.getFoodName());
        offer.setAllergens(food.getAllergies() == null ? new ArrayList<>() : new ArrayList<>(food.getAllergies()));
        offer.setCalories(food.getCalories());
        offer.setIngredients(request.ingredients().trim());
        offer.setOriginalPrice(original.setScale(2));
        offer.setDiscountedPrice(price.setScale(2));
        offer.setRemainingPortions(request.portions());
        offer.setPickupLocation(request.pickupLocation().trim());
        offer.setPickupStart(request.pickupStart());
        offer.setPickupEnd(request.pickupEnd());
        offer.setExpiresAt(request.expiresAt());
        offer.setCreatedAt(now);
        return SurplusOfferDto.from(offers.save(offer));
    }

    @Transactional(readOnly = true)
    public List<SurplusOfferDto> browse(boolean includeUnavailable) {
        List<SurplusOffer> result = includeUnavailable ? offers.findAllByOrderByCreatedAtDesc()
                : offers.findByExpiresAtAfterAndRemainingPortionsGreaterThanOrderByExpiresAtAsc(clock.instant(), 0);
        return result.stream().map(SurplusOfferDto::from).toList();
    }

    public SurplusReservationDto reserve(Long offerId, String username, SurplusReservationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Provide a reservation.");
        }
        requireQuantity(request.quantity());
        SurplusOffer offer = lockedOffer(offerId);
        Instant now = clock.instant();
        if (!offer.getExpiresAt().isAfter(now) || offer.getRemainingPortions() < request.quantity()) {
            throw new IllegalStateException("This offer has expired or does not have enough portions.");
        }
        if (request.pickupAt() == null || !request.pickupAt().isAfter(now)
                || request.pickupAt().isBefore(offer.getPickupStart())
                || request.pickupAt().isAfter(offer.getPickupEnd())) {
            throw new IllegalArgumentException("Choose a future pickup time inside the offer's pickup window.");
        }
        var customer = users.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Customer account not found."));
        if (offer.getAllergens().stream().anyMatch(allergen ->
                AllergenMatcher.restrictionsAffectedBy(customer.getDietaryRestrictions(), allergen))) {
            throw new IllegalArgumentException("This offer conflicts with your saved dietary restrictions.");
        }
        SurplusReservation reservation = new SurplusReservation();
        reservation.setOffer(offer);
        reservation.setCustomerUsername(username);
        reservation.setQuantity(request.quantity());
        reservation.setTotalPrice(offer.getDiscountedPrice().multiply(BigDecimal.valueOf(request.quantity())));
        reservation.setPickupAt(request.pickupAt());
        reservation.setCreatedAt(now);
        offer.setRemainingPortions(offer.getRemainingPortions() - request.quantity());
        return SurplusReservationDto.from(reservations.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<SurplusReservationDto> listReservations(String username, boolean staff) {
        var result = staff ? reservations.findAllByOrderByCreatedAtDesc()
                : reservations.findByCustomerUsernameOrderByCreatedAtDesc(username);
        return result.stream().map(SurplusReservationDto::from).toList();
    }

    public SurplusReservationDto cancel(Long id, String username) {
        SurplusReservation reservation = lockedReservation(id);
        if (!reservation.getCustomerUsername().equals(username)) {
            throw new AccessDeniedException("You can only cancel your own reservations.");
        }
        requireReserved(reservation);
        if (!clock.instant().isBefore(reservation.getPickupAt())) {
            throw new IllegalStateException("Reservations cannot be cancelled at or after pickup time.");
        }
        SurplusOffer offer = lockedOffer(reservation.getOffer().getId());
        offer.setRemainingPortions(offer.getRemainingPortions() + reservation.getQuantity());
        reservation.setStatus(Status.CANCELLED);
        return SurplusReservationDto.from(reservation);
    }

    public SurplusReservationDto collect(Long id) {
        SurplusReservation reservation = lockedReservation(id);
        requireReserved(reservation);
        Instant now = clock.instant();
        if (now.isBefore(reservation.getOffer().getPickupStart())
                || now.isAfter(reservation.getOffer().getPickupEnd())) {
            throw new IllegalStateException("Collection must occur during the pickup window.");
        }
        reservation.setStatus(Status.COLLECTED);
        return SurplusReservationDto.from(reservation);
    }

    private SurplusOffer lockedOffer(Long id) {
        return offers.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surplus offer not found."));
    }

    private SurplusReservation lockedReservation(Long id) {
        return reservations.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found."));
    }

    private static void requireReserved(SurplusReservation reservation) {
        if (reservation.getStatus() != Status.RESERVED) {
            throw new IllegalStateException("This reservation is already cancelled or collected.");
        }
    }

    private static void requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 10000) {
            throw new IllegalArgumentException("Portions must be a whole number between 1 and 10000.");
        }
    }

    private static void requireText(String text, int maxLength, String label) {
        if (text == null || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException(label + " is required (maximum " + maxLength + " characters).");
        }
    }
}
