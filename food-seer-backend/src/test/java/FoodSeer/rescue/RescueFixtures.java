package FoodSeer.rescue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shared builders for rescue matcher tests. */
final class RescueFixtures {

    static final Instant NOW = Instant.parse("2026-11-17T18:00:00Z");

    /** The allergen vocabulary DataInitializer seeds. */
    static final List<String> KNOWN = List.of("MILK", "LACTOSE", "EGGS", "FISH", "SHELLFISH", "TREE-NUTS",
            "PEANUTS", "WHEAT", "GLUTEN", "SOY", "SESAME", "CORN", "SULFITES", "MUSTARD", "MEAT", "BEEF", "PORK",
            "POULTRY", "GELATIN", "CAFFEINE", "DAIRY");

    private RescueFixtures() {
    }

    static RescueListing listing(final List<String> allergens) {
        return listing(allergens, 400, "12.00", "6.00", 5, NOW.plusSeconds(60 * 60));
    }

    static RescueListing listing(final List<String> allergens, final Integer calories, final String original,
            final String discounted, final int portions, final Instant expiresAt) {
        return new RescueListing(1L, 10L, "PASTA", allergens, calories, new BigDecimal(original),
                new BigDecimal(discounted), portions, expiresAt);
    }

    static RescueCandidate customer(final String username) {
        return new RescueCandidate(username, null, null, null, 0, null, 0, false);
    }

    static RescueCandidate restricted(final String username, final String restrictions) {
        return new RescueCandidate(username, restrictions, null, null, 0, null, 0, false);
    }

    static RescueCandidate withHistory(final String username, final int pastOrders, final Instant lastOrderAt) {
        return new RescueCandidate(username, null, null, null, pastOrders, lastOrderAt, 0, false);
    }

    static MatchResult run(final RescueListing listing, final RescueCandidate... candidates) {
        return RescueMatcher.match(listing, List.of(candidates), KNOWN, NOW, RescuePolicy.DEFAULT);
    }
}
