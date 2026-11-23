# Weekly meal planner

Project 3 milestone. It extends the inherited **Personal Diet** feature
(`docs/personal-diet.md`), and does not build on the surplus marketplace or rescue
matching.

## Problem

Personal Diet tells a customer, for **today**, which dishes fit the calories they
have left. Nobody eats one day at a time, though:

- Money is shared across the week, so a cheap Monday pays for a nicer Friday.
- Repeating the same dish gets old.
- People know ahead of time what they want: no breakfast on weekdays, pizza on
  Friday.

Picking day by day, the way Personal Diet allows today, hits the calorie target
on only 23% of days in our simulation and blows almost any weekly budget.

## What it does

A customer opens **📅 Meal Plan** (in the navigation, or **Plan My Week** on
Personal Diet), chooses how many days (1–7) and an optional spending limit, and
can type a request. FoodSeer then plans breakfast, lunch and dinner from the menu.

- **Target.** Each day aims for **daily calorie goal + calories burned**, the same
  two numbers Personal Diet uses. It is split 25% breakfast, 35% lunch and 40%
  dinner, renormalized when a meal is skipped. A meal is one main dish plus up to
  two sides.
- **Rules that are never broken:**
  - No dish that matches the customer's saved restrictions. Restrictions go through
    the trace-back `AllergenMatcher`, plus ingredient names ("tahini" means
    sesame), diets ("vegetarian", "vegan") and word fallback ("milk protein"
    means milk). Anything it still cannot map is kept, blocks dishes with that
    word in the name, and is reported.
  - Nothing out of stock or without a calorie value.
  - The weekly spending limit, whenever any plan can meet it. If none can, the
    plan says what the cheapest valid week costs.
  - Each main dish at most twice a week, unless the request changes that, and
    whenever any plan can manage it.
  - Pinned requests ("pizza on Friday"), whenever the dish is safe.
  - Meals already ordered stay as they are.
- **What it optimizes** within those rules:
  - each day close to its target, with a bonus for landing inside the ±10% band;
  - each meal close to its share of the day;
  - variety in mains and in sides;
  - requested dishes.
- **Requests.** Typed requests become rules: skipped meals, dishes or
  ingredients to avoid, dishes to favor, a dish on a weekday, a repeat limit, and
  a spending limit. A keyword reader always runs. When **Let the AI assistant
  read my request** is checked, a local LLM (Ollama, `qwen2.5:1.5b`, the runtime
  the chatbot already uses) reads it too. Its answer is kept only where it is
  grounded in the request text, and is merged with the keywords so it can only
  add rules. If Ollama is down, the page says so and keywords are used.
- **After planning:**
  - **Swap** any meal from today on. The planner picks a different main and
    re-balances the rest of that day; other days and ordered meals stay.
  - **Order** any of today's meals. This places a normal FoodSeer order, so
    Personal Diet counts it in today's calories.
  - **Track.** Each day shows planned calories against the target, and, for today
    and past days, what was actually eaten (orders plus logged meals, counted the
    way Personal Diet counts them).

## How the planner works (`MealPlanner`)

The planner is pure and deterministic: no Spring, no database, no randomness. The
service, the tests and the eval all run the same code.

1. **Options.** For each meal type, every safe main, alone or with one or two
   safe sides.
2. **Starting plan.** Mains are assigned to meals so no main exceeds the repeat
   cap, solved as a capacity matching with augmenting paths. Pinned meals claim
   their dish first. Each meal then takes its cheapest option with its main. This
   plan proves whether the budget can be met at all.
3. **Local search.** Repeatedly change the one meal whose change most improves the
   objective, until nothing helps.
4. **Budget.** If the best plan costs too much, a price per dollar (λ) is added
   to the objective. λ is searched by bisection until the plan fits, which spreads
   money across the week instead of letting Monday spend Sunday's share. A final
   pass spends any money left.

DishRoles lists which seed dishes can be a main for which meal, and which can be
a side. Dishes staff add later are mains for lunch and dinner at 250 kcal or more,
and sides below that.

## API

All endpoints are for customers only.

