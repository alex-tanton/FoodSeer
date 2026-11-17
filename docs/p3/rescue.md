# Surplus rescue matching

Project 3 milestone. Builds on the surplus marketplace (`docs/surplus-marketplace.md`):
staff already publish discounted surplus portions and customers reserve them for
pickup. This milestone decides **which customers to tell** about a new listing, and
tells them safely.

## Problem

Discounted surplus only helps if someone reserves it before the booking deadline.
Alerting every customer is spam. Alerting the wrong customer is dangerous, because
surplus food is where allergy mistakes happen.

## What was inherited and what is new

Fork point: upstream commit `9bde2b8` (Oct 21, 2026). Everything after it is team work.

| Inherited (reused, not rewritten) | Used for |
| --- | --- |
| `AllergenMatcher` | The only allergy check. No second matcher is written. |
| `CalorieBudgetService` | Remaining calories today for customers who track them. |
| `User.costPreference` | Price ceiling (budget $10, moderate $20, premium $35, same as the recommendations page). |
| `Order` history | How often a customer ordered the listed dish. |
| `Notification` + `NotificationService` | Delivery channel and unread count for rescue alerts. |
| `SurplusOffer` / `SurplusReservation` (teammate milestone) | The listing being matched and the purchase that counts as a sale. |

New code lives in `FoodSeer.rescue` (backend) and `src/pages/rescue/` (frontend).
Inherited files change only to wire the feature in.

## Scope and acceptance rules

### Matcher

- `RescueMatcher` is a pure, deterministic function of
  `(listing, candidates, known allergen vocabulary, now, policy)`. It reads no clock,
  database, or random source. Ties break by username.
- It returns every candidate as either **matched** (with score parts) or
  **excluded** (with the filter that excluded them), so staff can audit a run.

### Hard filters (never violated)

A customer is excluded when any of these holds:

1. **Allergen.** Any allergen tag on the listing affects the customer's saved
   restrictions, per `AllergenMatcher.restrictionsAffectedBy`. This is the same check
   the marketplace uses when reserving, so the two can never disagree.
2. **Uncertain restriction.** The customer saved a restriction FoodSeer cannot map
   to any known allergen (for example "soy sauce" or "egg whites") and the listing
   declares at least one allergen. Unsolicited alerts withhold when unsure; the
   customer can still browse the marketplace.
3. **Calories.** The customer tracks calories and the listing's calories are unknown
   or above what remains today (budget minus surplus already reserved today).
4. **Price.** The discounted price is above the customer's cost-preference ceiling.
5. **Time.** Less than the policy's lead time remains before the booking deadline,
   or no portions remain.
6. **Daily cap.** The customer already received the policy's maximum rescue alerts
   today, or was already alerted about this listing.

Only accounts with the customer role are candidates.

### Ranking

Each matched (customer, listing) pair gets a score that is a weighted sum of parts
in [0, 1], all stored so the UI can explain them:

- **Dish affinity:** past orders containing this dish (capped).
- **Recent activity:** how recently the customer ordered anything.
- **Discount:** share off the menu price.
- **Urgency:** how close the booking deadline is.

Within one listing only the customer-specific parts change the order. Across
listings (the customer's deals page) all parts matter. The reason line is built
from the parts, for example "you ordered this twice · 40% off · 25 min left".

### Alerts

- Staff can preview the ranked and excluded lists before sending.
- Sending alerts the top K matched customers, where K scales with remaining
  portions up to a policy maximum, and writes one rescue alert per customer through
  the existing notification channel. Rescue alerts are labelled separately from
  allergy safety notices.
- Sending twice never alerts the same customer twice for one listing.

### Customer view

"Rescue deals for you" lists the customer's alerted, still-available listings with
the reason line and a one-tap reserve that uses the marketplace's existing
reservation endpoint (pickup, not delivery).

### Restaurant view

Per listing: customers alerted, alerted customers who reserved, portions rescued,
revenue recovered, and customer savings. Cancelled reservations do not count.

## Evaluation

The M0 claim, thresholds, and protocol are fixed in `docs/p3/M0_RESCUE.md` before
any scored run.
