package FoodSeer.rescue;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the raw numbers behind a match into one short line a customer can read,
 * for example "you ordered this twice · 40% off · 25 min left".
 */
public final class RescueReasons {

    /** Separator between reason pieces. */
    static final String SEPARATOR = " · ";

    private RescueReasons() {
        // static helpers only
    }

    /**
     * @param pastOrdersOfDish how many past orders contained the dish
     * @param discountPercent whole percent off the menu price
     * @param minutesLeft minutes before the booking deadline
     * @return the reason line
     */
    public static String describe(final int pastOrdersOfDish, final int discountPercent, final long minutesLeft) {
        final List<String> pieces = new ArrayList<>();
        if (pastOrdersOfDish > 0) {
            pieces.add("you ordered this " + times(pastOrdersOfDish));
        }
        if (discountPercent > 0) {
            pieces.add(discountPercent + "% off");
        }
        pieces.add(timeLeft(minutesLeft));
        return String.join(SEPARATOR, pieces);
    }

    static String times(final int count) {
        return switch (count) {
            case 1 -> "once";
            case 2 -> "twice";
            default -> count + " times";
        };
    }

    static String timeLeft(final long minutesLeft) {
        if (minutesLeft < 60) {
            return Math.max(0, minutesLeft) + " min left";
        }
        final long hours = minutesLeft / 60;
        final long minutes = minutesLeft % 60;
        return minutes == 0 ? hours + " h left" : hours + " h " + minutes + " min left";
    }
}
