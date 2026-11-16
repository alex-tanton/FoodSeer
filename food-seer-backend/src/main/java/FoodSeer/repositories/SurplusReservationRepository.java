package FoodSeer.repositories;

import java.util.List;
import java.util.Optional;

import FoodSeer.entity.SurplusReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurplusReservationRepository extends JpaRepository<SurplusReservation, Long> {
    List<SurplusReservation> findByCustomerUsernameOrderByCreatedAtDesc(String username);
    List<SurplusReservation> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SurplusReservation r where r.id = :id")
    Optional<SurplusReservation> findLockedById(@Param("id") Long id);
}
