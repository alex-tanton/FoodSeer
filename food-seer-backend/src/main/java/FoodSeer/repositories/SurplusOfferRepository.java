package FoodSeer.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import FoodSeer.entity.SurplusOffer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurplusOfferRepository extends JpaRepository<SurplusOffer, Long> {
    List<SurplusOffer> findByExpiresAtAfterAndRemainingPortionsGreaterThanOrderByExpiresAtAsc(
            Instant now, int portions);

    List<SurplusOffer> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SurplusOffer o where o.id = :id")
    Optional<SurplusOffer> findLockedById(@Param("id") Long id);
}
