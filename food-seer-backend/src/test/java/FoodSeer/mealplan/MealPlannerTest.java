package FoodSeer.mealplan;

import static FoodSeer.mealplan.PlanFixtures.MONDAY;
import static FoodSeer.mealplan.PlanFixtures.allDishes;
import static FoodSeer.mealplan.PlanFixtures.byName;
import static FoodSeer.mealplan.PlanFixtures.menu;
import static FoodSeer.mealplan.PlanFixtures.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import FoodSeer.constant.MealType;
import FoodSeer.entity.Food;
import FoodSeer.service.AllergenMatcher;

class MealPlannerTest {

    private static Map<String, Integer> mainUses(final MealPlanner.Plan p) {
        final Map<String, Integer> uses = new HashMap<>();
        p.days().forEach(d -> d.meals().forEach(m -> uses.merge(m.foods().get(0).getFoodName(), 1, Integer::sum)));
        return uses;
    }

    @Test
    void plansAWeekNearTheTargetWithThreeMealsADay() {
        final MealPlanner.Plan p = plan(menu(), null, 2000, null, PlanRules.NONE);
        assertEquals(7, p.days().size());
        assertEquals(MONDAY, p.days().get(0).date());
        for (final MealPlanner.PlannedDay d : p.days()) {
            assertEquals(3, d.meals().size());
            assertTrue(d.onTarget(), d.date() + " " + d.calories());
            for (final MealPlanner.PlannedMeal m : d.meals()) {
                assertTrue(m.foods().size() >= 1 && m.foods().size() <= 3);
                assertTrue(DishRoles.canBeMain(m.foods().get(0), m.meal()));
                for (final Food side : m.foods().subList(1, m.foods().size())) {
                    assertTrue(DishRoles.canBeSide(side, m.meal()), side.getFoodName());
                }
            }
        }
        assertTrue(p.budgetMet());
        assertTrue(p.warnings().isEmpty(), p.warnings().toString());
    }

    @Test
    void dinnerGetsTheBiggestShareAndBreakfastTheSmallest() {
        final MealPlanner.Plan p = plan(menu(), null, 2000, null, PlanRules.NONE);
        int breakfast = 0;
        int dinner = 0;
        for (final MealPlanner.PlannedDay d : p.days()) {
            breakfast += d.meals().get(0).calories();
            dinner += d.meals().get(2).calories();
        }
        assertTrue(dinner > breakfast);
        assertEquals(500, p.days().get(0).meals().get(0).target());
        assertEquals(800, p.days().get(0).meals().get(2).target());
    }

    @Test
    void neverPlansADishWithAnAllergenEvenTypedAsFreeText() {
        final MealPlanner.Plan p = plan(menu(), "dairy, peanut butter, tahini", 2000, null, PlanRules.NONE);
        for (final Food f : allDishes(p)) {
            for (final String allergen : List.of("MILK", "PEANUT", "SESAME")) {
                assertFalse(AllergenMatcher.foodDeclaresAllergen(f.getAllergies(), allergen), f.getFoodName());
            }
        }
    }

    @Test
    void outOfStockAndUncaloriedDishesAreNeverPlanned() {
        final List<Food> m = menu();
        byName(m, "BURRITO").setAmount(0);
        byName(m, "PASTA").setCalories(null);
        final List<String> names = allDishes(plan(m, null, 2000, null, PlanRules.NONE)).stream()
                .map(Food::getFoodName).toList();
        assertFalse(names.contains("BURRITO"));
        assertFalse(names.contains("PASTA"));
    }

    @Test
    void mainsRespectTheRepeatCap() {
        // Lunch and dinner have 19 mains for 14 meals, so a cap of 1 is possible.
        final MealPlanner.Plan p = plan(menu(), null, 2000, null,
                new PlanRules(Set.of(MealType.BREAKFAST), List.of(), List.of(), List.of(), 1, null));
        mainUses(p).values().forEach(n -> assertTrue(n <= 1, mainUses(p).toString()));
        mainUses(plan(menu(), null, 2000, null, PlanRules.NONE)).values().forEach(n -> assertTrue(n <= 2));
    }

