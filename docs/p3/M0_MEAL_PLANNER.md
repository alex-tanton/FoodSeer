# M0 claim: weekly meal planner

Written November 17, 2026, before the eval or the planner exists. The thresholds
below are fixed now. They do not change after a scored run. If the result misses
them, it is reported as a miss.

## Claim

On simulated customers planning a week from the real FoodSeer seed menu, the meal
planner puts **at least 80% of planned days within ±10% of the day's calorie
target**. That is **at least 20 percentage points more** than the best baseline,
with an effect size of **Cliff's δ ≥ 0.33** on per-seed hit rate against that
baseline.

In every run, it also has:

- **0 dishes containing a customer's true allergen**;
- **0 plans over the weekly spending limit** whenever a plan under the limit
  exists;
- **0 main dishes planned more often than the repeat cap**;
- **every pinned request honored** ("pizza on Friday") whenever the pinned dish is
  safe for the customer.

## Why this is the risk to kill first

Personal Diet already tells a customer, for **today**, which dishes fit what is
left of their calorie budget. A planner only earns its place if planning the whole
week at once does better than picking day by day. Three things make that hard:

- Calorie targets have to be hit with a small menu of fixed portions.
- Money is shared across the week, so a cheap Monday pays for a nicer Friday.
- Variety and requests constrain every choice.

If an optimizer cannot beat the obvious greedy approaches on these, the feature is
not worth building.

## Day target

**target = daily calorie goal + calories burned per day.** These are the same two
numbers Personal Diet uses (`dailyCalorieGoal` and the fitness file). The target is
split across planned meals: breakfast 25%, lunch 35%, dinner 40%, renormalized
when a meal is skipped. A day is a **hit** when its planned calories are within
±10% of its target.

## Approaches compared

All approaches plan the same customers, choose from the same safe dishes (no
declared allergen, in stock, suitable for that meal), and may serve a main dish
plus at most one side per meal.

| Approach | How it plans |
| --- | --- |
| Meal planner | Whole-week optimizer: calorie fit per day, the weekly budget, the repeat cap, requests |
| Day-by-day Personal Diet | For each meal, in order, the highest-rated dish that still fits the day's remaining calories, as a customer using today's Personal Diet would pick |
| Greedy calories | For each meal, the option closest to that meal's calorie share, within an even per-meal split of the money left |
| Random | A random safe option for each meal |

The **best baseline** is whichever of the three baselines has the highest pooled
hit rate.

## World

- **Scale:** 20 seeds, 200 customers per seed, 7-day plans with breakfast, lunch
  and dinner.
- **Menu:** the 31-dish seed menu with its prices and calories.
- **Calorie targets:** goals drawn from 1400–2200 kcal and daily burn from
  150–500 kcal, so day targets fall between 1550 and 2700 kcal. That range is
  reachable with this menu.
- **Budgets:** see the amendment below. The original range was $140 to $320 a week.
- **Allergies:** true allergies use the prevalence and the checkbox or free-text
  phrasing of the inherited trace-back eval (`M0TraceEval`).
- **Requests:** 30% of customers have one: a pinned dish on a weekday, a skipped
  meal, or a dish to avoid.
- **Repeat cap:** 2 per main dish per week. Sides (drinks, fruit, desserts) may
  repeat.

## Amendment, November 17, 2026 (before the scored run)

Two changes were made after running development seeds 101–110 and before any
run of the scored seeds. The thresholds in the pass rule are **unchanged**.

**1. Budget range.** The original range ($140–$320 a week) cannot pay for the
calorie targets with this menu. On development seeds, the planner with **no**
spending limit hits 90% of days, and its median on-target week costs $407.
A budget sweep shows the hit rate climbing with money:

| Weekly budget | $175 | $200 | $225 | $250 | $275 | $300 | $350 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Days within ±10% | 6.6% | 9.5% | 19.5% | 40.3% | 61.2% | 73.6% | 82.7% |

A claim about planning quality should not be decided by a budget that no method
could meet. Each customer's budget is therefore now **60–100% of what they would
spend ordering day by day with Personal Diet** (the day-by-day baseline's spend
for that customer), drawn uniformly. This is the scenario the feature is for:
plan the week and spend less than you would otherwise.

