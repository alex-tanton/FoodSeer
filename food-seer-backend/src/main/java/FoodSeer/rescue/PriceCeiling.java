package FoodSeer.rescue;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Maps a saved cost preference to a price ceiling. The ranges are the ones the
 * recommendations page already applies: budget up to $10, moderate up to $20,
 * premium up to $35. Anything else, including "no-limit", has no ceiling.
 */
public final class PriceCeiling {

    private PriceCeiling() {
        // static helpers only
    }

    /**
     * @param costPreference the saved preference, may be null
     * @return the highest acceptable price, or null when there is no ceiling
     */
    public static BigDecimal of(final String costPreference) {
        if (costPreference == null) {
            return null;
        }
        return switch (costPreference.trim().toLowerCase(Locale.ROOT)) {
            case "budget" -> BigDecimal.valueOf(10);
            case "moderate" -> BigDecimal.valueOf(20);
            case "premium" -> BigDecimal.valueOf(35);
            default -> null;
        };
    }

    /**
     * @param costPreference the saved preference, may be null
     * @param price the price to check
     * @return true when the price is within the preference
     */
    public static boolean allows(final String costPreference, final BigDecimal price) {
        final BigDecimal ceiling = of(costPreference);
        return ceiling == null || (price != null && price.compareTo(ceiling) <= 0);
    }
}
