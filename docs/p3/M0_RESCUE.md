# M0 claim: surplus rescue matching

Written November 17, 2026, before the eval or the matcher exists. The thresholds
below are fixed now. They do not change after a scored run. If the result misses
them, it is reported as a miss.

## Claim

On simulated surplus listings, rescue matching sells **at least 20% more** surplus
portions before the booking deadline than the best equal-budget baseline, with
**at most 3.0 alerts per portion sold**, **0 allergen-unsafe alerts in every run**,
and an effect size of **Cliff's δ ≥ 0.33** (at least "medium") on per-seed
sell-through against that baseline.

## Approaches compared

| Approach | Who is alerted | Filters |
| --- | --- | --- |
| Rescue matching | Top K by score | All hard filters |
| Notify everyone | Every customer | None |
| Random K | K drawn with the seed's random source | All hard filters |
| Most recent K | K who ordered anything most recently | All hard filters |

K is computed the same way for every K-based approach, so random K and most
recent K are the **equal-budget baselines**. The best one is whichever has the
higher median sell-through. Notify everyone is reported for alerts per sale and
unsafe alerts but is not an equal-budget baseline.

## Metrics

Per seed, pooled over all simulated days:

- **Sell-through:** portions reserved before the booking deadline / portions listed.
- **Alerts per sale:** alerts sent / portions reserved.
- **Unsafe alerts:** alerts sent to a customer whose **true** allergies overlap the
  listing's declared allergen tags. Measured against the simulator's ground truth,
  not against what the customer typed, so free text the matcher misreads counts.

Reported: the median of each metric over the scored seeds, the per-seed table,
and Cliff's δ (matcher vs best equal-budget baseline) with the usual bands:
|δ| < 0.147 negligible, < 0.33 small, < 0.474 medium, otherwise large.

## Simulation

The matcher never sees the simulator's ground truth.

- **Seeds:** 1 to 20 are scored. Seeds 101 to 110 are the only seeds used while
  building or tuning the matcher weights or the simulator.
- **Menu:** the real FoodSeer seed menu (names, prices, calories, allergen tags).
- **Customers:** true allergies drawn at the same prevalence as the inherited
  `M0TraceEval`. 40% save restrictions as free text, drawn from `M0TraceEval`'s
  phrase lists (some of which `AllergenMatcher` does not understand). Each customer
  also gets a cost preference, an optional calorie budget, a hidden dish
  preference, and a hidden engagement level.
- **History:** past orders are sampled from the hidden preference and engagement,
  so history is a noisy view of them. Reservations made during the simulation are
  added to history.
- **Listings:** several per day from the menu, 30% to 70% off, 2 to 10 portions,
  booking deadline 30 to 180 minutes after posting.
- **Response:** an alerted customer reserves with a probability that rises with
  hidden preference, engagement and discount, and falls with each alert already
  received that day. Customers never reserve food they are allergic to, cannot
  afford under their cost preference, or that exceeds their remaining calories.
  Customers who were not alerted find a listing by browsing at a small fixed rate
  that is the same for every approach. Portions run out first come, first served.

Exact parameter values live in `M0RescueEval.java` and are committed before the
first scored run.

## Running it

`mvn -Dtest=M0RescueEval -Dsurefire.failIfNoSpecifiedTests=false test` writes the
report to `target/m0-eval/m0-rescue-report.md`. CI runs it in its own workflow and
fails when the claim fails, the same way the inherited trace-back eval does.

## Results

Not run yet.
