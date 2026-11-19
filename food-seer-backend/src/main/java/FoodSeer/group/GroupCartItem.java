package FoodSeer.group;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An item ordered by a member in a group dining session.
 */
@Entity
@Table(name = "group_cart_items")
@Getter
@Setter
@NoArgsConstructor
public class GroupCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GroupSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private GroupMember member;

    @Column(nullable = false)
    private Long foodId;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(length = 250)
    private String specialInstructions;

    @Column(nullable = false)
    private Instant addedAt = Instant.now();

    public GroupCartItem(final GroupSession session, final GroupMember member, final Long foodId,
            final String foodName, final int price, final int quantity, final String specialInstructions) {
        this.session = session;
        this.member = member;
        this.foodId = foodId;
        this.foodName = foodName;
        this.price = price;
        this.quantity = Math.max(1, quantity);
        this.specialInstructions = specialInstructions;
        this.addedAt = Instant.now();
    }
}
