# Surplus rescue matching

Project 3 milestone. Builds on the surplus marketplace (`docs/surplus-marketplace.md`):
staff already publish discounted surplus portions and customers reserve them for
pickup. This milestone decides **which customers to tell** about a listing, and
tells them safely.

## Problem

Discounted surplus only helps if someone reserves it before the booking deadline.
Alerting every customer is spam. Alerting the wrong customer is dangerous, because
surplus food is where allergy mistakes happen.

## Try it (no setup)

```bash
scripts/rescue-demo.sh
```

Starts the backend on an in-memory H2 database with the `demo` profile (no MySQL,
nothing to configure) and the React frontend on http://localhost:3000. Every demo
account's password is `rescue123`.

1. Log in as `demo_staff` → **Rescue Matching** → **Preview matches for STEAK**.
   One steak portion is left. maya (ordered it 3 times) and leo (once) are ranked
   and will be alerted; ana (new customer) is eligible but over the alert budget;
   vic (vegetarian), tara ("tahini", which FoodSeer cannot map) and ben (budget
   preference, deal costs $12) are excluded, each with the reason.
2. **Send rescue alerts for STEAK**: maya and leo get a rescue deal notice.
3. Log in as `maya` → **Rescue Deals (1)** → **Reserve 1 portion of STEAK**.
4. Log in as `demo_staff` → **Rescue Matching**: 2 alerted, 1 reserved, 1 meal
   rescued, $12.00 recovered, customers saved $16.00.

Those numbers are from running the flow against the demo backend over HTTP on
November 17, 2026.

## What was inherited and what is new

