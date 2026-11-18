package FoodSeer.mealplan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import FoodSeer.entity.Food;

/** The real seed menu, for planner tests. */
final class PlanFixtures {

    /** A Monday. */
    static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

    private PlanFixtures() {
    }

    static Food dish(final long id, final String name, final int price, final Integer calories,
            final String... allergens) {
        final Food f = new Food(name, 20, price, Arrays.asList(allergens));
        f.setId(id);
        f.setCalories(calories);
        return f;
    }

    /** The 31-dish seed menu (DataInitializer). */
    static List<Food> menu() {
        final List<Food> m = new ArrayList<>();
        long id = 1;
        m.add(dish(id++, "COFFEE", 3, 5, "CAFFEINE"));
        m.add(dish(id++, "TEA", 2, 2, "CAFFEINE"));
        m.add(dish(id++, "BAGEL", 4, 250, "GLUTEN", "WHEAT", "SESAME"));
        m.add(dish(id++, "BANANA", 1, 105));
        m.add(dish(id++, "APPLE", 2, 95));
        m.add(dish(id++, "ORANGE JUICE", 5, 110));
        m.add(dish(id++, "YOGURT", 4, 150, "MILK", "DAIRY", "LACTOSE"));
        m.add(dish(id++, "GRANOLA BAR", 3, 190, "TREE-NUTS", "PEANUTS", "GLUTEN", "WHEAT", "SOY"));
        m.add(dish(id++, "TURKEY SANDWICH", 12, 450, "GLUTEN", "WHEAT", "MEAT", "POULTRY", "DAIRY", "EGGS",
                "MUSTARD"));
        m.add(dish(id++, "GARDEN SALAD", 10, 120));
        m.add(dish(id++, "CAESAR SALAD", 11, 470, "MILK", "DAIRY", "EGGS", "FISH", "GLUTEN", "WHEAT"));
        m.add(dish(id++, "PASTA", 14, 600, "GLUTEN", "WHEAT", "EGGS"));
        m.add(dish(id++, "PIZZA SLICE", 8, 285, "GLUTEN", "WHEAT", "MILK", "DAIRY", "LACTOSE"));
        m.add(dish(id++, "BURRITO", 11, 650, "GLUTEN", "WHEAT", "MILK", "DAIRY", "LACTOSE", "MEAT", "BEEF", "SOY"));
        m.add(dish(id++, "VEGETABLE SOUP", 9, 110, "SOY"));
        m.add(dish(id++, "CHICKEN NOODLE SOUP", 10, 180, "GLUTEN", "WHEAT", "MEAT", "POULTRY", "EGGS"));
        m.add(dish(id++, "SUSHI ROLL", 13, 255, "FISH", "SOY", "SESAME", "EGGS"));
        m.add(dish(id++, "CHICKEN WRAP", 10, 450, "GLUTEN", "WHEAT", "MEAT", "POULTRY", "MILK", "DAIRY"));
        m.add(dish(id++, "STEAK", 28, 679, "MEAT", "BEEF"));
        m.add(dish(id++, "SALMON", 24, 412, "FISH"));
        m.add(dish(id++, "LOBSTER", 35, 320, "SHELLFISH"));
        m.add(dish(id++, "SUSHI PLATTER", 32, 500, "FISH", "SHELLFISH", "SOY", "SESAME", "EGGS"));
        m.add(dish(id++, "RIBEYE", 30, 720, "MEAT", "BEEF"));
        m.add(dish(id++, "VEGGIE BURGER", 11, 390, "GLUTEN", "WHEAT", "SOY", "SESAME"));
        m.add(dish(id++, "TOFU BOWL", 12, 380, "SOY", "SESAME"));
        m.add(dish(id++, "QUINOA SALAD", 13, 350));
        m.add(dish(id++, "HUMMUS WRAP", 9, 420, "GLUTEN", "WHEAT", "SESAME"));
        m.add(dish(id++, "CHOCOLATE CAKE", 7, 450, "GLUTEN", "WHEAT", "MILK", "DAIRY", "LACTOSE", "EGGS", "SOY"));
        m.add(dish(id++, "ICE CREAM", 6, 270, "MILK", "DAIRY", "LACTOSE", "EGGS"));
        m.add(dish(id++, "COOKIES", 5, 160, "GLUTEN", "WHEAT", "MILK", "DAIRY", "EGGS", "SOY", "TREE-NUTS",
                "PEANUTS"));
        m.add(dish(id++, "FRUIT SALAD", 8, 130));
        return m;
    }

    static Food byName(final List<Food> menu, final String name) {
        return menu.stream().filter(f -> f.getFoodName().equals(name)).findFirst().orElseThrow();
    }

    static MealPlanner.Plan plan(final List<Food> menu, final String restrictions, final int target,
            final Integer budget, final PlanRules rules) {
        return new MealPlanner().plan(new MealPlanner.Input(menu, Restrictions.parse(restrictions), MONDAY, 7, target,
                budget, rules, null, null));
    }

    static List<Food> allDishes(final MealPlanner.Plan plan) {
        final List<Food> out = new ArrayList<>();
        plan.days().forEach(d -> d.meals().forEach(m -> out.addAll(m.foods())));
        return out;
    }
}