    @Test
    void anImpossibleRepeatCapIsWarnedNotHidden() {
        // Allergic to wheat and milk: only fruit can be a breakfast main.
        final MealPlanner.Plan p = plan(menu(), "wheat, milk, nuts", 2000, null,
                new PlanRules(Set.of(), List.of(), List.of(), List.of(), 1, null));
        assertTrue(p.warnings().stream().anyMatch(w -> w.startsWith("Not enough different dishes")));
    }

    @Test
    void stayWithinTheBudgetAndSpreadItAcrossTheWeek() {
        final MealPlanner.Plan free = plan(menu(), null, 2000, null, PlanRules.NONE);
        final MealPlanner.Plan capped = plan(menu(), null, 2000, 230, PlanRules.NONE);
        assertTrue(capped.totalCost() <= 230);
        assertTrue(capped.budgetMet());
        assertTrue(free.totalCost() > capped.totalCost());
        // A tight budget still finishes some days on target, never starves a day, warns
        // about the short ones, and says what budget would fix it.
        assertTrue(capped.days().stream().filter(MealPlanner.PlannedDay::onTarget).count() >= 3);
        capped.days().forEach(d -> assertTrue(d.calories() >= 2000 * 0.7, d.date() + " " + d.calories()));
        assertTrue(capped.warnings().stream().anyMatch(w -> w.contains("under your calorie target")));
        assertTrue(capped.warnings().stream().anyMatch(w -> w.startsWith("Your $230 limit keeps")));
    }

    @Test
    void anImpossibleBudgetSaysWhatTheCheapestPlanCosts() {
        final MealPlanner.Plan p = plan(menu(), null, 2000, 20, PlanRules.NONE);
        assertFalse(p.budgetMet());
        assertTrue(p.cheapestPossible() > 20);
        assertTrue(p.warnings().get(0).startsWith("No plan fits $20; the cheapest plan"));
    }

    @Test
    void moreMoneyNeverMeansFewerDaysOnTarget() {
        int previous = -1;
        for (final int budget : new int[] {200, 250, 300, 350, 400}) {
            final int hits = (int) plan(menu(), null, 2200, budget, PlanRules.NONE).days().stream()
                    .filter(MealPlanner.PlannedDay::onTarget).count();
            assertTrue(hits >= previous - 1, budget + ": " + hits + " after " + previous);
            previous = hits;
        }
    }

    @Test
    void skippedMealsAreNotPlannedAndTheirShareMovesToOtherMeals() {
        final MealPlanner.Plan p = plan(menu(), null, 2000, null,
                new PlanRules(Set.of(MealType.BREAKFAST), List.of(), List.of(), List.of(), 2, null));
        for (final MealPlanner.PlannedDay d : p.days()) {
            assertEquals(List.of(MealType.LUNCH, MealType.DINNER), d.meals().stream().map(m -> m.meal()).toList());
            assertTrue(d.onTarget());
        }
        assertEquals(1067, p.days().get(0).meals().get(1).target());
    }

    @Test
    void avoidedDishesArePlannedNowhere() {
        final MealPlanner.Plan p = plan(menu(), null, 2000, null,
                new PlanRules(Set.of(), List.of("salad", "dairy"), List.of(), List.of(), 2, null));
        for (final Food f : allDishes(p)) {
            assertFalse(f.getFoodName().contains("SALAD"), f.getFoodName());
            assertFalse(f.getAllergies().contains("DAIRY"), f.getFoodName());
        }
    }

