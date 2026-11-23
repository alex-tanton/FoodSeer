package FoodSeer.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchStopRepository extends JpaRepository<BatchStop, Long> {

    List<BatchStop> findByBatchIdOrderByStopSequenceAsc(Long batchId);

    List<BatchStop> findByOrderId(Long orderId);
}
