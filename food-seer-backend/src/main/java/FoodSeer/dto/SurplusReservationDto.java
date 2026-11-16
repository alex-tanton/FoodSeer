package FoodSeer.dto;

import java.math.BigDecimal;
import java.time.Instant;

import FoodSeer.entity.SurplusReservation;

public record SurplusReservationDto(Long id, SurplusOfferDto offer, String customerUsername,
        int quantity, BigDecimal totalPrice, Instant pickupAt, String status) {
    public static SurplusReservationDto from(SurplusReservation reservation) {
        return new SurplusReservationDto(reservation.getId(), SurplusOfferDto.from(reservation.getOffer()),
                reservation.getCustomerUsername(), reservation.getQuantity(), reservation.getTotalPrice(),
                reservation.getPickupAt(), reservation.getStatus().name());
    }
}
