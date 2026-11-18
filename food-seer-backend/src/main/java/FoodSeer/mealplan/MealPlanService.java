package FoodSeer.mealplan;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import FoodSeer.constant.MealType;
import FoodSeer.dto.FitnessData;
import FoodSeer.dto.OrderDto;
import FoodSeer.entity.Food;
import FoodSeer.entity.MealLog;
import FoodSeer.entity.Order;
import FoodSeer.entity.User;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.MealLogRepository;
import FoodSeer.repositories.OrderRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.FitnessDataService;
import FoodSeer.service.OrderService;

/**
 * Creates, shows, swaps, orders and tracks a customer's meal plan. Builds on
 * Personal Diet: the daily target is the customer's calorie goal plus calories
 * burned (the same two numbers Personal Diet uses), and "eaten" counts orders
 * and logged meals the same way.
 */
@Service
public class MealPlanService {

    static final int DEFAULT_DAYS = 7;

    private final UserRepository users;
    private final FoodRepository foods;
    private final OrderRepository orders;
    private final MealLogRepository mealLogs;
    private final FitnessDataService fitness;
    private final OrderService orderService;
    private final MealPlanRepository plans;
    private final MealPlanEntryRepository entries;
    private final AiPlanReader aiReader;
    private final Clock clock;
    private final MealPlanner planner = new MealPlanner();
    // Saved rules must read back even if PlanRules gains derived properties (isEmpty).
    private final ObjectMapper json = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    public MealPlanService(final UserRepository users, final FoodRepository foods, final OrderRepository orders,
            final MealLogRepository mealLogs, final FitnessDataService fitness, final OrderService orderService,
            final MealPlanRepository plans, final MealPlanEntryRepository entries, final AiPlanReader aiReader) {
        this(users, foods, orders, mealLogs, fitness, orderService, plans, entries, aiReader,
                Clock.systemDefaultZone());
    }

    MealPlanService(final UserRepository users, final FoodRepository foods, final OrderRepository orders,
            final MealLogRepository mealLogs, final FitnessDataService fitness, final OrderService orderService,
            final MealPlanRepository plans, final MealPlanEntryRepository entries, final AiPlanReader aiReader,
            final Clock clock) {
        this.users = users;
        this.foods = foods;
        this.orders = orders;
        this.mealLogs = mealLogs;
        this.fitness = fitness;
        this.orderService = orderService;
        this.plans = plans;
        this.entries = entries;
        this.aiReader = aiReader;
        this.clock = clock;
    }

