package FoodSeer.mealplan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import FoodSeer.entity.User;
import jakarta.persistence.Column;
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
 * A customer's saved meal plan. Only one plan per customer is active; a new
 * plan deactivates the old one. Deleting the customer deletes their plans.
 */
@Entity
@Table(name = "meal_plans")
@Data
@NoArgsConstructor
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    private LocalDate startDate;
    private int days;
    /** Calories aimed for each day: goal plus calories burned. */
    private int dailyTarget;
    private int dailyGoal;
    private int dailyBurn;
    /** Weekly spending limit in dollars, or null. */
    private Integer weeklyBudget;
    private boolean budgetMet;
    private boolean active;
    private LocalDateTime createdAt;

    /** What the customer typed, if anything. */
    @Column(length = 500)
    private String request;
    /** The rules the request was read as, as JSON, so swaps re-plan with them. */
    @Column(length = 2000)
    private String rulesJson;
    /** OFF, USED or FALLBACK. */
    private String aiStatus;

    /** Warnings from the last planning run, one per line. */
    @Column(length = 2000)
    private String warningsText;

    /** @return the warnings as a list */
    public List<String> warningList() {
        if (warningsText == null || warningsText.isBlank()) {
            return List.of();
        }
        return List.of(warningsText.split("\n"));
    }

    /** @param warnings the warnings to keep */
    public void warningList(final List<String> warnings) {
        this.warningsText = warnings == null || warnings.isEmpty() ? null : String.join("\n", warnings);
    }
}
