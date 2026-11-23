# Driver Batch Dispatcher

Project 3 milestone. Empowers delivery drivers with multi-order batch suggestion, thermal freshness routing heuristics, and atomic route execution with pooled efficiency bonuses.

## Problem

In FoodSeer, delivery drivers historically claimed unassigned orders individually in a flat FIFO list. This single-order delivery model introduces three fundamental inefficiencies:
1. **Excess Vehicle Travel & Environmental Impact**: Repeated round trips between distinct restaurants and student dorms consume excessive fuel and inflate transit times.
2. **Food Temperature Spoilage**: Food categories have distinct thermal decay envelopes: hot soups and pizzas lose temperature past 20 minutes, while chilled ice creams and salads melt or wilt past 15 minutes. Unoptimized routing risks delivering spoiled or degraded meals.
3. **Sub-optimal Driver Compensation**: Drivers lack consolidated routing opportunities that pool delivery fees and reward multi-order dispatch efficiency.

## Key Capabilities

1. **Intelligent Batch Suggestion Engine (`DriverBatchService`)**:
   - Automatically clusters open `Placed` orders by geographic proximity across restaurant hubs (e.g. Hillsborough St, Mission Valley) and delivery dropoff zones (Talley Union, Centennial Campus, North Campus).
   - Groups orders into optimal 2–4 order batches with combined base payouts and dynamic batch efficiency bonuses ($2.50 per additional order).
2. **Thermal Freshness & Route Optimizer (`BatchRouteOptimizer`)**:
   - Constrained Traveling Salesperson Problem (TSP) solver enforcing strict precedence constraints: **every order's pickup must precede its dropoff**.
   - Dual-objective cost function: minimizes travel distance (Haversine metric) and penalizes food temperature degradation based on dish category (`HOT`, `COLD`, `AMBIENT`).
   - Generates route metrics: total distance in miles, estimated duration in minutes, and thermal degradation risk score.
3. **Atomic Batch Claiming (`POST /api/driver/batches/{batchId}/claim`)**:
   - Single atomic transaction that claims all constituent orders for the authenticated driver, preventing race conditions or partial splits.
4. **Turn-by-Turn Route Execution Runner (`POST /api/driver/batches/{batchId}/step`)**:
   - Guides the driver step-by-step through the optimized sequence: `CLAIMED` &rarr; `PICKING_UP` &rarr; `IN_TRANSIT` &rarr; `DELIVERING` &rarr; `COMPLETED`.
   - Synchronizes order fulfillment and automatically credits individual delivery costs plus the batch completion bonus to driver earnings.
5. **Interactive Driver Dashboard UI (`DriverBatchPanel`)**:
   - Mode switcher on the driver dashboard allowing seamless toggle between "⚡ Batched Routes (Optimized)" and "📦 Individual Orders".
   - Real-time active route timeline with thermal badges, stop sequence cards, and step confirmation buttons.

## Try It (Zero Setup)

```bash
scripts/driver-demo.sh
```

Starts the backend on an in-memory H2 database with the `demo` profile (no MySQL required) and the React frontend on `http://localhost:3000`.

1. Navigate to `http://localhost:3000/login` &rarr; Log in as `demo_driver` (Password: `password`).
2. You will be redirected to the **Driver Dashboard**.
3. Under **⚡ Batched Routes (Optimized)**:
   - Notice the suggested batches grouped from pre-seeded hot and cold campus orders (Hot Ramen, Artisan Gelato, Margherita Pizza, Acai Bowls).
   - Inspect the guaranteed total payout (e.g. `$10.00` with `+$2.50 Batch Bonus`), total distance (`1.85 mi`), and Freshness Rating (`🌟 Optimal Freshness`).
4. Click **⚡ Claim Batched Route** &rarr; The route is assigned atomically to `demo_driver`.
5. Progress through the turn-by-turn route by clicking **Confirm Pickup** and **Confirm Delivery**.
6. When the final stop is completed, the route marks complete and the batch efficiency bonus is added directly to Today's Earnings!

## Architecture & Code Heritage

Fork point: upstream commit `9bde2b8` (Oct 21, 2026). Across the delta, zero existing business logic was broken or rewritten.

| Inherited Component | How It Is Reused |
| :--- | :--- |
| `FoodSeer.entity.Order` & `OrderRepository` | Clustered into batches; updated atomically to `Claimed`, `Picked Up`, and `Delivered`. |
| `FoodSeer.entity.DriverStats` & `DriverStatsService` | Driver earnings, active orders, and deliveries updated on batch completion. |
| `FoodSeer.entity.Food` | Dish names analyzed to assign thermal envelopes (`HOT`, `COLD`, `AMBIENT`). |
| `FoodSeer.entity.User` | Driver authentication and role verification (`ROLE_DRIVER`). |

New code lives in:
- Backend: `FoodSeer.batch.*` (`DeliveryBatch`, `BatchStop`, `DeliveryBatchRepository`, `BatchStopRepository`, `BatchRouteOptimizer`, `BatchDtos`, `DriverBatchService`, `DriverBatchServiceImpl`, `DriverBatchController`, `DriverBatchDemoSeeder`)
- Frontend: `src/services/driverBatchApi.js`, `src/pages/driver/DriverBatchPanel.js`, `src/pages/driver/DriverBatchPanel.css`
- Eval: `FoodSeer.eval.M0DriverBatchEval`
- Demo: `scripts/driver-demo.sh`

## Verification Commands

```powershell
# Run backend batch tests and M0 evaluation
mvn -Dtest="BatchRouteOptimizerTest,DriverBatchServiceTest,DriverBatchControllerTest,M0DriverBatchEval" test

# Run frontend driver batch tests
npm --prefix food-seer-frontend test -- --watchAll=false src/services/driverBatchApi.test.js src/pages/driver/DriverBatchPanel.test.js
```
