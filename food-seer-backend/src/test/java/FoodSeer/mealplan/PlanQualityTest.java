package FoodSeer.mealplan;

import static FoodSeer.mealplan.PlanFixtures.MONDAY;
import static FoodSeer.mealplan.PlanFixtures.allDishes;
import static FoodSeer.mealplan.PlanFixtures.byName;
import static FoodSeer.mealplan.PlanFixtures.menu;
import static FoodSeer.mealplan.PlanFixtures.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import FoodSeer.constant.MealType;
import FoodSeer.entity.Food;
import FoodSeer.service.AllergenMatcher;

/** Plans that read like real meals, diet goals, and never ignoring a request silently. */
class PlanQualityTest {

    private static PlanRules goals(final String... goals) {
        return new PlanRules(Set.of(), List.of(), List.of(), List.of(), 2, null, Set.of(goals));
    }

    private static long count(final MealPlanner.Plan p, final java.util.function.Predicate<Food> test) {
        return allDishes(p).stream().filter(test).count();
    }

    @Test
    void atMostOneDessertADayAndNoDishTwiceInADay() {
        for (final int target : new int[] {1600, 2200, 2800}) {
            final MealPlanner.Plan p = plan(menu(), null, target, null, PlanRules.NONE);
            for (final MealPlanner.PlannedDay d : p.days()) {
                final List<Food> dishes = d.meals().stream().flatMap(m -> m.foods().stream()).toList();
                assertTrue(dishes.stream().filter(DishRoles::isDessert).count() <= 1, d.date() + " " + dishes);
                assertEquals(dishes.size(), new HashSet<>(dishes).size(), d.date() + " repeats a dish: " + dishes);
            }
        }
    }

    @Test
    void aMealHasAtMostOneDessertAndOneDrink() {
        final MealPlanner.Plan p = plan(menu(), null, 2800, null, PlanRules.NONE);
        for (final MealPlanner.PlannedDay d : p.days()) {
            for (final MealPlanner.PlannedMeal m : d.meals()) {
                assertTrue(m.foods().stream().filter(DishRoles::isDessert).count() <= 1);
                assertTrue(m.foods().stream().filter(DishRoles::isDrink).count() <= 1);
            }
        }
    }

    @Test
    void desertsAreOccasionalInAnOrdinaryWeek() {
        final MealPlanner.Plan p = plan(menu(), null, 1800, 250, PlanRules.NONE);
        assertTrue(count(p, DishRoles::isDessert) <= 4, "desserts: " + count(p, DishRoles::isDessert));
    }

    @Test
    void secondDishesAreSoupsSushiOrGrainSaladsNeverAnotherWrap() {
        final MealPlanner.Plan p = plan(menu(), null, 2600, null, PlanRules.NONE);
        for (final MealPlanner.PlannedDay d : p.days()) {
            for (final MealPlanner.PlannedMeal m : d.meals()) {
                for (final Food side : m.foods().subList(1, m.foods().size())) {
                    if (DishRoles.MAINS.containsKey(side.getFoodName()) && !MealType.BREAKFAST.equals(m.meal())) {
                        assertTrue(DishRoles.SECOND_DISHES.contains(side.getFoodName())
                                || DishRoles.SIDES.containsKey(side.getFoodName()), side.getFoodName());
                    }
                }
            }
        }
        assertFalse(DishRoles.canBeSide(byName(menu(), "HUMMUS WRAP"), MealType.LUNCH));
        assertTrue(DishRoles.canBeSide(byName(menu(), "CHICKEN NOODLE SOUP"), MealType.DINNER));
        assertFalse(DishRoles.canBeSide(byName(menu(), "CHICKEN NOODLE SOUP"), MealType.BREAKFAST));
    }

    @Test
    void vegetarianAndVeganAreHardFilters() {
        for (final Food f : allDishes(plan(menu(), null, 2000, null, goals(PlanRules.VEGETARIAN)))) {
            for (final String tag : List.of("MEAT", "FISH", "SHELLFISH")) {
                assertFalse(AllergenMatcher.foodDeclaresAllergen(f.getAllergies(), tag), f.getFoodName());
            }
        }
        for (final Food f : allDishes(plan(menu(), null, 2000, null, goals(PlanRules.VEGAN)))) {
            for (final String tag : List.of("MEAT", "FISH", "SHELLFISH", "MILK", "EGG")) {
                assertFalse(AllergenMatcher.foodDeclaresAllergen(f.getAllergies(), tag), f.getFoodName());
            }
        }
    }

