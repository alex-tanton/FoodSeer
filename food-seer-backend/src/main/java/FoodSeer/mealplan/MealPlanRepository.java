package FoodSeer.mealplan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import FoodSeer.entity.User;

/** Saved meal plans. */
public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    /** @return the customer's active plan, newest first */
    Optional<MealPlan> findFirstByUserAndActiveTrueOrderByIdDesc(User user);

    /** @return every active plan the customer has (normally at most one) */
    List<MealPlan> findByUserAndActiveTrue(User user);
}
