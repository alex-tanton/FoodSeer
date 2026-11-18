package FoodSeer.rescue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The parts of a surplus offer the rescue matcher needs. A plain value, so the
 * matcher never touches JPA entities or the database.
 *
 * @param offerId the surplus offer id
 * @param foodId the menu item the offer was published from
 * @param foodName the dish name shown to customers
 * @param allergens the allergen tags snapshotted on the offer; never null
 * @param calories calories per portion, or null when the menu item has none recorded
 * @param originalPrice the menu price
 * @param discountedPrice the surplus price
 * @param remainingPortions portions still available
 * @param expiresAt the booking deadline
 */
public record RescueListing(Long offerId, Long foodId, String foodName, List<String> allergens, Integer calories,
        BigDecimal originalPrice, BigDecimal discountedPrice, int remainingPortions, Instant expiresAt) {

    public RescueListing {
        allergens = allergens == null ? List.of() : List.copyOf(allergens);
    }
}
