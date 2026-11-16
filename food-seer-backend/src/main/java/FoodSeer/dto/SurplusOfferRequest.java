package FoodSeer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SurplusOfferRequest(Long foodId, String ingredients, BigDecimal discountedPrice,
        Integer portions, String pickupLocation, Instant pickupStart, Instant pickupEnd, Instant expiresAt) { }
