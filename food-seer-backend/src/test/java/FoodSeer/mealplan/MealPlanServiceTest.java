package FoodSeer.mealplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import FoodSeer.entity.Order;
import FoodSeer.entity.User;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.OrderRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.AllergenMatcher;
import FoodSeer.service.impl.DataInitializer;
import jakarta.persistence.EntityManager;

/**
 * The meal plan service against the real schema, repositories and order flow.
 * The LLM is switched off, so a request asked to use it falls back to keywords.
 */
@SpringBootTest(properties = "foodseer.llm.enabled=false")
@Transactional
class MealPlanServiceTest {

    private static final String CUSTOMER = "mealplan_customer";

    @Autowired private MealPlanService service;
    @Autowired private UserRepository users;
    @Autowired private FoodRepository foods;
    @Autowired private OrderRepository orders;
    @Autowired private MealPlanRepository plans;
    @Autowired private DataInitializer dataInitializer;
    @Autowired private EntityManager em;

    private User customer;

    @BeforeEach
    void setUp() {
        if (foods.count() < 20) {
            foods.deleteAll();
            dataInitializer.onApplicationReady();
        }
        customer = users.findByUsername(CUSTOMER).orElseGet(() -> users.save(User.builder().username(CUSTOMER)
                .email(CUSTOMER + "@test.foodseer").password("x").role("ROLE_CUSTOMER").build()));
        customer.setDailyCalorieGoal(1800);
        customer.setDietaryRestrictions("dairy");
        users.save(customer);
    }

