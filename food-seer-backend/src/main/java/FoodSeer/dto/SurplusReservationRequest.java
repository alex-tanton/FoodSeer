package FoodSeer.dto;

import java.time.Instant;

public record SurplusReservationRequest(Integer quantity, Instant pickupAt) { }
