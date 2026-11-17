package FoodSeer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import FoodSeer.entity.SurplusOffer;

public record SurplusOfferDto(Long id, String foodName, String ingredients, List<String> allergens,
        Integer calories, BigDecimal originalPrice, BigDecimal discountedPrice, int remainingPortions,
        String pickupLocation, Instant pickupStart, Instant pickupEnd, Instant expiresAt) {
    public SurplusOfferDto {
        allergens = List.copyOf(allergens);
    }

    public static SurplusOfferDto from(SurplusOffer offer) {
        return new SurplusOfferDto(offer.getId(), offer.getFoodName(), offer.getIngredients(),
                List.copyOf(offer.getAllergens()), offer.getCalories(), offer.getOriginalPrice(),
                offer.getDiscountedPrice(), offer.getRemainingPortions(), offer.getPickupLocation(),
                offer.getPickupStart(), offer.getPickupEnd(), offer.getExpiresAt());
    }
}