    /**
     * Builds and saves a new plan, replacing the customer's current one.
     *
     * @param username
     *            the customer
     * @param req
     *            days, budget, request text, AI flag, start date
     * @return the saved plan
     */
    @Transactional
    public PlanDtos.Plan create(final String username, final PlanDtos.CreateRequest req) {
        final User user = user(username);
        if (user.getDailyCalorieGoal() == null || user.getDailyCalorieGoal() <= 0) {
            throw new IllegalStateException("Set a daily calorie goal on Personal Diet first.");
        }
        final PlanDtos.CreateRequest r = req == null ? new PlanDtos.CreateRequest(null, null, null, false, null) : req;
        final LocalDate today = LocalDate.now(clock);
        final LocalDate start = r.startDate() == null ? today : r.startDate();
        if (start.isBefore(today) || start.isAfter(today.plusDays(7))) {
            throw new IllegalArgumentException("A plan must start between today and a week from today.");
        }
        final int days = r.days() == null ? DEFAULT_DAYS : Math.max(1, Math.min(7, r.days()));
        final List<Food> menu = foods.findAll();

        // Read the request: the AI if asked and available, keywords always.
        final String text = r.request() == null || r.request().isBlank() ? null
                : r.request().trim().substring(0, Math.min(500, r.request().trim().length()));
        PlanRules rules = PlanRules.NONE;
        String aiStatus = "OFF";
        if (text != null) {
            if (Boolean.TRUE.equals(r.useAi())) {
                final Optional<PlanRules> fromAi = aiReader.read(text, menu);
                aiStatus = fromAi.isPresent() ? "USED" : "FALLBACK";
                rules = fromAi.orElseGet(() -> KeywordPlanReader.read(text, menu));
            } else {
                rules = KeywordPlanReader.read(text, menu);
            }
        }
        Integer budget = r.weeklyBudget() != null && r.weeklyBudget() > 0 ? r.weeklyBudget() : null;
        if (rules.weeklyBudget() != null) {
            budget = budget == null ? rules.weeklyBudget() : Math.min(budget, rules.weeklyBudget());
        }

        final int burn = fitness.getTodaysFitnessData(user.getUsername()).map(FitnessData::caloriesBurned)
                .filter(c -> c != null && c > 0).orElse(0);
        // "Lighter" aims 15% under the usual Personal Diet target.
        final int target = (int) Math.round((user.getDailyCalorieGoal() + burn)
                * (rules.has(PlanRules.LIGHTER) ? 0.85 : 1.0));
        final MealPlanner.Plan result = planner.plan(new MealPlanner.Input(menu,
                Restrictions.parse(user.getDietaryRestrictions()), start, days, target, budget, rules, null, null));

        for (final MealPlan old : plans.findByUserAndActiveTrue(user)) {
            old.setActive(false);
            plans.save(old);
        }
        final MealPlan plan = new MealPlan();
        plan.setUser(user);
        plan.setStartDate(start);
        plan.setDays(days);
        plan.setDailyGoal(user.getDailyCalorieGoal());
        plan.setDailyBurn(burn);
        plan.setDailyTarget(target);
        plan.setWeeklyBudget(budget);
        plan.setBudgetMet(result.budgetMet());
        plan.setActive(true);
        plan.setCreatedAt(LocalDateTime.now(clock));
        plan.setRequest(text);
        plan.setRulesJson(writeRules(rules));
        plan.setAiStatus(aiStatus);
        plan.warningList(result.warnings());
        final MealPlan saved = plans.save(plan);
        for (final MealPlanner.PlannedDay day : result.days()) {
            for (final MealPlanner.PlannedMeal meal : day.meals()) {
                saveMeal(saved, day.date(), meal.meal(), meal.foods(), null);
            }
        }
        return view(saved, user);
    }

    /**
     * @param username
     *            the customer
     * @return their active plan with progress
     */
    @Transactional(readOnly = true)
    public PlanDtos.Plan current(final String username) {
        final User user = user(username);
        return view(active(user), user);
    }

    /**
     * Replaces one meal's dishes with the next best choice and re-balances the
     * rest of that day. Other days, and meals already ordered, stay as they are.
     *
     * @param username
     *            the customer
     * @param slot
     *            the meal to swap
     * @return the updated plan
     */
    @Transactional
    public PlanDtos.Plan swap(final String username, final PlanDtos.SlotRequest slot) {
        final User user = user(username);
        final MealPlan plan = active(user);
        final String meal = mealName(slot);
        final LocalDate today = LocalDate.now(clock);
        if (slot.date().isBefore(today)) {
            throw new IllegalArgumentException("Past days cannot be changed.");
        }
        final Map<MealPlanner.Slot, List<MealPlanEntry>> bySlot = bySlot(plan);
        final MealPlanner.Slot target = new MealPlanner.Slot(slot.date(), meal);
        final List<MealPlanEntry> current = bySlot.get(target);
        if (current == null || current.isEmpty()) {
            throw new ResourceNotFoundException("That meal is not in your plan.");
        }
        if (current.get(0).getOrderId() != null) {
            throw new IllegalStateException("That meal has already been ordered.");
        }

        // Keep everything except this day's unordered meals.
        final Map<MealPlanner.Slot, List<Food>> locked = new HashMap<>();
        bySlot.forEach((s, list) -> {
            if (!s.date().equals(slot.date()) || list.get(0).getOrderId() != null) {
                locked.put(s, list.stream().map(MealPlanEntry::getFood).toList());
            }
        });
        final Map<MealPlanner.Slot, Set<Long>> banned = Map.of(target, Set.of(current.get(0).getFood().getId()));
        final MealPlanner.Plan result = planner.plan(new MealPlanner.Input(foods.findAll(),
                Restrictions.parse(user.getDietaryRestrictions()), plan.getStartDate(), plan.getDays(),
                plan.getDailyTarget(), plan.getWeeklyBudget(), readRules(plan.getRulesJson()), locked, banned));

        final MealPlanner.PlannedDay day = result.days().stream().filter(d -> d.date().equals(slot.date()))
                .findFirst().orElseThrow();
        final MealPlanner.PlannedMeal replacement = day.meals().stream().filter(m -> m.meal().equals(meal))
                .findFirst().orElseThrow();
        if (replacement.foods().isEmpty()) {
            throw new IllegalStateException("No other dish fits this meal.");
        }
        for (final MealPlanner.PlannedMeal m : day.meals()) {
            if (m.locked()) {
                continue;
            }
            final List<MealPlanEntry> old = bySlot.get(new MealPlanner.Slot(slot.date(), m.meal()));
            if (old != null) {
                entries.deleteAll(old);
            }
            saveMeal(plan, slot.date(), m.meal(), m.foods(), null);
        }
        plan.warningList(result.warnings());
        plan.setBudgetMet(result.budgetMet());
        plans.save(plan);
        return view(plan, user);
    }

