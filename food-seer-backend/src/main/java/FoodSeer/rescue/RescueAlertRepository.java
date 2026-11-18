package FoodSeer.rescue;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RescueAlertRepository extends JpaRepository<RescueAlert, Long> {
    List<RescueAlert> findByOfferId(Long offerId);

    List<RescueAlert> findByRecipientUsernameOrderByCreatedAtDesc(String recipientUsername);

    boolean existsByOfferIdAndRecipientUsername(Long offerId, String recipientUsername);

    long countByRecipientUsernameAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String recipientUsername,
            Instant from, Instant to);
}
