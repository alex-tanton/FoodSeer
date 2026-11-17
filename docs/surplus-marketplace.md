# Surplus food marketplace

Staff and admins publish discounted portions of an existing menu item. Customers
reserve portions and choose a time inside the pickup window, then pay at pickup.
This first version uses FoodSeer's existing single-restaurant model.

## Contract

- Surplus is a separate pool of prepared portions explicitly declared by staff;
  publishing does not subtract from regular menu inventory.
- Publishing snapshots the menu name, original price, calories, and allergen tags.
  Staff must also supply ingredients and a pickup location. Later menu edits do
  not change an existing offer or reservation.
- Prices are USD, positive, at most two decimal places, and below the menu price.
- All API timestamps are UTC instants. The browser displays local times.
- The booking deadline must be in the future and no later than pickup end.
  Pickup start must precede pickup end; pickup end must be in the future.
- Only offers with remaining portions and a future booking deadline are browsable.
- Reservations require a positive whole quantity and a future pickup time inside
  the offer window. Server-calculated totals use the published discount price.
- Reservations serialize on the offer's database row to prevent overselling.
- Customers see only their own reservations. Staff/admins see all reservations.
- Customers may cancel their own reserved portions before the pickup time;
  cancellation returns portions to the offer. Staff may mark reserved portions
  collected during the pickup window, exactly once.
- Known conflicts with saved dietary restrictions block reservations using the
  existing allergen matcher. Displaying allergen tags does not certify a meal is
  allergen-free.
- Surplus reservations are separate from delivery orders and calorie meal logs.

## Implementation and verification

1. Persist offers and reservations with immutable food snapshots.
2. Implement publishing, browsing, reservation, cancellation, and collection.
3. Expose authenticated APIs with explicit role restrictions.
4. Add a marketplace page for staff and customers, with navigation.
5. Verify validation, permissions, reservation ownership, expiry, money totals,
   concurrent reservations, and frontend success/error flows.

Keep each step in small commits. Run backend/frontend regression suites and the
repository's lint, formatting, build, and static-analysis checks.

## Try it

1. Start the backend and frontend following the repository README. Hibernate's
   existing `ddl-auto=update` setting creates the two new surplus tables.
2. Log in as staff or admin and open **Surplus Marketplace** (`/surplus`).
3. Choose a menu item, enter ingredients, discount price, available portions,
   pickup location, pickup window, and booking deadline. Publish the offer.
4. Log in as a customer. The marketplace shows available offers with their
   published ingredients, allergen tags, prices, remaining portions, and times.
5. Choose a quantity and pickup time. Reserve; the confirmation shows the
   reservation number and total payable at pickup. Find it under **My reservations**.
6. Cancel before the chosen pickup time to return portions to the offer, or visit
   the pickup location. Staff locate the reservation number under **Customer
   reservations** and mark it collected during the pickup window.

## API

All routes require authentication. Drivers cannot access the marketplace.

| Method and path | Role | Result |
| --- | --- | --- |
| `GET /api/surplus/offers` | Customer, staff, admin | Available offers, earliest deadline first |
| `GET /api/surplus/offers/manage` | Staff, admin | All offers, including sold-out/expired offers |
| `POST /api/surplus/offers` | Staff, admin | Publish an offer; 201 |
| `POST /api/surplus/offers/{id}/reservations` | Customer | Reserve portions; 201 |
| `GET /api/surplus/reservations` | Customer, staff, admin | Own reservations for customers, all for staff/admins |
| `POST /api/surplus/reservations/{id}/cancel` | Customer | Cancel own reservation before pickup |
| `POST /api/surplus/reservations/{id}/collect` | Staff, admin | Mark reserved portions collected |

Publish body (replace example times with future instants):

```json
{
  "foodId": 1,
  "ingredients": "Rice, vegetables, soy sauce",
  "discountedPrice": 3.25,
  "portions": 5,
  "pickupLocation": "Restaurant front desk",
  "pickupStart": "2030-06-01T18:00:00Z",
  "pickupEnd": "2030-06-01T20:00:00Z",
  "expiresAt": "2030-06-01T19:00:00Z"
}
```

Reserve body:

```json
{ "quantity": 2, "pickupAt": "2030-06-01T19:30:00Z" }
```

Quantities must be JSON integers from 1 to 10000. Invalid input returns 400;
unknown resources return 404; expired, insufficient-stock, late-cancellation,
and completed-reservation conflicts return 409. Unauthorized roles or attempts
to cancel another customer's reservation return 403.

## Tests

```bash
# Backend service rules, real HTTP security filters, and H2 concurrent transactions
cd food-seer-backend
mvn -Dtest=SurplusServiceTest,SurplusControllerTest,SurplusPersistenceTest test

# Real Chrome + API + database workflow; start the React frontend on port 3000 first
# Configure an isolated test database as for the existing browser tests.
mvn -Dtest=SurplusMarketplaceTest test

# Frontend marketplace interactions and authenticated API client
cd ../food-seer-frontend
CI=true npm test -- --watchAll=false --runInBand SurplusMarketplace surplusApi
```

The concurrency test starts simultaneous transactions against the last remaining
portion and requires exactly one successful reservation. Other tests cover
cancellation persistence, allergy conflict rollback, price and time validation,
role/ownership boundaries, malformed requests, and UI error recovery. The browser
test publishes as staff, reserves as a customer, collects as staff, and checks
the persisted reservation and unchanged regular inventory.

### Verification recorded November 16, 2026

| Check | Result |
| --- | --- |
| Surplus service, controller, persistence, and real-browser tests | 56 passed |
| Full Selenium regression suite | 44 passed |
| Frontend Jest suite | 41 passed |
| Complete default Maven suite | 410 run; 409 passed, 1 intermittent inherited chatbot failure |
| Isolated rerun of `ChatServiceImplTest` | All 5 passed |
| Frontend ESLint, Prettier check, and production build | Passed |
| Backend SpotBugs, PMD, and CPD commands | Completed successfully with advisory findings |

The inherited `ChatServiceImplTest.test_ErrorHandling_NoCrash` calls live local
Ollama with "Cause error" but does not trigger the mocked repository failure,
because that message is not a recommendation request. Its assertion depends on
whether the model's actual response contains the word "error". Neither that test
nor the chatbot implementation changed in this feature branch.

The separately invoked M0 allergy evaluation also reproduces its already
documented failure (0.890 recall, below its 0.95 target). It is not part of the
default Maven suite, and surplus reservations do not change its behavior.

Backend verification used the Java 21 runtime and H2, with Chrome for browser
tests. MySQL-specific behavior has not been exercised. No dependency versions or
lockfiles were changed.

Static analysis remains advisory under the repository's existing configuration.
Reports include mutable JPA-entity exposure warnings, style findings, and two
pre-existing duplication groups in the food/order DTOs and entities. Those
commands succeeding does not mean the reports contain zero findings.

## Deliberate limits

This version supports one restaurant and pickup only. It does not process online
payments, deliver surplus meals, add reservations to calorie logs, or include
them in delivery-order incident tracing. Staff should verify the menu item's
allergen tags before publishing; ingredient text is displayed, not automatically
classified. Those integrations can be separate follow-up milestones.
