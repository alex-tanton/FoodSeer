package FoodSeer.rescue;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One customer alerted about one surplus offer, with the score that picked them.
 * The customer-facing text goes out as a {@link FoodSeer.entity.Notification};
 * this row keeps the matching details for the deals page and restaurant stats.
 */
@Entity
@Table(name = "rescue_alerts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"offerId", "recipientUsername"}))
@Getter
@Setter
@NoArgsConstructor
public class RescueAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long offerId;
    @Column(nullable = false)
    private String recipientUsername;
    private Long notificationId;
    @Column(nullable = false)
    private double score;
    @Column(nullable = false)
    private double affinity;
    @Column(nullable = false)
    private double activity;
    @Column(nullable = false)
    private double discount;
    @Column(nullable = false)
    private double urgency;
    @Column(nullable = false)
    private int pastOrdersOfDish;
    @Column(nullable = false)
    private int discountPercent;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * @param offerId the surplus offer
     * @param match the matcher's decision for this customer
     * @param createdAt when the alert was sent
     * @return a new, unsaved alert
     */
    public static RescueAlert of(final Long offerId, final RankedCandidate match, final Instant createdAt) {
        final RescueAlert alert = new RescueAlert();
        alert.setOfferId(offerId);
        alert.setRecipientUsername(match.username());
        alert.setScore(match.parts().total());
        alert.setAffinity(match.parts().affinity());
        alert.setActivity(match.parts().activity());
        alert.setDiscount(match.parts().discount());
        alert.setUrgency(match.parts().urgency());
        alert.setPastOrdersOfDish(match.pastOrdersOfDish());
        alert.setDiscountPercent(match.discountPercent());
        alert.setReason(match.reason());
        alert.setCreatedAt(createdAt);
        return alert;
    }
}
