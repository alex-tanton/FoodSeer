package FoodSeer.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryBatchRepository extends JpaRepository<DeliveryBatch, Long> {

    Optional<DeliveryBatch> findByBatchCode(String batchCode);

    List<DeliveryBatch> findByStatus(String status);

    List<DeliveryBatch> findByDriverUsernameAndStatusIn(String username, List<String> statuses);

    Optional<DeliveryBatch> findFirstByDriverUsernameAndStatusInOrderByClaimedAtDesc(
            String username, List<String> statuses);
}