    private static PlanDtos.Meal meal(final PlanDtos.Plan plan, final int day, final String meal) {
        return plan.days().get(day).meals().stream().filter(m -> m.meal().equals(meal)).findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void createBuildsASafeWeekAndReadsTheRequest() {
        final PlanDtos.Plan plan = service.create(CUSTOMER,
                new PlanDtos.CreateRequest(7, 400, "no salads, skip breakfast", true, null));
        assertEquals(LocalDate.now(), plan.startDate());
        assertEquals(7, plan.days().size());
        assertEquals(1800, plan.dailyGoal());
        assertEquals(plan.dailyGoal() + plan.dailyBurn(), plan.dailyTarget());
        assertEquals("FALLBACK", plan.aiStatus());
        assertEquals("no breakfast; avoid salads", plan.understood());
        assertTrue(plan.totalCost() <= 400);
        for (final PlanDtos.Day day : plan.days()) {
            assertEquals(List.of("LUNCH", "DINNER"), day.meals().stream().map(PlanDtos.Meal::meal).toList());
            for (final PlanDtos.Meal m : day.meals()) {
                for (final PlanDtos.Dish d : m.dishes()) {
                    assertFalse(d.name().contains("SALAD"), d.name());
                    assertFalse(AllergenMatcher.foodDeclaresAllergen(d.allergies(), "MILK"), d.name());
                }
            }
        }
        assertNotNull(plan.days().get(0).eatenCalories());
        assertNull(plan.days().get(1).eatenCalories());
        assertTrue(meal(plan, 0, "LUNCH").orderable());
        assertFalse(meal(plan, 1, "LUNCH").orderable());
        assertEquals(plan, service.current(CUSTOMER));
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void swapReplacesTheMainAndKeepsTheRules() {
        final PlanDtos.Plan before = service.create(CUSTOMER,
                new PlanDtos.CreateRequest(3, null, "no soup", false, null));
        final LocalDate today = before.days().get(0).date();
        final PlanDtos.Plan after = service.swap(CUSTOMER, new PlanDtos.SlotRequest(today, "dinner"));
        assertNotEquals(meal(before, 0, "DINNER").dishes().get(0).id(), meal(after, 0, "DINNER").dishes().get(0).id());
        assertEquals("avoid soup", after.understood());
        assertEquals(before.days().get(1), after.days().get(1)); // other days untouched
        after.days().get(0).meals().forEach(m -> m.dishes().forEach(d -> assertFalse(d.name().contains("SOUP"))));
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void orderPlacesTodaysMealAsARealOrder() {
        final PlanDtos.Plan plan = service.create(CUSTOMER, new PlanDtos.CreateRequest(2, null, null, false, null));
        final LocalDate today = plan.days().get(0).date();
        final PlanDtos.Meal lunch = meal(plan, 0, "LUNCH");
        final int eatenBefore = plan.days().get(0).eatenCalories();

        final PlanDtos.Plan ordered = service.order(CUSTOMER, new PlanDtos.SlotRequest(today, "LUNCH"));
        final PlanDtos.Meal placed = meal(ordered, 0, "LUNCH");
        assertTrue(placed.ordered());
        assertFalse(placed.orderable());
        assertEquals(eatenBefore + lunch.calories(), ordered.days().get(0).eatenCalories());
        final Order order = orders.findById(placed.orderId()).orElseThrow();
        assertEquals(CUSTOMER, order.getUser().getUsername());
        assertEquals(lunch.dishes().size(), order.getFoods().size());
        assertEquals(lunch.cost(), order.getCost().intValue());
        assertEquals("Placed", order.getStatus());

        assertThrows(IllegalStateException.class,
                () -> service.order(CUSTOMER, new PlanDtos.SlotRequest(today, "LUNCH")));
        assertThrows(IllegalStateException.class,
                () -> service.swap(CUSTOMER, new PlanDtos.SlotRequest(today, "LUNCH")));
        assertThrows(IllegalArgumentException.class,
                () -> service.order(CUSTOMER, new PlanDtos.SlotRequest(today.plusDays(1), "LUNCH")));
        // A swap on today's other meal keeps the ordered lunch.
        final PlanDtos.Plan swapped = service.swap(CUSTOMER, new PlanDtos.SlotRequest(today, "DINNER"));
        assertEquals(placed, meal(swapped, 0, "LUNCH"));
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void invalidRequestsAreRejected() {
        customer.setDailyCalorieGoal(null);
        users.save(customer);
        final IllegalStateException noGoal = assertThrows(IllegalStateException.class,
                () -> service.create(CUSTOMER, null));
        assertTrue(noGoal.getMessage().contains("calorie goal"));
        customer.setDailyCalorieGoal(1800);
        users.save(customer);

        assertThrows(IllegalArgumentException.class, () -> service.create(CUSTOMER,
                new PlanDtos.CreateRequest(7, null, null, false, LocalDate.now().minusDays(1))));
        assertThrows(IllegalArgumentException.class, () -> service.create(CUSTOMER,
                new PlanDtos.CreateRequest(7, null, null, false, LocalDate.now().plusDays(8))));
        assertThrows(ResourceNotFoundException.class, () -> service.current(CUSTOMER));
        assertThrows(ResourceNotFoundException.class, () -> service.current("nobody_here"));

        final PlanDtos.Plan plan = service.create(CUSTOMER, new PlanDtos.CreateRequest(1, null, null, false, null));
        final LocalDate today = plan.days().get(0).date();
        assertThrows(IllegalArgumentException.class,
                () -> service.swap(CUSTOMER, new PlanDtos.SlotRequest(today, "BRUNCH")));
        assertThrows(IllegalArgumentException.class, () -> service.swap(CUSTOMER, null));
        assertThrows(ResourceNotFoundException.class,
                () -> service.swap(CUSTOMER, new PlanDtos.SlotRequest(today.plusDays(3), "LUNCH")));
        assertThrows(IllegalArgumentException.class,
                () -> service.swap(CUSTOMER, new PlanDtos.SlotRequest(today.minusDays(1), "LUNCH")));
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void aNewPlanReplacesTheOldOneAndDeleteClearsIt() {
        final PlanDtos.Plan first = service.create(CUSTOMER, new PlanDtos.CreateRequest(2, null, null, false, null));
        final PlanDtos.Plan second = service.create(CUSTOMER, new PlanDtos.CreateRequest(3, null, null, false, null));
        assertNotEquals(first.id(), second.id());
        assertEquals(1, plans.findByUserAndActiveTrue(customer).size());
        assertEquals(second.id(), service.current(CUSTOMER).id());
        service.delete(CUSTOMER);
        assertThrows(ResourceNotFoundException.class, () -> service.current(CUSTOMER));
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void deletingTheCustomerDeletesTheirPlans() {
        service.create(CUSTOMER, new PlanDtos.CreateRequest(2, null, null, false, null));
        em.flush();
        em.clear();
        final Long id = customer.getId();
        em.createNativeQuery("DELETE FROM users WHERE id = :id").setParameter("id", id).executeUpdate();
        final Number left = (Number) em.createNativeQuery("SELECT COUNT(*) FROM meal_plans WHERE user_id = :id")
                .setParameter("id", id).getSingleResult();
        assertEquals(0, left.intValue());
    }

    @Test
    void savedRulesReadBackEvenWithDerivedFields() {
        final PlanRules rules = KeywordPlanReader.read("pizza on friday, no salads", foods.findAll());
        assertEquals(rules, service.readRules(
                "{\"skipMeals\":[],\"avoid\":[\"salads\"],\"prefer\":[],\"pins\":[{\"day\":\"FRIDAY\",\"dish\":\"pizza\","
                        + "\"meal\":null}],\"maxRepeats\":2,\"weeklyBudget\":null,\"empty\":false}"));
        assertEquals(PlanRules.NONE, service.readRules(null));
        assertEquals(PlanRules.NONE, service.readRules("not json"));
    }
}