    @Test
    void noDessertMeansNone() {
        assertEquals(0, count(plan(menu(), null, 2600, null, goals(PlanRules.NO_DESSERT)), DishRoles::isDessert));
    }

    @Test
    void goalsShiftTheDishesChosen() {
        final MealPlanner.Plan base = plan(menu(), null, 2000, null, PlanRules.NONE);
        final MealPlanner.Plan protein = plan(menu(), null, 2000, null, goals(PlanRules.HIGH_PROTEIN));
        final MealPlanner.Plan lowCarb = plan(menu(), null, 2000, null, goals(PlanRules.LOW_CARB));
        final MealPlanner.Plan fewer = plan(menu(), null, 2000, null, goals(PlanRules.FEWER_DESSERTS));
        assertTrue(proteinMains(protein) > proteinMains(base), proteinMains(protein) + " vs " + proteinMains(base));
        assertTrue(count(lowCarb, DishRoles::isCarbHeavy) < count(base, DishRoles::isCarbHeavy));
        assertTrue(count(fewer, DishRoles::isDessert) <= count(base, DishRoles::isDessert));
    }

    private static long proteinMains(final MealPlanner.Plan p) {
        return p.days().stream().flatMap(d -> d.meals().stream())
                .filter(m -> DishRoles.isProteinRich(m.foods().get(0))).count();
    }

    @Test
    void dishKindsForNewDishesComeFromTheirNameAndTags() {
        assertTrue(DishRoles.isDessert(PlanFixtures.dish(90, "APPLE PIE", 5, 300)));
        assertTrue(DishRoles.isDrink(PlanFixtures.dish(91, "MANGO SMOOTHIE", 5, 200)));
        assertTrue(DishRoles.isProteinRich(PlanFixtures.dish(92, "LAMB CURRY", 15, 700, "MEAT")));
        assertTrue(DishRoles.isProteinRich(PlanFixtures.dish(93, "EGG BOWL", 9, 400)));
        assertFalse(DishRoles.isProteinRich(byName(menu(), "PASTA")));
        assertFalse(DishRoles.isProteinRich(PlanFixtures.dish(94, "RICE PLATE", 9, 400)));
        assertTrue(DishRoles.isCarbHeavy(byName(menu(), "BAGEL")));
        assertTrue(DishRoles.isCarbHeavy(byName(menu(), "ORANGE JUICE")));
        assertFalse(DishRoles.isCarbHeavy(byName(menu(), "STEAK")));
    }

    @Test
    void requestWordsThatMatchNothingAreReportedNotIgnored() {
        final MealPlanner.Plan p = plan(menu(), null, 2000, null, new PlanRules(Set.of(), List.of("kale"),
                List.of("lasagna"), List.of(), 2, null));
        assertTrue(p.warnings().contains("\"lasagna\" does not match any dish on the menu, so it was not used."));
        assertTrue(p.warnings().contains("Nothing on the menu matches \"kale\", so nothing was removed for it."));
    }

    @Test
    void aBindingBudgetSaysWhatWouldFixIt() {
        final MealPlanner.Plan p = new MealPlanner().plan(new MealPlanner.Input(menu(), null, MONDAY, 3, 2500, 100,
                new PlanRules(Set.of(MealType.BREAKFAST), List.of(), List.of(), List.of(), 2, null), null, null));
        assertTrue(p.warnings().stream().anyMatch(w -> w.matches("Your \\$100 limit keeps \\d days? under target; "
                + "about \\$\\d+ would plan \\d of 3 days on target\\.")), p.warnings().toString());
    }

    @Test
    void goalsAreCleanedMergedDescribedAndSaved() throws Exception {
        final PlanRules r = new PlanRules(Set.of(), List.of(), List.of(), List.of(), 2, null,
                Set.of("high_protein", "VEGAN", "VEGETARIAN", "NO_DESSERT", "FEWER_DESSERTS", "PIZZA"));
        assertEquals(Set.of(PlanRules.HIGH_PROTEIN, PlanRules.VEGAN, PlanRules.NO_DESSERT), r.goals());
        assertTrue(r.describe().startsWith("vegan; high protein"));
        final PlanRules merged = goals(PlanRules.LOW_CARB).mergeSafely(goals(PlanRules.LIGHTER));
        assertEquals(Set.of(PlanRules.LOW_CARB, PlanRules.LIGHTER), merged.goals());
        final ObjectMapper json = new ObjectMapper();
        assertEquals(r, json.readValue(json.writeValueAsString(r), PlanRules.class));
    }
}
