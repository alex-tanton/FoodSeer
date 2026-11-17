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

## Development runs (seeds 101 to 110, not scored)

Recorded before the first scored run. Only the matcher policy was tuned; the
simulator and the thresholds did not change.

| Run | Policy change | Sell-through vs best baseline | Alerts per sale | Unsafe | Cliff's δ | Dev result |
| --- | --- | --: | --: | --: | --: | --- |
| 1 | 3 alerts per remaining portion | +88.8% | 3.03 | 0 | 1.000 | Miss (alerts per sale) |
| 2 | 2 alerts per remaining portion | +99.6% | 2.51 | 0 | 1.000 | Pass |

The alert budget K applies to the equal-budget baselines too, so lowering it
shrinks their budget by the same amount. Run 2's policy is the one scored.

## Results (scored seeds 1 to 20, run November 17, 2026)

**PASS.** Numbers copied from `target/m0-eval/m0-rescue-report.md`.

| Check | Value | Target | Met |
| --- | --: | --: | :-: |
| Sell-through vs best equal-budget baseline (most recent K) | +104.7% (0.809 vs 0.395) | ≥ +20% | yes |
| Alerts per sale (median) | 2.47 | ≤ 3.0 | yes |
| Unsafe alerts, all seeds | 0 | 0 | yes |
| Cliff's δ vs most recent K | 1.000 (large) | ≥ 0.33 | yes |

Medians over the 20 scored seeds:

| Approach | Sell-through | Alerts per sale | Unsafe alerts (total, 20 seeds) |
| --- | --: | --: | --: |
| Rescue matching | 0.809 | 2.47 | 0 |
| Notify everyone | 0.977 | 68.13 | 63,770 |
| Random K (hard filters) | 0.376 | 5.32 | 0 |
| Most recent K (hard filters) | 0.395 | 5.06 | 0 |

The per-seed table is in the generated report.

### Reading the result honestly

- **Notify everyone sells more surplus** (0.977 vs 0.809). It does so with about
  28 times as many alerts per sale and 63,770 alerts to customers who are truly
  allergic to the dish. That trade is the reason this feature exists, and it is
  why the claim compares against equal-budget baselines.
- **Zero unsafe alerts is partly by construction.** The allergen filter and the
  "withhold when unsure" rule exclude anyone whose saved text conflicts with,
  or cannot be mapped against, a declared allergen. The eval checks that against
  true allergies, including free text such as "tahini" and "egg whites" that
  `AllergenMatcher` does not understand. It cannot catch allergens a dish fails
  to declare; that is the trace-back feature's job.
- **Cliff's δ of 1.000 means the matcher won every seed pairing.** The main threat
  to validity is the simulator itself: order history is sampled from the same
  hidden taste that drives purchases, so a history-based ranker is favored by
  design. The baselines use the same hard filters, so the gap is ranking, not
  safety. Real order and reservation data would be the stronger test.
- The policy was tuned once, on development seeds only (see above).