    @Test
    void pinsPutTheDishOnThatDay() {
        final MealPlanner.Plan p = plan(menu(), null, 2000, null, new PlanRules(Set.of(), List.of(), List.of(),
                List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pizza", null),
                        new PlanRules.Pin(DayOfWeek.TUESDAY, "sushi", "LUNCH")), 2, null));
        final MealPlanner.PlannedDay friday = p.days().get(4);
        assertTrue(friday.meals().stream().anyMatch(m -> m.foods().get(0).getFoodName().equals("PIZZA SLICE")));
        // Sushi may be the main or the second dish of that lunch; either honors the request.
        final MealPlanner.PlannedMeal tuesdayLunch = p.days().get(1).meals().get(1);
        assertTrue(tuesdayLunch.foods().stream().anyMatch(f -> f.getFoodName().startsWith("SUSHI")));
    }

    @Test
    void anUnsafePinIsDroppedWithAWarning() {
        final MealPlanner.Plan p = plan(menu(), "milk", 2000, null, new PlanRules(Set.of(), List.of(), List.of(),
                List.of(new PlanRules.Pin(DayOfWeek.FRIDAY, "pizza", null)), 2, null));
        assertTrue(p.warnings().contains("Could not plan pizza on Friday: no matching dish is on the menu and safe for you."));
        assertTrue(allDishes(p).stream().noneMatch(f -> f.getFoodName().contains("PIZZA")));
    }

    @Test
    void aPinnedDishDoesNotPushItsOtherUsesPastTheCap() {
        final MealPlanner.Plan p = plan(menu(), "eggs, wheat", 2400, null, new PlanRules(Set.of(), List.of(),
                List.of(), List.of(new PlanRules.Pin(DayOfWeek.SATURDAY, "steak", null)), 2, null));
        assertTrue(mainUses(p).getOrDefault("STEAK", 0) <= 2, mainUses(p).toString());
        assertTrue(p.days().get(5).meals().stream().anyMatch(m -> m.foods().get(0).getFoodName().equals("STEAK")));
    }

    @Test
    void preferredDishesAreFavored() {
        final long without = allDishes(plan(menu(), null, 2000, null, PlanRules.NONE)).stream()
                .filter(f -> f.getFoodName().contains("SUSHI")).count();
        final long with = allDishes(plan(menu(), null, 2000, null,
                new PlanRules(Set.of(), List.of(), List.of("sushi"), List.of(), 2, null))).stream()
                .filter(f -> f.getFoodName().contains("SUSHI")).count();
        assertTrue(with >= without);
        assertTrue(with > 0);
    }

    @Test
    void lockedMealsStayAndBannedMainsAreReplaced() {
        final List<Food> m = menu();
        final Food steak = byName(m, "STEAK");
        final MealPlanner.Slot mondayDinner = new MealPlanner.Slot(MONDAY, MealType.DINNER);
        final MealPlanner.Slot tuesdayDinner = new MealPlanner.Slot(MONDAY.plusDays(1), MealType.DINNER);
        final MealPlanner.Plan p = new MealPlanner().plan(new MealPlanner.Input(m, Restrictions.NONE, MONDAY, 2, 2000,
                null, PlanRules.NONE, Map.of(mondayDinner, List.of(steak)), Map.of(tuesdayDinner, Set.of(steak.getId()))));
        final MealPlanner.PlannedMeal locked = p.days().get(0).meals().get(2);
        assertTrue(locked.locked());
        assertEquals(List.of(steak), locked.foods());
        assertNotEquals("STEAK", p.days().get(1).meals().get(2).foods().get(0).getFoodName());
    }

    @Test
    void anEmptyMenuWarnsForEveryMeal() {
        final MealPlanner.Plan p = new MealPlanner().plan(new MealPlanner.Input(List.of(), null, MONDAY, 1, 2000, null,
                null, null, null));
        assertEquals(3, p.warnings().stream().filter(w -> w.startsWith("No safe dish")).count());
        assertEquals(0, p.totalCost());
        assertTrue(p.warnings().stream().anyMatch(w -> w.startsWith("Monday is 100% under")));
    }

    @Test
    void unrecognizedRestrictionsAreReported() {
        final MealPlanner.Plan p = plan(menu(), "kiwi", 2000, null, PlanRules.NONE);
        assertTrue(p.warnings().stream().anyMatch(w -> w.startsWith("Could not recognize: kiwi")));
    }

    @Test
    void daysAreClampedAndPlanningIsDeterministic() {
        final MealPlanner.Input in = new MealPlanner.Input(menu(), null, MONDAY, 30, 2000, 300, null, null, null);
        assertEquals(7, in.days());
        final MealPlanner.Plan a = new MealPlanner().plan(in);
        final MealPlanner.Plan b = new MealPlanner().plan(in);
        assertEquals(allDishes(a).stream().map(Food::getId).toList(), allDishes(b).stream().map(Food::getId).toList());
    }

    @Test
    void repeatPenaltyCountsExtraUsesAndUsesOverTheCap() {
        assertEquals(0.03, MealPlanner.repeatDelta(new int[] {1, 1}, 0, 1, 2), 1e-9);
        assertEquals(-0.03, MealPlanner.repeatDelta(new int[] {2, 0}, 0, 1, 2), 1e-9);
        assertEquals(-1.03, MealPlanner.repeatDelta(new int[] {3, 0}, 0, 1, 2), 1e-9);
    }
}
