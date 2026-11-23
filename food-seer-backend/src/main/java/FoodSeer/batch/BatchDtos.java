package FoodSeer.batch;

import java.math.BigDecimal;
import java.util.List;

public class BatchDtos {

    public static class BatchStopDto {
        private Long id;
        private int stopSequence;
        private String stopType;
        private Long orderId;
        private String orderName;
        private String locationName;
        private Double latitude;
        private Double longitude;
        private String thermalCategory;
        private String status;
        private String allergyNotice;

        public BatchStopDto() {}

        public BatchStopDto(final BatchStop stop) {
            if (stop != null) {
                this.id = stop.getId();
                this.stopSequence = stop.getStopSequence();
                this.stopType = stop.getStopType();
                this.orderId = stop.getOrderId();
                this.orderName = stop.getOrderName();
                this.locationName = stop.getLocationName();
                this.latitude = stop.getLatitude();
                this.longitude = stop.getLongitude();
                this.thermalCategory = stop.getThermalCategory();
                this.status = stop.getStatus();
                this.allergyNotice = stop.getAllergyNotice();
            }
        }

        public Long getId() { return id; }
        public void setId(final Long id) { this.id = id; }
        public int getStopSequence() { return stopSequence; }
        public void setStopSequence(final int stopSequence) { this.stopSequence = stopSequence; }
        public String getStopType() { return stopType; }
        public void setStopType(final String stopType) { this.stopType = stopType; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(final Long orderId) { this.orderId = orderId; }
        public String getOrderName() { return orderName; }
        public void setOrderName(final String orderName) { this.orderName = orderName; }
        public String getLocationName() { return locationName; }
        public void setLocationName(final String locationName) { this.locationName = locationName; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(final Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(final Double longitude) { this.longitude = longitude; }
        public String getThermalCategory() { return thermalCategory; }
        public void setThermalCategory(final String thermalCategory) { this.thermalCategory = thermalCategory; }
        public String getStatus() { return status; }
        public void setStatus(final String status) { this.status = status; }
        public String getAllergyNotice() { return allergyNotice; }
        public void setAllergyNotice(final String allergyNotice) { this.allergyNotice = allergyNotice; }
    }

    public static class BatchDto {
        private Long id;
        private String batchCode;
        private String driverUsername;
        private String status;
        private BigDecimal totalDistanceMiles;
        private Integer estimatedDurationMinutes;
        private BigDecimal basePayout;
        private BigDecimal batchBonus;
        private BigDecimal totalPayout;
        private Double thermalDegradationRisk;
        private Integer currentStopIndex;
        private List<BatchStopDto> stops;

        public BatchDto() {}

        public Long getId() { return id; }
        public void setId(final Long id) { this.id = id; }
        public String getBatchCode() { return batchCode; }
        public void setBatchCode(final String batchCode) { this.batchCode = batchCode; }
        public String getDriverUsername() { return driverUsername; }
        public void setDriverUsername(final String driverUsername) { this.driverUsername = driverUsername; }
        public String getStatus() { return status; }
        public void setStatus(final String status) { this.status = status; }
        public BigDecimal getTotalDistanceMiles() { return totalDistanceMiles; }
        public void setTotalDistanceMiles(final BigDecimal totalDistanceMiles) { this.totalDistanceMiles = totalDistanceMiles; }
        public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
        public void setEstimatedDurationMinutes(final Integer estimatedDurationMinutes) { this.estimatedDurationMinutes = estimatedDurationMinutes; }
        public BigDecimal getBasePayout() { return basePayout; }
        public void setBasePayout(final BigDecimal basePayout) { this.basePayout = basePayout; }
        public BigDecimal getBatchBonus() { return batchBonus; }
        public void setBatchBonus(final BigDecimal batchBonus) { this.batchBonus = batchBonus; }
        public BigDecimal getTotalPayout() { return totalPayout; }
        public void setTotalPayout(final BigDecimal totalPayout) { this.totalPayout = totalPayout; }
        public Double getThermalDegradationRisk() { return thermalDegradationRisk; }
        public void setThermalDegradationRisk(final Double thermalDegradationRisk) { this.thermalDegradationRisk = thermalDegradationRisk; }
        public Integer getCurrentStopIndex() { return currentStopIndex; }
        public void setCurrentStopIndex(final Integer currentStopIndex) { this.currentStopIndex = currentStopIndex; }
        public List<BatchStopDto> getStops() { return stops; }
        public void setStops(final List<BatchStopDto> stops) { this.stops = stops; }
    }

    public static class BatchStepResponseDto {
        private Long batchId;
        private Integer currentStopIndex;
        private int totalStops;
        private String status;
        private String message;
        private BatchStopDto completedStop;
        private BatchStopDto nextStop;
        private boolean batchCompleted;
        private BigDecimal totalEarningsAwarded;

        public BatchStepResponseDto() {}

        public Long getBatchId() { return batchId; }
        public void setBatchId(final Long batchId) { this.batchId = batchId; }
        public Integer getCurrentStopIndex() { return currentStopIndex; }
        public void setCurrentStopIndex(final Integer currentStopIndex) { this.currentStopIndex = currentStopIndex; }
        public int getTotalStops() { return totalStops; }
        public void setTotalStops(final int totalStops) { this.totalStops = totalStops; }
        public String getStatus() { return status; }
        public void setStatus(final String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(final String message) { this.message = message; }
        public BatchStopDto getCompletedStop() { return completedStop; }
        public void setCompletedStop(final BatchStopDto completedStop) { this.completedStop = completedStop; }
        public BatchStopDto getNextStop() { return nextStop; }
        public void setNextStop(final BatchStopDto nextStop) { this.nextStop = nextStop; }
        public boolean isBatchCompleted() { return batchCompleted; }
        public void setBatchCompleted(final boolean batchCompleted) { this.batchCompleted = batchCompleted; }
        public BigDecimal getTotalEarningsAwarded() { return totalEarningsAwarded; }
        public void setTotalEarningsAwarded(final BigDecimal totalEarningsAwarded) { this.totalEarningsAwarded = totalEarningsAwarded; }
    }
}
