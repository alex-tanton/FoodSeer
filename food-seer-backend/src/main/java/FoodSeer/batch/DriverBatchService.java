package FoodSeer.batch;

import java.util.List;

public interface DriverBatchService {

    List<BatchDtos.BatchDto> getSuggestedBatches();

    BatchDtos.BatchDto claimBatch(Long batchId);

    BatchDtos.BatchStepResponseDto advanceBatchStep(Long batchId);

    BatchDtos.BatchDto getActiveBatchForDriver(String username);

    BatchDtos.BatchDto getBatchById(Long batchId);
}
