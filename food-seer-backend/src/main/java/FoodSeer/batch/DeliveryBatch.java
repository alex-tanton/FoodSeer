package FoodSeer.batch;

import FoodSeer.entity.DriverStats;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a batched multi-order delivery route assigned to a driver.
 */
@Entity
@Table(name = "delivery_batches")
public class DeliveryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String batchCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "driver_username",
            referencedColumnName = "username",
            foreignKey = @ForeignKey(name = "fk_batch_driver")
    )
    private DriverStats driver;

    @Column(nullable = false)
    private String status; // PROPOSED, CLAIMED, IN_TRANSIT, COMPLETED, CANCELLED

    private BigDecimal totalDistanceMiles;

    private Integer estimatedDurationMinutes;

    private BigDecimal basePayout;

    private BigDecimal batchBonus;

    private BigDecimal totalPayout;

    private Double thermalDegradationRisk; // 0.0 to 1.0 (lower is fresher)

    private Integer currentStopIndex = 0;

    private LocalDateTime createdAt;

    private LocalDateTime claimedAt;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stopSequence ASC")
    private List<BatchStop> stops = new ArrayList<>();

    public DeliveryBatch() {
        this.status = "PROPOSED";
        this.createdAt = LocalDateTime.now();
        this.currentStopIndex = 0;
        this.basePayout = BigDecimal.ZERO;
        this.batchBonus = BigDecimal.ZERO;
        this.totalPayout = BigDecimal.ZERO;
        this.totalDistanceMiles = BigDecimal.ZERO;
        this.estimatedDurationMinutes = 0;
        this.thermalDegradationRisk = 0.0;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(final String batchCode) {
        this.batchCode = batchCode;
    }

    public DriverStats getDriver() {
        return driver;
    }

    public void setDriver(final DriverStats driver) {
        this.driver = driver;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public BigDecimal getTotalDistanceMiles() {
        return totalDistanceMiles;
    }

    public void setTotalDistanceMiles(final BigDecimal totalDistanceMiles) {
        this.totalDistanceMiles = totalDistanceMiles;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public void setEstimatedDurationMinutes(final Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public BigDecimal getBasePayout() {
        return basePayout;
    }

    public void setBasePayout(final BigDecimal basePayout) {
        this.basePayout = basePayout;
    }

    public BigDecimal getBatchBonus() {
        return batchBonus;
    }

    public void setBatchBonus(final BigDecimal batchBonus) {
        this.batchBonus = batchBonus;
    }

    public BigDecimal getTotalPayout() {
        return totalPayout;
    }

    public void setTotalPayout(final BigDecimal totalPayout) {
        this.totalPayout = totalPayout;
    }

    public Double getThermalDegradationRisk() {
        return thermalDegradationRisk;
    }

    public void setThermalDegradationRisk(final Double thermalDegradationRisk) {
        this.thermalDegradationRisk = thermalDegradationRisk;
    }

    public Integer getCurrentStopIndex() {
        return currentStopIndex;
    }

    public void setCurrentStopIndex(final Integer currentStopIndex) {
        this.currentStopIndex = currentStopIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(final LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(final LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<BatchStop> getStops() {
        return stops;
    }

    public void setStops(final List<BatchStop> stops) {
        this.stops = stops;
    }

    public void addStop(final BatchStop stop) {
        stop.setBatch(this);
        this.stops.add(stop);
    }
}
