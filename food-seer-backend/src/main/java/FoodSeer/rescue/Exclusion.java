package FoodSeer.rescue;

/**
 * Why the matcher left a customer out. When several filters apply, the first
 * one in this order is reported.
 */
public enum Exclusion {
    /** Too little time before the booking deadline, or no portions left. */
    TIME("Not enough time before the booking deadline, or sold out"),
    /** A listed allergen affects the customer's saved restrictions. */
    ALLERGEN("Conflicts with saved dietary restrictions"),
    /** A saved restriction is unrecognized and the listing declares allergens. */
    UNCERTAIN_RESTRICTION("Saved restriction is not recognized and the dish declares allergens"),
    /** Unknown or too many calories for what the customer has left today. */
    CALORIES("Does not fit the remaining calories today"),
    /** Above the customer's cost-preference ceiling. */
    PRICE("Above the customer's price preference"),
    /** Already alerted about this listing. */
    ALREADY_ALERTED("Already alerted about this listing"),
    /** Already received the daily maximum of rescue alerts. */
    DAILY_CAP("Reached today's rescue alert limit");

    private final String description;

    Exclusion(final String description) {
        this.description = description;
    }

    /** @return a short sentence staff can read */
    public String description() {
        return description;
    }
}