    /**
     * Places today's meal as a regular FoodSeer order, so it counts toward
     * Personal Diet's calories eaten today.
     *
     * @param username
     *            the customer
     * @param slot
     *            the meal (must be today)
     * @return the updated plan
     */
    @Transactional
    public PlanDtos.Plan order(final String username, final PlanDtos.SlotRequest slot) {
        final User user = user(username);
        final MealPlan plan = active(user);
        final String meal = mealName(slot);
        if (!slot.date().equals(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Only today's meals can be ordered.");
        }
        final List<MealPlanEntry> meals = bySlot(plan).get(new MealPlanner.Slot(slot.date(), meal));
        if (meals == null || meals.isEmpty()) {
            throw new ResourceNotFoundException("That meal is not in your plan.");
        }
        if (meals.get(0).getOrderId() != null) {
            throw new IllegalStateException("That meal has already been ordered.");
        }
        final OrderDto order = new OrderDto();
        order.setName("Meal plan: " + MealPlanner.dayName(slot.date()) + " " + meal.toLowerCase(Locale.ROOT));
        int cost = 0;
        for (final MealPlanEntry e : meals) {
            if (e.getFood().getAmount() <= 0) {
                throw new IllegalStateException(e.getFood().getFoodName() + " is out of stock. Swap this meal first.");
            }
            order.getFoods().add(e.getFood());
            cost += e.getFood().getPrice();
        }
        order.setCost(BigDecimal.valueOf(cost));
        order.setStatus("Placed");
        final OrderDto placed = orderService.createOrder(order);
        for (final MealPlanEntry e : meals) {
            e.setOrderId(placed.getId());
            entries.save(e);
        }
        return view(plan, user);
    }

    /**
     * @param username
     *            the customer
     */
    @Transactional
    public void delete(final String username) {
        final User user = user(username);
        for (final MealPlan p : plans.findByUserAndActiveTrue(user)) {
            p.setActive(false);
            plans.save(p);
        }
    }

    // ----------------------------------------------------------- helpers

    private User user(final String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private MealPlan active(final User user) {
        return plans.findFirstByUserAndActiveTrueOrderByIdDesc(user)
                .orElseThrow(() -> new ResourceNotFoundException("You have no meal plan yet."));
    }

    private static String mealName(final PlanDtos.SlotRequest slot) {
        if (slot == null || slot.date() == null || !MealType.isValid(slot.meal())) {
            throw new IllegalArgumentException("Give a date and a meal: BREAKFAST, LUNCH or DINNER.");
        }
        return slot.meal().toUpperCase(Locale.ROOT);
    }

    private void saveMeal(final MealPlan plan, final LocalDate date, final String meal, final List<Food> dishes,
            final Long orderId) {
        for (int i = 0; i < dishes.size(); i++) {
            final MealPlanEntry e = new MealPlanEntry();
            e.setPlan(plan);
            e.setPlanDate(date);
            e.setMealType(meal);
            e.setPosition(i);
            e.setFood(dishes.get(i));
            e.setOrderId(orderId);
            entries.save(e);
        }
    }

    private Map<MealPlanner.Slot, List<MealPlanEntry>> bySlot(final MealPlan plan) {
        final Map<MealPlanner.Slot, List<MealPlanEntry>> out = new LinkedHashMap<>();
        for (final MealPlanEntry e : entries.findByPlanOrderByPlanDateAscIdAsc(plan)) {
            out.computeIfAbsent(new MealPlanner.Slot(e.getPlanDate(), e.getMealType()), k -> new ArrayList<>()).add(e);
        }
        out.values().forEach(list -> list.sort(Comparator.comparingInt(MealPlanEntry::getPosition)));
        return out;
    }

    private String writeRules(final PlanRules rules) {
        try {
            return json.writeValueAsString(rules);
        } catch (final Exception e) {
            return null;
        }
    }

    PlanRules readRules(final String text) {
        if (text == null) {
            return PlanRules.NONE;
        }
        try {
            return json.readValue(text, PlanRules.class);
        } catch (final Exception e) {
            return PlanRules.NONE;
        }
    }

    /** Calories recorded per day: orders placed that day plus logged meals, as Personal Diet counts them. */
    private Map<LocalDate, Integer> eatenByDay(final User user) {
        final Map<LocalDate, Integer> out = new HashMap<>();
        for (final Order o : orders.findByUser(user)) {
            if (o.getCreatedAt() == null) {
                continue;
            }
            int cal = 0;
            for (final Food f : o.getFoods()) {
                cal += f.getCalories() == null ? 0 : f.getCalories();
            }
            out.merge(o.getCreatedAt().toLocalDate(), cal, Integer::sum);
        }
        for (final MealLog m : mealLogs.findByUser(user)) {
            out.merge(m.getLogDate(), m.getCalories(), Integer::sum);
        }
        return out;
    }

    private PlanDtos.Plan view(final MealPlan plan, final User user) {
        final LocalDate today = LocalDate.now(clock);
        final Map<LocalDate, Integer> eaten = eatenByDay(user);
        final Map<MealPlanner.Slot, List<MealPlanEntry>> bySlot = bySlot(plan);
        final PlanRules rules = readRules(plan.getRulesJson());
        final List<PlanDtos.Day> days = new ArrayList<>();
        int total = 0;
        int onTarget = 0;
        double shareTotal = 0;
        for (final String m : MealPlanner.MEAL_SHARE.keySet()) {
            if (!rules.skipMeals().contains(m)) {
                shareTotal += MealPlanner.MEAL_SHARE.get(m);
            }
        }
        for (int d = 0; d < plan.getDays(); d++) {
            final LocalDate date = plan.getStartDate().plusDays(d);
            final List<PlanDtos.Meal> meals = new ArrayList<>();
            int cal = 0;
            int cost = 0;
            for (final String m : MealPlanner.MEAL_SHARE.keySet()) {
                final List<MealPlanEntry> list = bySlot.get(new MealPlanner.Slot(date, m));
                if (list == null) {
                    continue;
                }
                final List<PlanDtos.Dish> dishes = new ArrayList<>();
                int mc = 0;
                int mcost = 0;
                for (final MealPlanEntry e : list) {
                    final Food f = e.getFood();
                    dishes.add(new PlanDtos.Dish(f.getId(), f.getFoodName(), f.getCalories(), f.getPrice(),
                            List.copyOf(f.getAllergies())));
                    mc += f.getCalories() == null ? 0 : f.getCalories();
                    mcost += f.getPrice();
                }
                final Long orderId = list.get(0).getOrderId();
                final int mealTarget = shareTotal == 0 ? 0
                        : (int) Math.round(plan.getDailyTarget() * MealPlanner.MEAL_SHARE.get(m) / shareTotal);
                meals.add(new PlanDtos.Meal(m, mealTarget, dishes, mc, mcost, orderId != null, orderId,
                        orderId == null && date.equals(today)));
                cal += mc;
                cost += mcost;
            }
            final boolean hit = Math.abs(cal - plan.getDailyTarget()) <= MealPlanner.HIT_TOLERANCE
                    * plan.getDailyTarget();
            onTarget += hit ? 1 : 0;
            total += cost;
            days.add(new PlanDtos.Day(date, MealPlanner.dayName(date), plan.getDailyTarget(), cal, cost, hit,
                    date.isAfter(today) ? null : eaten.getOrDefault(date, 0), meals));
        }
        return new PlanDtos.Plan(plan.getId(), plan.getStartDate(), plan.getDailyGoal(), plan.getDailyBurn(),
                plan.getDailyTarget(), plan.getWeeklyBudget(), total, plan.isBudgetMet(), onTarget, plan.getRequest(),
                rules.isEmpty() ? null : rules.describe(), plan.getAiStatus(), plan.warningList(), days);
    }
}
