package FoodSeer.batch;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a single stop (pickup or dropoff) in a delivery batch route.
 */
@Entity
@Table(name = "batch_stops")
public class BatchStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private DeliveryBatch batch;

    @Column(nullable = false)
    private int stopSequence;

    @Column(nullable = false)
    private String stopType; // PICKUP or DROPOFF

    private Long orderId;

    private String orderName;

    private String locationName;

    private Double latitude;

    private Double longitude;

    private String thermalCategory; // HOT, COLD, AMBIENT

    @Column(nullable = false)
    private String status; // PENDING, IN_PROGRESS, COMPLETED

    private String allergyNotice;

    private LocalDateTime completedAt;

    public BatchStop() {
        this.status = "PENDING";
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public DeliveryBatch getBatch() {
        return batch;
    }

    public void setBatch(final DeliveryBatch batch) {
        this.batch = batch;
    }

    public int getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(final int stopSequence) {
        this.stopSequence = stopSequence;
    }

    public String getStopType() {
        return stopType;
    }

    public void setStopType(final String stopType) {
        this.stopType = stopType;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(final Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderName() {
        return orderName;
    }

    public void setOrderName(final String orderName) {
        this.orderName = orderName;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(final String locationName) {
        this.locationName = locationName;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(final Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(final Double longitude) {
        this.longitude = longitude;
    }

    public String getThermalCategory() {
        return thermalCategory;
    }

    public void setThermalCategory(final String thermalCategory) {
        this.thermalCategory = thermalCategory;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getAllergyNotice() {
        return allergyNotice;
    }

    public void setAllergyNotice(final String allergyNotice) {
        this.allergyNotice = allergyNotice;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(final LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
