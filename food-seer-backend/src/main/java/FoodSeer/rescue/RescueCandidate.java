package FoodSeer.rescue;

import java.time.Instant;

/**
 * One customer the matcher may alert, with everything it needs already looked
 * up. The service fills this in; the matcher only reads it.
 *
 * @param username the customer
 * @param dietaryRestrictions saved restrictions text, may be null
 * @param costPreference saved cost preference ("budget", "moderate", "premium",
 *        "no-limit"), may be null
 * @param remainingCalories calories left today, or null when the customer does not
 *        track calories
 * @param pastOrdersOfDish how many of the customer's past orders contained this dish
 * @param lastOrderAt when the customer last ordered anything, or null if never
 * @param alertsToday rescue alerts the customer already received today
 * @param alreadyAlerted true when the customer was already alerted about this listing
 */
public record RescueCandidate(String username, String dietaryRestrictions, String costPreference,
        Integer remainingCalories, int pastOrdersOfDish, Instant lastOrderAt, int alertsToday,
        boolean alreadyAlerted) {
}
