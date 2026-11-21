package FoodSeer.mealplan;

import static FoodSeer.mealplan.PlanFixtures.byName;
import static FoodSeer.mealplan.PlanFixtures.dish;
import static FoodSeer.mealplan.PlanFixtures.menu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import FoodSeer.constant.MealType;
import FoodSeer.entity.Food;

/** Dish roles, restriction resolution and plan rules. */
class PlanBasicsTest {

    @Test
    void seedDishesHaveTheirListedRoles() {
        final List<Food> m = menu();
        assertTrue(DishRoles.canBeMain(byName(m, "STEAK"), MealType.DINNER));
        assertFalse(DishRoles.canBeMain(byName(m, "STEAK"), MealType.BREAKFAST));
        assertTrue(DishRoles.canBeMain(byName(m, "BANANA"), MealType.BREAKFAST));
        assertTrue(DishRoles.canBeSide(byName(m, "BANANA"), MealType.LUNCH));
        assertFalse(DishRoles.canBeMain(byName(m, "COFFEE"), MealType.BREAKFAST));
        assertTrue(DishRoles.canBeSide(byName(m, "COFFEE"), MealType.BREAKFAST));
        assertFalse(DishRoles.canBeSide(byName(m, "STEAK"), MealType.DINNER));
        assertTrue(DishRoles.canBeSide(byName(m, "CHOCOLATE CAKE"), MealType.DINNER));
        assertFalse(DishRoles.canBeSide(byName(m, "CHOCOLATE CAKE"), MealType.BREAKFAST));
    }

    @Test
    void newDishesStaffAddGetRolesFromCalories() {
        final Food big = dish(90, "LAMB CURRY", 18, 700);
        final Food small = dish(91, "MINI MUFFIN", 3, 120);
        final Food unknown = dish(92, "MYSTERY", 3, null);
        assertTrue(DishRoles.canBeMain(big, MealType.DINNER));
        assertFalse(DishRoles.canBeMain(big, MealType.BREAKFAST));
        assertFalse(DishRoles.canBeSide(big, MealType.DINNER));
        assertTrue(DishRoles.canBeSide(small, MealType.LUNCH));
        assertFalse(DishRoles.canBeMain(small, MealType.LUNCH));
        assertFalse(DishRoles.canBeMain(unknown, MealType.LUNCH));
        assertFalse(DishRoles.canBeSide(unknown, MealType.LUNCH));
    }

    @ParameterizedTest(name = "\"{0}\" blocks {1}")
    @CsvSource({"PEANUTS,GRANOLA BAR", "dairy,YOGURT", "milk protein,ICE CREAM", "egg whites,PASTA",
        "finned fish,SALMON", "salmon and tuna,SUSHI ROLL", "shrimp,LOBSTER", "gluten,BAGEL", "soy sauce,TOFU BOWL",
        "tahini,HUMMUS WRAP", "peanut butter,COOKIES", "vegetarian,STEAK", "vegetarian,SALMON", "vegan,YOGURT",
        "cheese,PIZZA SLICE", "TREE-NUTS,GRANOLA BAR"})
    void restrictionsBlockTheRightDishes(final String typed, final String dish) {
        final Restrictions r = Restrictions.parse(typed);
        assertNotNull(r.violatedBy(byName(menu(), dish)), typed + " should block " + dish);
        assertTrue(r.unrecognized().isEmpty(), typed);
    }

    @Test
    void restrictionsDoNotOverreach() {
        final List<Food> m = menu();
        assertNull(Restrictions.parse("peanut butter").violatedBy(byName(m, "YOGURT")));
        assertNull(Restrictions.parse("fish").violatedBy(byName(m, "LOBSTER")));
        assertNull(Restrictions.parse("egg").violatedBy(byName(m, "VEGGIE BURGER")));
        assertNull(Restrictions.NONE.violatedBy(byName(m, "PASTA")));
        assertEquals(Restrictions.NONE, Restrictions.parse(" "));
        assertEquals(Restrictions.NONE, Restrictions.parse(null));
    }

    @Test
    void unknownRestrictionsBlockByNameAndAreReported() {
        final Restrictions r = Restrictions.parse("PEANUTS, kiwi").plus(Restrictions.parse("salad"));
        assertEquals(List.of("kiwi", "salad"), r.unrecognized());
        assertEquals("SALAD", r.violatedBy(byName(menu(), "GARDEN SALAD")));
        assertNull(r.violatedBy(byName(menu(), "APPLE")));
    }

    @Test
    void rulesDropAnythingOutsideTheVocabulary() {
        final PlanRules r = new PlanRules(Set.of("BREAKFAST", "brunch", "lunch"), List.of(" Salad ", "", "x".repeat(50)),
                null, java.util.Arrays.asList(new PlanRules.Pin(DayOfWeek.FRIDAY, "Pizza", "dinner"),
                        new PlanRules.Pin(null, "x", null), null),
                0, 5);
        assertEquals(Set.of("BREAKFAST", "LUNCH"), r.skipMeals());
        assertEquals(List.of("salad"), r.avoid());
        assertEquals(List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pizza", "DINNER")), r.pins());
        assertEquals(PlanRules.DEFAULT_MAX_REPEATS, r.maxRepeats());
        assertNull(r.weeklyBudget());
        assertTrue(new PlanRules(Set.of("BREAKFAST", "LUNCH", "DINNER"), null, null, null, 2, null).skipMeals()
                .isEmpty());
        assertTrue(PlanRules.NONE.isEmpty());
    }

    @Test
    void mergeSafelyKeepsEveryRestriction() {
        final PlanRules a = new PlanRules(Set.of("BREAKFAST"), List.of("salad"), List.of("sushi"),
                List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pizza", null)), 3, 200);
        final PlanRules b = new PlanRules(Set.of("LUNCH"), List.of("soup"), List.of(),
                List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pasta", null),
                        new PlanRules.Pin(DayOfWeek.MONDAY, "steak", null)), 1, 150);
        final PlanRules m = a.mergeSafely(b);
        assertEquals(Set.of("BREAKFAST", "LUNCH"), m.skipMeals());
        assertEquals(List.of("salad", "soup"), m.avoid());
        assertEquals(2, m.pins().size());
        assertEquals("pizza", m.pins().get(0).dish());
        assertEquals(1, m.maxRepeats());
        assertEquals(150, m.weeklyBudget());
        assertEquals("no breakfast; no lunch; pizza on Friday; steak on Monday; avoid salad, soup; more sushi; "
                + "each main at most 1×; under $150 this week", m.describe());
    }
}
