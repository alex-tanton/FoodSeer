package FoodSeer.dto;

import java.time.LocalDateTime;

/**
 * A safety notice as the customer sees it.
 *
 * @param id
 *            notification id
 * @param incidentId
 *            the incident behind it
 * @param orderId
 *            the customer's order that may have been exposed
 * @param message
 *            the notice text
 * @param createdAt
 *            when it was created
 * @param acknowledged
 *            true once the customer has acknowledged it
 * @param acknowledgedAt
 *            when they acknowledged it, or null
 * @param remedy
 *            what the restaurant offers, or null if it has not responded yet
 * @param kind
 *            SAFETY for allergy notices, RESCUE for surplus rescue deals
 */
public record NotificationDto(Long id, Long incidentId, Long orderId, String message, LocalDateTime createdAt,
        boolean acknowledged, LocalDateTime acknowledgedAt, String remedy, String kind) {

    /** A safety notice, as built before notices had a kind. */
    public NotificationDto(final Long id, final Long incidentId, final Long orderId, final String message,
            final LocalDateTime createdAt, final boolean acknowledged, final LocalDateTime acknowledgedAt,
            final String remedy) {
        this(id, incidentId, orderId, message, createdAt, acknowledged, acknowledgedAt, remedy, "SAFETY");
    }
}
