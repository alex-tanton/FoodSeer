# Group Dining Harmonizer

Project 3 milestone. Enables collaborative multi-member group ordering sessions with automated multi-profile dietary consensus, real-time table cross-contamination auditing, and itemized split billing.

## Problem

When coworkers, friends, or families order food together, coordinating diverse dietary needs is error-prone and stressful. A single group order often contains members with celiac disease, peanut allergies, vegetarian diets, and differing calorie or price budgets. Ordering separately increases delivery fees and leads to cross-contamination hazards when allergen-heavy dishes are packed alongside sensitive meals.

## Key Capabilities

1. **Dining Rooms**: A host creates a room session with a unique 6-character room code (`DEMO26`); participants join with their user profiles or enter guest preferences.
2. **Consensus Safety Filter**: Automatically aggregates all seated members' dietary restrictions, using `AllergenMatcher` to partition the menu into:
   - *Universally Safe*: 100% allergen-free for every participant at the table.
   - *Conditionally Safe*: Annotated with specific per-member conflict badges (e.g., "Safe for Alex & Maya, ⚠️ Unsafe for Leo [PEANUT]").
3. **Table Cross-Contamination Guard**: Evaluates the collective table cart in real time. If a member adds an item whose allergens conflict with another seated member's profile, a prominent warning banner is displayed and attached to the kitchen order notes.
4. **Itemized Split-Billing**: Calculates per-person food subtotals, proportional tax and fee shares, and total owed per member without floating-point penny drift.
5. **Consolidated Checkout**: The host finalizes the session, generating a single FoodSeer `Order` for the kitchen with composite dietary instructions and participant tags.

## Try It (Zero Setup)

```bash
scripts/group-demo.sh
```

Starts the backend on an in-memory H2 database with the `demo` profile (no MySQL required) and the React frontend on `http://localhost:3000`. Every demo account's password is `rescue123`.

1. Log in as `host_alex` &rarr; **Group Dining** &rarr; open room **`DEMO26`** (`/group-dining/DEMO26`).
2. Seated members: `host_alex` (Host), `leo` (Peanut allergic), and `maya` (Gluten allergic).
3. Under **Harmonized Menu**:
   - Filter by **Universally Safe for All**: Shows dishes like `GARDEN SALAD` and `BANANA`.
   - Filter by **Needs Caution**: Shows dishes like `COOKIES` with tag `⚠️ Unsafe for Leo: Contains PEANUT`.
4. Switch to **Shared Cart & Split Bill**:
   - View the active **Table Cross-Contamination Alert**: `COOKIES (added by Alex) contains PEANUT, which conflicts with Leo's allergy. Request separate packaging.`
   - View itemized split bill breakdown per member.
5. Click **Submit Group Order** &rarr; Generates Order with attached composite kitchen notes!

## Architecture & Code Heritage

Fork point: upstream commit `9bde2b8` (Oct 21, 2026). Across the delta, zero existing business logic was broken or rewritten.

| Inherited Component | How It Is Reused |
| :--- | :--- |
| `FoodSeer.service.AllergenMatcher` | The authoritative allergen normalization, expansion, and matching engine. No custom string matching was introduced. |
| `FoodSeer.entity.User` | Individual dietary restrictions and cost preferences loaded automatically upon room entry. |
| `FoodSeer.entity.Food` | Menu dishes, pricing, calories, and allergen tags. |
| `FoodSeer.entity.Order` & `OrderService` | Consolidated group carts convert into standard FoodSeer orders for kitchen fulfillment and driver delivery. |
| `EvalStats` | Non-parametric Cliff's delta and median calculations used for the M0 evaluation. |

New code lives in:
- Backend: `FoodSeer.group.*` (`GroupSession`, `GroupMember`, `GroupCartItem`, `GroupHarmonizerService`, `GroupDiningService`, `GroupDiningController`, `GroupDtos`)
- Frontend: `src/services/groupDiningApi.js`, `src/pages/group/GroupDiningHub.js`, `src/pages/group/GroupRoom.js`
- Eval: `FoodSeer.eval.M0GroupDiningEval`
- Demo: `scripts/group-demo.sh`

## Verification Commands

```powershell
# Run backend unit and integration tests
mvn -Dtest="GroupHarmonizerServiceTest,GroupDiningServiceTest,GroupDiningControllerTest" test

# Run M0 Group Dining Acceptance Evaluation
mvn -Dtest=M0GroupDiningEval test

# Run frontend tests
npm test -- --watchAll=false src/pages/group/GroupDining.test.js src/services/groupDiningApi.test.js
```
