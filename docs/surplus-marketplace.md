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
