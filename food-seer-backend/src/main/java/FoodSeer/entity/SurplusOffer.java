package FoodSeer.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A separately declared pool of surplus portions with a menu snapshot. */
@Entity
@Table(name = "surplus_offers")
@Getter
@Setter
@NoArgsConstructor
public class SurplusOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long foodId;
    @Column(nullable = false)
    private String foodName;
    @Column(nullable = false, length = 2000)
    private String ingredients;
    @ElementCollection
    private List<String> allergens = new ArrayList<>();
    private Integer calories;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountedPrice;
    @Column(nullable = false)
    private int remainingPortions;
    @Column(nullable = false)
    private String pickupLocation;
    @Column(nullable = false)
    private Instant pickupStart;
    @Column(nullable = false)
    private Instant pickupEnd;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private Instant createdAt;
}
