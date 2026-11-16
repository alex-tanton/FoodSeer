package FoodSeer.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "surplus_reservations")
@Getter
@Setter
@NoArgsConstructor
public class SurplusReservation {
    public enum Status { RESERVED, CANCELLED, COLLECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private SurplusOffer offer;
    @Column(nullable = false)
    private String customerUsername;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal totalPrice;
    @Column(nullable = false)
    private Instant pickupAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RESERVED;
}
