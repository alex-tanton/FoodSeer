package FoodSeer.mealplan;

import java.util.Map;
import java.util.Set;

import FoodSeer.constant.MealType;
import FoodSeer.entity.Food;

/**
 * Which meals a dish can anchor (as the main) and whether it can be added as a
 * side. A plan's meal is one main plus up to two sides.
 */
public final class DishRoles {

    /** Main-dish meals for each seed-menu dish; dishes not listed are side-only. */
    static final Map<String, Set<String>> MAINS = Map.ofEntries(
            Map.entry("BAGEL", Set.of(MealType.BREAKFAST)),
            Map.entry("YOGURT", Set.of(MealType.BREAKFAST)),
            Map.entry("GRANOLA BAR", Set.of(MealType.BREAKFAST)),
            Map.entry("FRUIT SALAD", Set.of(MealType.BREAKFAST)),
            // Fruit can anchor a light breakfast, so allergies to the other
            // breakfast mains still leave enough to vary the week.
            Map.entry("BANANA", Set.of(MealType.BREAKFAST)),
            Map.entry("APPLE", Set.of(MealType.BREAKFAST)),
            Map.entry("TURKEY SANDWICH", Set.of(MealType.LUNCH)),
            Map.entry("GARDEN SALAD", Set.of(MealType.LUNCH)),
            Map.entry("CAESAR SALAD", Set.of(MealType.LUNCH)),
            Map.entry("PASTA", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("PIZZA SLICE", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("BURRITO", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("VEGETABLE SOUP", Set.of(MealType.LUNCH)),
            Map.entry("CHICKEN NOODLE SOUP", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("SUSHI ROLL", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("CHICKEN WRAP", Set.of(MealType.LUNCH)),
            Map.entry("STEAK", Set.of(MealType.DINNER)),
            Map.entry("SALMON", Set.of(MealType.DINNER)),
            Map.entry("LOBSTER", Set.of(MealType.DINNER)),
            Map.entry("SUSHI PLATTER", Set.of(MealType.DINNER)),
            Map.entry("RIBEYE", Set.of(MealType.DINNER)),
            Map.entry("VEGGIE BURGER", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("TOFU BOWL", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("QUINOA SALAD", Set.of(MealType.LUNCH)),
            Map.entry("HUMMUS WRAP", Set.of(MealType.LUNCH)));

    /** Seed-menu dishes that can be added to a meal as a side, and to which meals. */
    static final Map<String, Set<String>> SIDES = Map.ofEntries(
            Map.entry("COFFEE", Set.of(MealType.BREAKFAST, MealType.LUNCH)),
            Map.entry("TEA", Set.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)),
            Map.entry("BAGEL", Set.of(MealType.BREAKFAST)),
            Map.entry("BANANA", Set.of(MealType.BREAKFAST, MealType.LUNCH)),
            Map.entry("APPLE", Set.of(MealType.BREAKFAST, MealType.LUNCH)),
            Map.entry("ORANGE JUICE", Set.of(MealType.BREAKFAST)),
            Map.entry("YOGURT", Set.of(MealType.BREAKFAST, MealType.LUNCH)),
            Map.entry("GRANOLA BAR", Set.of(MealType.BREAKFAST, MealType.LUNCH)),
            Map.entry("FRUIT SALAD", Set.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)),
            Map.entry("GARDEN SALAD", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("VEGETABLE SOUP", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("COOKIES", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("ICE CREAM", Set.of(MealType.LUNCH, MealType.DINNER)),
            Map.entry("CHOCOLATE CAKE", Set.of(MealType.DINNER)));

    /** Below this, an unlisted dish is treated as a side rather than a lunch or dinner main. */
    static final int MAIN_MIN_CALORIES = 250;

    /**
     * Mains that also work as the second dish of a big lunch or dinner (a soup,
     * a sushi roll, a grain salad), never a second wrap, burger or plate.
     */
    static final Set<String> SECOND_DISHES = Set.of("CHICKEN NOODLE SOUP", "SUSHI ROLL", "QUINOA SALAD");

    private DishRoles() {
    }

    /**
     * @param food
     *            a dish
     * @param meal
     *            BREAKFAST, LUNCH or DINNER
     * @return true if the dish can be the main of that meal
     */
    public static boolean canBeMain(final Food food, final String meal) {
        // A dish listed both ways (banana) is a main for the meals it is listed under.
        final Set<String> listed = MAINS.get(food.getFoodName());
        if (listed != null) {
            return listed.contains(meal);
        }
        if (SIDES.containsKey(food.getFoodName())) {
            return false;
        }
        // A dish staff added later: a lunch or dinner main if it is substantial.
        return !MealType.BREAKFAST.equals(meal) && food.getCalories() != null
                && food.getCalories() >= MAIN_MIN_CALORIES;
    }

    /**
     * @param food
     *            a dish
     * @param meal
     *            BREAKFAST, LUNCH or DINNER
     * @return true if the dish can be added to that meal as a side
     */
    public static boolean canBeSide(final Food food, final String meal) {
        final Set<String> listed = SIDES.get(food.getFoodName());
        if (listed != null) {
            return listed.contains(meal);
        }
        final Set<String> asMain = MAINS.get(food.getFoodName());
        if (asMain != null) {
            // A smaller savory dish can be the second dish of a big lunch or dinner
            // (burrito + chicken noodle soup), which beats filling up on dessert.
            return !MealType.BREAKFAST.equals(meal) && SECOND_DISHES.contains(food.getFoodName());
        }
        return food.getCalories() != null && food.getCalories() < MAIN_MIN_CALORIES;
    }

    /** Seed-menu desserts. */
    static final Set<String> DESSERTS = Set.of("CHOCOLATE CAKE", "ICE CREAM", "COOKIES");
    /** Seed-menu drinks. */
    static final Set<String> DRINKS = Set.of("COFFEE", "TEA", "ORANGE JUICE");
    /**
     * Seed-menu dishes built around a protein (meat, fish, eggs, tofu, yogurt).
     * FoodSeer stores no protein amounts, so this is the planner's stand-in.
     */
    static final Set<String> PROTEIN = Set.of("STEAK", "RIBEYE", "SALMON", "LOBSTER", "SUSHI ROLL", "SUSHI PLATTER",
            "BURRITO", "TURKEY SANDWICH", "CHICKEN WRAP", "CHICKEN NOODLE SOUP", "TOFU BOWL", "YOGURT");

    /**
     * @param food
     *            a dish
     * @return true for a dessert (seed list, or a sweet-sounding name for new dishes)
     */
    public static boolean isDessert(final Food food) {
        final String n = food.getFoodName() == null ? "" : food.getFoodName();
        return DESSERTS.contains(n) || n.matches(".*\\b(CAKE|COOKIES?|ICE CREAM|BROWNIES?|PIE|DONUTS?|PUDDING|CUPCAKES?)\\b.*");
    }

    /**
     * @param food
     *            a dish
     * @return true for a drink
     */
    public static boolean isDrink(final Food food) {
        final String n = food.getFoodName() == null ? "" : food.getFoodName();
        return DRINKS.contains(n) || n.matches(".*\\b(COFFEE|TEA|JUICE|SODA|SMOOTHIE|LATTE|LEMONADE)\\b.*");
    }

    /**
     * @param food
     *            a dish
     * @return true if the dish is built around a protein
     */
    public static boolean isProteinRich(final Food food) {
        final String n = food.getFoodName() == null ? "" : food.getFoodName();
        if (PROTEIN.contains(n)) {
            return true;
        }
        if (MAINS.containsKey(n) || SIDES.containsKey(n)) {
            return false;
        }
        // A dish staff added later: judge by its tags and name.
        for (final String tag : food.getAllergies()) {
            if (tag.matches("MEAT|BEEF|PORK|POULTRY|FISH|SHELLFISH")) {
                return true;
            }
        }
        return n.matches(".*\\b(CHICKEN|TURKEY|TOFU|EGGS?|TUNA|SALMON|SHRIMP)\\b.*");
    }

    /**
     * @param food
     *            a dish
     * @return true for wheat- or sugar-heavy dishes (bread, pasta, desserts, juice)
     */
    public static boolean isCarbHeavy(final Food food) {
        if (isDessert(food) || "ORANGE JUICE".equals(food.getFoodName())) {
            return true;
        }
        for (final String tag : food.getAllergies()) {
            if (tag.matches("WHEAT|GLUTEN")) {
                return true;
            }
        }
        return false;
    }
}
