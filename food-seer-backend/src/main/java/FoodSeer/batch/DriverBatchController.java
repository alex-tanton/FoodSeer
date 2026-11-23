package FoodSeer.batch;

import FoodSeer.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/driver/batches")
public class DriverBatchController {

    private final DriverBatchService driverBatchService;
    private final UserService userService;

    public DriverBatchController(
            final DriverBatchService driverBatchService,
            final UserService userService) {
        this.driverBatchService = driverBatchService;
        this.userService = userService;
    }

    /**
     * Retrieves suggested multi-order batches optimized for route distance and thermal freshness.
     */
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<List<BatchDtos.BatchDto>> getSuggestedBatches() {
        return ResponseEntity.ok(driverBatchService.getSuggestedBatches());
    }

    /**
     * Atomically claims a batched delivery route for the authenticated driver.
     */
    @PostMapping("/{batchId}/claim")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<BatchDtos.BatchDto> claimBatch(@PathVariable final Long batchId) {
        return ResponseEntity.ok(driverBatchService.claimBatch(batchId));
    }

    /**
     * Advances the driver to the next stop in their claimed delivery batch.
     */
    @PostMapping("/{batchId}/step")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<BatchDtos.BatchStepResponseDto> advanceBatchStep(@PathVariable final Long batchId) {
        return ResponseEntity.ok(driverBatchService.advanceBatchStep(batchId));
    }

    /**
     * Retrieves the current active batch assigned to the authenticated driver, if any.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<BatchDtos.BatchDto> getActiveBatch(final Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        if (username == null && userService.getCurrentUser() != null) {
            username = userService.getCurrentUser().getUsername();
        }
        BatchDtos.BatchDto activeBatch = driverBatchService.getActiveBatchForDriver(username);
        return ResponseEntity.ok(activeBatch);
    }

    /**
     * Gets a specific batch by its ID.
     */
    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<BatchDtos.BatchDto> getBatchById(@PathVariable final Long batchId) {
        return ResponseEntity.ok(driverBatchService.getBatchById(batchId));
    }
}
