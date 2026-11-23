package FoodSeer.mealplan;

import java.time.LocalDate;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import FoodSeer.entity.Food;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One dish in one planned meal. A meal is a main (position 0) plus sides.
 */
@Entity
@Table(name = "meal_plan_entries")
@Data
@NoArgsConstructor
public class MealPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MealPlan plan;

    private LocalDate planDate;
    /** BREAKFAST, LUNCH or DINNER. */
    private String mealType;
    private int position;

    @ManyToOne
    @JoinColumn(name = "food_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Food food;

    /** The order this meal was placed as, once ordered. */
    private Long orderId;
}
