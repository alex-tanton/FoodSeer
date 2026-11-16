package FoodSeer.dto;

import java.time.Instant;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record SurplusReservationRequest(
        @JsonDeserialize(using = WholePortionsDeserializer.class) Integer quantity, Instant pickupAt) { }
