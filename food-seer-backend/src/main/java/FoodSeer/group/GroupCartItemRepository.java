package FoodSeer.group;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupCartItemRepository extends JpaRepository<GroupCartItem, Long> {

    List<GroupCartItem> findBySessionOrderByAddedAtAsc(GroupSession session);

    List<GroupCartItem> findByMember(GroupMember member);

    void deleteBySession(GroupSession session);
}
