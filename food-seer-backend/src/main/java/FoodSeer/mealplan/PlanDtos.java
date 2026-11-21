package FoodSeer.mealplan;

import java.time.LocalDate;
import java.util.List;

/**
 * Request and response shapes for /api/meal-plans.
 */
public final class PlanDtos {

    private PlanDtos() {
    }

    /**
     * @param days
     *            days to plan, 1 to 7 (default 7)
     * @param weeklyBudget
     *            spending limit in dollars, or null
     * @param request
     *            free text such as "skip breakfast, pizza on Friday"
     * @param useAi
     *            let the LLM read the request
     * @param startDate
     *            first day, today to a week from today (default today)
     */
    public record CreateRequest(Integer days, Integer weeklyBudget, String request, Boolean useAi,
            LocalDate startDate) {
    }

    /**
     * @param date
     *            the day
     * @param meal
     *            BREAKFAST, LUNCH or DINNER
     */
    public record SlotRequest(LocalDate date, String meal) {
    }

    /** One dish in a meal. */
    public record Dish(Long id, String name, Integer calories, int price, List<String> allergies) {
    }

    /**
     * One meal.
     *
     * @param meal
     *            BREAKFAST, LUNCH or DINNER
     * @param target
     *            calories this meal aims for
     * @param dishes
     *            main first
     * @param calories
     *            planned calories
     * @param cost
     *            planned price in dollars
     * @param ordered
     *            true once placed as an order
     * @param orderId
     *            the order, once placed
     * @param orderable
     *            true if it can be ordered now (today and not yet ordered)
     */
    public record Meal(String meal, int target, List<Dish> dishes, int calories, int cost, boolean ordered,
            Long orderId, boolean orderable) {
    }

    /**
     * One day.
     *
     * @param date
     *            the day
     * @param dayName
     *            e.g. "Friday"
     * @param target
     *            calorie target
     * @param plannedCalories
     *            planned total
     * @param plannedCost
     *            planned spend
     * @param onTarget
     *            planned total within ±10% of target
     * @param eatenCalories
     *            calories actually recorded that day (orders and logged meals),
     *            or null for days that have not happened yet
     * @param meals
     *            planned meals
     */
    public record Day(LocalDate date, String dayName, int target, int plannedCalories, int plannedCost,
            boolean onTarget, Integer eatenCalories, List<Meal> meals) {
    }

    /**
     * A saved plan.
     *
     * @param id
     *            plan id
     * @param startDate
     *            first day
     * @param dailyGoal
     *            the customer's calorie goal
     * @param dailyBurn
     *            calories burned per day from the fitness data
     * @param dailyTarget
     *            goal plus burn
     * @param weeklyBudget
     *            spending limit, or null
     * @param totalCost
     *            whole-plan spend
     * @param budgetMet
     *            false only if no plan could fit the limit
     * @param daysOnTarget
     *            planned days within ±10%
     * @param request
     *            what the customer typed
     * @param understood
     *            the request as read, in words
     * @param aiStatus
     *            OFF, USED or FALLBACK
     * @param warnings
     *            things to know
     * @param days
     *            the days
     */
    public record Plan(Long id, LocalDate startDate, int dailyGoal, int dailyBurn, int dailyTarget,
            Integer weeklyBudget, int totalCost, boolean budgetMet, int daysOnTarget, String request,
            String understood, String aiStatus, List<String> warnings, List<Day> days) {
    }
}
