package FoodSeer.rescue;

/**
 * The pieces of a match score, each in [0, 1] before weighting, kept so the UI
 * can say why a customer was picked.
 *
 * @param affinity past orders of the dish, scaled by the policy's cap
 * @param activity how recently the customer ordered anything
 * @param discount share off the menu price
 * @param urgency how close the booking deadline is
 * @param total the weighted sum used for ranking
 */
public record ScoreParts(double affinity, double activity, double discount, double urgency, double total) {
}
