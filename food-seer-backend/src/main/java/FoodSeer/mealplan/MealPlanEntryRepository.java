package FoodSeer.mealplan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** The dishes in saved meal plans. */
public interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> {

    /** @return a plan's dishes in day, meal and position order */
    List<MealPlanEntry> findByPlanOrderByPlanDateAscIdAsc(MealPlan plan);
}
