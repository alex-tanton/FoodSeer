package FoodSeer.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupSessionRepository extends JpaRepository<GroupSession, Long> {

    Optional<GroupSession> findBySessionCode(String sessionCode);

    boolean existsBySessionCode(String sessionCode);

    List<GroupSession> findByHostUsernameOrderByCreatedAtDesc(String hostUsername);
}