Fork point: upstream commit `9bde2b8` (Oct 21, 2026). This milestone is commits
`0cd00b3` onward on `feature/surplus-rescue-matching` (PR #5). Across the whole
delta only 8 existing lines were removed; everything else is additions.

| Inherited (reused, not rewritten) | Used for |
| --- | --- |
| `AllergenMatcher` | The only allergy check. No second matcher was written. |
| `CalorieBudgetService` | Remaining calories today for customers who track them. |
| `User.costPreference` | Price ceiling (budget $10, moderate $20, premium $35, same as the recommendations page). |
| `Order` history | How often a customer ordered the listed dish, and how recently they ordered anything. |
| `Notification` channel | Delivery and unread state for rescue alerts. |
| `SurplusOffer` / `SurplusReservation` and `SurplusService` (teammate milestone) | The listing being matched, the one-tap reservation, and the sale. |

New code lives in `FoodSeer.rescue` (backend), `src/pages/rescue/` and
`src/services/rescueApi.js` (frontend), `FoodSeer.eval.M0RescueEval` (eval) and
`scripts/rescue-demo.sh`.

### Inherited files changed (wiring only)

| File | Change | Why |
| --- | --- | --- |
| `entity/Notification.java` | `incidentId` optional; new `kind` (SAFETY or RESCUE; null reads as SAFETY) | Rescue alerts have no incident |
| `dto/NotificationDto.java` | Adds `kind`; the old 8-argument constructor is kept | Inherited tests compile unchanged |
| `service/impl/NotificationServiceImpl.java` | Passes `kind` (1 line) | |
| `pages/SafetyNotices.js` | +14 / −1: rescue deals render as their own card | A deal must not look like a safety warning |
| `components/Navigation.js` | Counts safety and rescue notices separately; links to the two rescue pages | The inherited banner said "unread food safety notices" for every unread notice |
| `App.js` | Two routes | |

**Existing MySQL databases:** Hibernate's `ddl-auto=update` adds the `kind`
column but does not drop the NOT NULL on `notifications.incident_id`. Fresh
databases (demo, CI) are fine. An existing local database needs:

```sql
ALTER TABLE notifications MODIFY incident_id BIGINT NULL;
```

## How matching works

`RescueMatcher.match(listing, candidates, knownAllergens, now, policy)` is pure
and deterministic: it reads no clock, database or random source, and ties break
by username. `RescueCandidateFactory` looks up its inputs and `RescueService`
acts on its output.

### Hard filters (never traded off against the score)

A customer is excluded when the first of these applies:

1. **Time:** less than 10 minutes before the booking deadline, or no portions left.
2. **Allergen:** any allergen tag on the listing affects the customer's saved
   restrictions, per `AllergenMatcher.restrictionsAffectedBy`. This is the same
   check the marketplace applies when reserving, so the two cannot disagree.
   Synonyms and groups carry over: "dairy" blocks MILK, "almonds" blocks
   TREE-NUTS, "wheat" blocks GLUTEN.
3. **Uncertain restriction:** the customer saved a restriction FoodSeer cannot
   map to any known allergen ("soy sauce", "egg whites", "tahini") and the
   listing declares at least one allergen. Unsolicited alerts withhold when
   unsure; the customer can still browse the marketplace.
4. **Calories:** the customer tracks calories and the dish's calories are
   unknown or above what remains today. The inherited budget does not count
   surplus reservations, so calories reserved today are subtracted here.
5. **Price:** the discounted price is above the cost-preference ceiling.
6. **Already alerted** about this listing.
7. **Daily cap:** two rescue alerts per customer per day.

Only customer accounts are candidates.

### Ranking

Score = 0.5 × dish affinity + 0.3 × recent activity + 0.1 × discount + 0.1 × urgency,
each part in [0, 1]:

- **Dish affinity:** past orders containing this dish, capped at 3.
- **Recent activity:** 1 for an order just now, falling to 0 at 14 days.
- **Discount:** share off the menu price.
- **Urgency:** 1 at the deadline, 0 at 3 hours or more.

Discount and urgency are the same for every customer of one listing; they order
deals across listings on the customer's page. Each match carries a reason line
built from the parts: "you ordered this 3 times · 57% off · 1 h 29 min left".

### Alerting

The alert budget K is 2 per remaining portion, at most 20. Sending alerts the
top K, writes one RESCUE notification and one `RescueAlert` row (score parts,
reason, notification id) per customer, and skips anyone already alerted, so
sending twice never alerts anyone twice. A unique constraint on (offer,
customer) backs that up.

### Stats

Per offer: customers alerted; alerted customers who reserved (a reservation that
was not cancelled, made at or after their alert); portions rescued and
collected; revenue recovered; customer savings against the menu price.
Cancelled reservations never count.

## API

| Method and path | Role | Result |
| --- | --- | --- |
| `GET /api/rescue/offers/{id}/matches` | Staff, admin | Ranked customers with score parts and reasons, excluded customers with the filter that excluded them, and K |
| `POST /api/rescue/offers/{id}/notify` | Staff, admin | Alert the top K; returns who was alerted |
| `GET /api/rescue/deals` | Customer | The customer's open deals, best first, with a refreshed reason line |
| `GET /api/rescue/stats` | Staff, admin | Stats for every offer |
| `GET /api/rescue/offers/{id}/stats` | Staff, admin | Stats for one offer |

Reserving reuses `POST /api/surplus/offers/{id}/reservations` (pickup, not
delivery). The deals page picks the earliest valid pickup time.

## Tests

All counts are from runs on November 17, 2026 (JDK 21, H2). JUnit and Jest count
each parameterized row as one test.

| Suite | New tests | Result |
| --- | --: | --- |
| Backend `FoodSeer.rescue` (matcher filters, allergen synonyms, ranking, reasons, policy, candidate lookup, service, H2 persistence, controller security, stats, demo seed, notification kind) | 160 | pass |
| Backend `EvalStats` (median, Cliff's delta) | 17 | pass |
| Backend browser test `RescueMatchingFlowTest` (real Chrome, API, database) | 1 | pass (frontend on port 3001) |
| Frontend Jest (deals page, staff page, notices card, navigation counts, API client) | 33 | pass |
| **Total new** | **211** | |

Whole suites after this milestone:

| Suite | Result |
| --- | --- |
| Backend, everything except browser tests | 543 / 543 pass (366 inherited and teammate, 177 new) |
| Frontend Jest | 74 / 74 pass (41 inherited and teammate, 33 new) |
| Inherited browser tests | Not run locally (they need the frontend on port 3000, which was in use); CI runs them |

Run them:

```bash
cd food-seer-backend
mvn test                                   # everything (browser tests need the frontend on :3000)
mvn -Dtest='FoodSeer.rescue.*Test' test    # rescue only
mvn -Dtest=RescueMatchingFlowTest -Dfrontend.url=http://localhost:3001 test
cd ../food-seer-frontend
CI=true npm test -- --watchAll=false rescue Rescue SafetyNotices
```

## Evaluation

Claim, protocol and full results: `docs/p3/M0_RESCUE.md`. The thresholds were
committed before the eval existed; the simulator and the one policy change were
pushed before the first scored run.

Scored seeds 1 to 20: **PASS**.

| Approach | Sell-through (median) | Alerts per sale (median) | Unsafe alerts (total) |
| --- | --: | --: | --: |
| Rescue matching | 0.809 | 2.47 | 0 |
| Notify everyone | 0.977 | 68.13 | 63,770 |
| Random K (hard filters) | 0.376 | 5.32 | 0 |
| Most recent K (hard filters) | 0.395 | 5.06 | 0 |

+104.7% sell-through over the best equal-budget baseline, Cliff's δ = 1.000
(large). Notify-everyone sells more but at 28 times the alerts per sale and with
63,770 alerts to truly allergic customers. The main threat to validity is the
simulator: history is sampled from the same hidden taste that drives purchases.

The inherited trace-back eval (`M0TraceEval`) still runs and reports the same
numbers as before this milestone: recall 0.890, precision 0.287 (it misses its
own 0.95 / 0.70 targets, as documented in `docs/allergy-trace-back.md`).

```bash
mvn -Dtest=M0RescueEval -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Dtest=M0TraceEval -Dsurefire.failIfNoSpecifiedTests=false test
```

## Limits

- One restaurant, pickup only, as in the marketplace.
- Staff send alerts from the Rescue Matching page; publishing does not alert
  automatically, so the marketplace code stays untouched.
- The matcher trusts the dish's declared allergen tags. Allergens a dish fails
  to declare are the trace-back feature's job.
- The eval is a simulation. Real order and reservation data would be the
  stronger test.