The original $140–$320 range is still run as a **tight-budget stress test**, and
reported but not part of the pass rule.

**2. Breakfast mains.** Banana and apple may anchor a breakfast. With only four
breakfast mains, a customer allergic to wheat and milk had one usable main, so
no plan could respect the repeat cap. Even so, some customers' allergies leave too
few mains for any plan to meet the cap (for example, three breakfast mains at 2
uses each cannot cover 7 breakfasts). Repeat-cap violations are therefore counted
only when a plan within the cap exists. This is checked independently of the
planner with Hall's condition: for every set of meal types, the distinct safe
mains for those meals, times the cap, must cover those meals' slots. Budget
violations were already counted only for feasible plans, and this is the same
rule.

**Planner fix made in the same step.** The first version spent money greedily,
day by day, so Monday could spend what Sunday needed; in the sweep, more money
sometimes gave fewer hits. It now puts a price on each dollar and searches for
the price that fits the budget, then spends any money left over. The sweep above
is from the fixed planner.

## Tuning log (development seeds 101–110 only)

| Step | Days within ±10% | Notes |
| --- | ---: | --- |
| First planner, original budgets | 41.3% | budgets cannot pay for the targets (amendment 1) |
| Amended budgets, first planner | 78.5% | 101 repeat-cap violations |
| Fill most constrained meals first; bonus for finishing a day on target (0.3) | 79.8% | 42 violations; most could not be avoided by any plan (amendment 2) |
| Pinned dish exempt from the cap only when it is the main; over-cap penalty | 79.8% | 5 avoidable violations: chains single changes cannot fix |
| Starting mains assigned by capacity matching (augmenting paths) | 80.1% | all checks pass |
| On-target bonus 0.5, margin 0.08 (from 0.3, 0.07) | 81.6% | frozen here |

## Metrics

- **Hit rate**: share of planned days within ±10% of target.
- **Budget**: plans over the weekly limit, counted only when the cheapest valid plan
  fits under it (feasible).
- **Safety**: planned dishes declaring one of the customer's true allergens, checked
  with `AllergenMatcher` against the canonical allergen.
- **Repeats**: main dishes planned more than the cap.
- **Pins**: pinned requests not honored, when the pinned dish is safe.
- Also reported, not part of the claim: mean absolute calorie error per day, weekly
  spend, and distinct dishes per week.

## Pass rule

All of these must hold:

1. Planner pooled hit rate ≥ 80%.
2. Planner hit rate ≥ best baseline + 20 percentage points.
3. Cliff's δ ≥ 0.33 on the 20 per-seed hit rates, planner vs best baseline.
4. 0 unsafe dishes, 0 feasible plans over budget, 0 repeat-cap violations and 0
   unhonored safe pins, in every seed.

## Tuning

Planner settings may be tuned only on **development seeds 101–110**. The **scored
seeds 1–20** are run once, after tuning stops, and that run is the result.

## Result (scored run, November 17, 2026): FAIL

The scored seeds 1–20 were run once, with the settings frozen above. **The claim
failed two of its seven checks.** This is reported as a miss, as written in advance.

| Approach | Days within ±10% | Mean daily calorie error | Mean weekly spend | Plans over budget |
| --- | ---: | ---: | ---: | ---: |
| Meal planner | **79.4%** | 173 kcal | $330 | 0.4% |
| Day-by-day Personal Diet | 23.1% | 539 kcal | $422 | 99.8% |
| Greedy calories | 54.9% | 284 kcal | $329 | 5.6% |
| Random | 17.1% | 601 kcal | $408 | 83.6% |

| Check | Needed | Result | |
| --- | --- | --- | --- |
| Planner days within ±10% | ≥ 80% | 79.4% | ❌ missed by 0.6 points |
| Points above best baseline (greedy) | ≥ 20 | 24.5 | ✅ |
| Cliff's δ, per-seed hit rate | ≥ 0.33 | 0.91 (large) | ✅ |
| Dishes with a true allergen | 0 | 0 | ✅ |
| Feasible plans over budget | 0 | 0 of 3985 | ✅ |
| Main dishes over the repeat cap | 0 | 1 of 3944 plans | ❌ |
| Safe pins not honored | 0 | 0 of 331 | ✅ |

