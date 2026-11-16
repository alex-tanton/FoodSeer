package FoodSeer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record SurplusOfferRequest(Long foodId, String ingredients, BigDecimal discountedPrice,
        @JsonDeserialize(using = WholePortionsDeserializer.class) Integer portions,
        String pickupLocation, Instant pickupStart, Instant pickupEnd, Instant expiresAt) { }
