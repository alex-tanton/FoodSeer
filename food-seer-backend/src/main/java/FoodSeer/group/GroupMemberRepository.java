package FoodSeer.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findBySessionOrderByJoinedAtAsc(GroupSession session);

    Optional<GroupMember> findBySessionAndUsername(GroupSession session, String username);
}