On the original tight budgets (stress test), the planner hit 61.5% of days. Greedy
hit 15.7%, and day-by-day hit 23.1% while ignoring money.

**What the misses mean.**

- **Hit rate.** The planner is far better than every baseline (+24.5 points, a
  large effect), but it falls just short of the absolute 80% bar. The remaining
  misses are mostly customers whose budget is close to the cheapest valid week.
- **Repeat cap.** The one violation was a real bug. A customer pinned steak on
  Saturday, and the starting plan had already used both of steak's allowed uses
  on earlier dinners before reaching the pin.

**After the scored run (not the result).** Pinned meals now claim their dish
before any other meal. Re-running the same seeds after that fix gives 79.4% of
days on target and 0 repeat-cap violations. The fix removes the violation and
does not change the hit rate. The official result above stays a FAIL.

## Changes after the scored run (not the result)

These came from using the feature, not from the eval. The official result above
does not change.

| Change | Why | Scored seeds, current code |
| --- | --- | --- |
| Pinned meals claim their dish first | The repeat-cap bug the scored run found | 0 cap violations |
| Stronger pull toward each meal's share (0.15 → 0.6) | Real plans had 1000 kcal lunches and 180 kcal dinners | |
| Sides past 2 uses a week cost a little | The same granola bar and ice cream every day | |
| Saved request rules read back correctly | Swaps were losing "pizza on Friday" | |

With all of these, the scored seeds give **78.9%** of days on target (vs 79.4% for
the official run) and **+24.0 points** over greedy, with every guarantee at 0.
Balancing meals costs about half a point of hit rate.

**What CI checks.** `M0MealPlannerEval` writes the pre-registered verdict into its
report. The build fails on any break of the four hard guarantees (unsafe dish,
feasible plan over budget, avoidable repeat-cap violation, unhonored safe pin). It
also fails if the hit rate drops below a **regression floor** of 75%, or the lead
over the best baseline drops below 15 points. The floors are guards set below
today's results, not the claim.

## Plan requests (reported, not part of the pass rule)

Customers can type a request such as "skip breakfast, pizza on Friday, no
salads". A keyword reader always runs. The LLM (local Ollama, `qwen2.5:1.5b`)
reads the request too, and its answer is kept only where it is grounded in the
text: every avoid, prefer and pinned dish, day and meal must appear in what the
customer typed. The LLM's answer is then merged with the keyword reading, so it
can only add rules.

A request counts as read correctly only when every rule matches the hand label.
The set is in `PlanRequestEval`. **Secondary claim:** keyword + LLM accuracy ≥
keyword-only accuracy.

| Run | Set | Keyword only | Keyword + LLM | Rules not asked for (LLM) |
| --- | --- | ---: | ---: | ---: |
| First live run (**the result**) | 30 labeled | 30 / 30\* | 19 / 30 | 5 |
| After grounding fixes | 30 labeled | 30 / 30\* | 29 / 30 | 0 |
| Held out, scored once | 10 written after tuning | 6 / 10 | 7 / 10 | 1 (keyword) |

\* In-sample. The keyword reader was fixed against these 30 requests.

**Secondary claim: FAIL** on the first live run. The model added rules nobody asked
for: "more pizza" whenever pizza was pinned, "dinner" guessed from "night",
"gluten-free" read as *more* gluten-free, and "lunch" pinned as a dish. Four
general grounding rules fixed these:

1. a pinned dish is never also "more of";
2. "X-free" is never "more of";
3. a pin's dish cannot be a meal or day word;
4. a pin's meal is kept only when the request names it.

On the held-out requests the LLM adds a little: it read "lunch is covered at
work, so leave it out" as skipping lunch, which the keywords missed. Most of the
work is done by the keyword reader, and the LLM is an optional extra. Both readers
missed "a hundred and fifty bucks" and "once a week at most", and both misread
"I'm not a breakfast person" (as an avoid).

## What this does not cover

- Real customers. The world is simulated, and the person who wrote the planner
  also wrote the world. Threats to validity are reported with the results.
- Nutrition beyond calories. FoodSeer stores no protein, carbohydrate or fat data,
  so the planner does not plan for them.