| Method and path | What |
| --- | --- |
| `POST /api/meal-plans` | Body `{days, weeklyBudget, request, useAi, startDate}` (all optional). Builds and saves a plan, replacing the current one. 409 if no calorie goal is set. |
| `GET /api/meal-plans/current` | The current plan, with eaten calories per day. 404 if there is none. |
| `POST /api/meal-plans/current/swap` | Body `{date, meal}`. Swaps that meal and re-balances that day. |
| `POST /api/meal-plans/current/order` | Body `{date, meal}`. Orders one of today's meals. |
| `DELETE /api/meal-plans/current` | Clears the plan. |

LLM settings: `foodseer.llm.enabled` (default `true`), `foodseer.llm.url`,
`foodseer.llm.model`, `foodseer.llm.timeout-seconds` (default 20).

Plans are saved in `meal_plans` and `meal_plan_entries`. Deleting a customer
deletes their plans (`ON DELETE CASCADE`).

## Try it

The no-setup demo runs on an in-memory database, and every demo password is
`rescue123`:

```bash
scripts/rescue-demo.sh
```

1. Log in as `maya` and open **🔥 Personal Diet**. Set a daily goal such as 1800.
2. Press **Plan My Week**. Enter a $250 limit and type `pizza on Friday, no
   salads`.
3. The plan shows each day's calories against the target, and Friday has pizza.
4. **Swap** a meal, then **Order** one of today's meals. Personal Diet now counts
   it.

To use the AI reader, run `ollama serve` and `ollama pull qwen2.5:1.5b` first.

## Results

The pre-registered claim is in [M0_MEAL_PLANNER.md](M0_MEAL_PLANNER.md), together
with its amendment, tuning log and threats.

**The scored run failed two of seven checks.** The planner hit **79.4%** of days
against the 80% bar, and one repeat-cap violation exposed a real bug, since fixed.
It beat every baseline by a wide margin (+24.5 points over greedy, Cliff's δ
0.91), with 0 unsafe dishes, 0 feasible plans over budget and every safe pin
honored.

| Approach (scored seeds, current code) | Days within ±10% | Plans over budget |
| --- | ---: | ---: |
| Meal planner | 78.9% | 0.4% (only plans no week could fit) |
| Greedy calories | 54.9% | 5.6% |
| Day-by-day Personal Diet | 23.1% | 99.8% |
| Random | 17.1% | 83.6% |

**Reading requests.** The keyword reader read 6 of 10 held-out requests exactly,
and keyword + LLM read 7 of 10. On the first live run, before grounding fixes, the
LLM *lowered* accuracy (19 of 30 vs 30 of 30) by adding rules nobody asked for.
The details are in the claim document.

## Tests

| What | Where |
| --- | --- |
| Planner rules, budget, pins, cap, locks and bans, determinism | `mealplan/MealPlannerTest` (20) |
| Dish roles, restriction resolution, rule cleaning and merging | `mealplan/PlanBasicsTest` (22) |
| Keyword reader; what is accepted from a scripted fake LLM; Ollama failing closed | `mealplan/PlanReadersTest` (10) |
| Create, swap, order (a real order), errors, replacement, cascade delete, on H2 | `mealplan/MealPlanServiceTest` (7) |
| Endpoints through the real security filter chain | `mealplan/MealPlanControllerTest` (7) |
| M0 eval, and the plan-request eval | `eval/M0MealPlannerEval`, `eval/PlanRequestEval` (CI workflow `meal-planner-eval.yml`) |
| Page and API client (Jest) | `pages/mealplan/MealPlan.test.js`, `services/mealPlanApi.test.js` |

## What was inherited and what is new

- **New:**
  - the `FoodSeer.mealplan` package;
  - `mealPlanApi.js` and `pages/mealplan/`;
  - the two evals, the workflow and these docs.
- **Changed:**
  - `App.js` (route), `Navigation.js` (link) and `PersonalDiet.js` (a **Plan My
    Week** button).
  - `KeywordPlanReader.singular` is public for the eval.
- **Reused unchanged:**
  - `AllergenMatcher`;
  - `FitnessDataService` (calories burned);
  - `OrderService.createOrder` (ordering);
  - the meal-log and order repositories (eaten calories);
  - `M0TraceEval`'s menu and allergy phrasing, and `EvalStats`.
