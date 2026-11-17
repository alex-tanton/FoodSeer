package FoodSeer.rescue;

/**
 * A customer who passed every hard filter, with the score that ranks them.
 *
 * @param username the customer
 * @param parts the score and its parts
 * @param pastOrdersOfDish raw count behind the affinity part
 * @param discountPercent whole-number percent off behind the discount part
 * @param minutesLeft whole minutes before the booking deadline
 * @param reason one line explaining the match, for example
 *        "you ordered this twice · 40% off · 25 min left"
 */
public record RankedCandidate(String username, ScoreParts parts, int pastOrdersOfDish, int discountPercent,
        long minutesLeft, String reason) {
}
