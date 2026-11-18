package FoodSeer.mealplan;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import FoodSeer.constant.MealType;
import FoodSeer.entity.Food;

/**
 * Reads a plan request such as "skip breakfast, pizza on Friday, no salads,
 * under $150" without an LLM. Always runs, so a request does something even
 * when the AI is off or unavailable.
 */
public final class KeywordPlanReader {

    static final Map<String, DayOfWeek> DAYS = Map.ofEntries(Map.entry("monday", DayOfWeek.MONDAY),
            Map.entry("mon", DayOfWeek.MONDAY), Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("tue", DayOfWeek.TUESDAY), Map.entry("tues", DayOfWeek.TUESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY), Map.entry("wed", DayOfWeek.WEDNESDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY), Map.entry("thu", DayOfWeek.THURSDAY),
            Map.entry("thurs", DayOfWeek.THURSDAY), Map.entry("friday", DayOfWeek.FRIDAY),
            Map.entry("fri", DayOfWeek.FRIDAY), Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("sat", DayOfWeek.SATURDAY), Map.entry("sunday", DayOfWeek.SUNDAY),
            Map.entry("sun", DayOfWeek.SUNDAY));

    private static final Pattern MEAL = Pattern.compile("\\b(breakfast|lunch|dinner)e?s?\\b");
    private static final Pattern SKIP = Pattern.compile(
            "\\b(?:skip|skipping|no|without|don'?t (?:need|want|plan)|leave out|drop)\\s+(?:the\\s+|any\\s+)?(breakfast|lunch|dinner)e?s?\\b");
    private static final Pattern ONLY = Pattern.compile("\\bonly\\s+(?:(?:eat|want|have|need|do|plan)\\s+)?((?:breakfast|lunch|dinner)(?:\\s*(?:and|,|&)\\s*(?:breakfast|lunch|dinner))*)");
    private static final Pattern AVOID = Pattern.compile(
            "\\b(?:no|without|avoid|not|hate|skip|nothing with|don'?t want)\\s+(?:any\\s+|more\\s+)?([a-z][a-z ]{1,25}?)(?=$|[,.;!]|\\s(?:and|or|but|please|on|for|this|in)\\b)");
    private static final Pattern FREE = Pattern.compile("\\b([a-z]+)-free\\b");
    private static final Pattern PREFER = Pattern.compile(
            "\\b(?:more|lots of|love|prefer|extra|plenty of|favor)\\s+([a-z][a-z ]{1,25}?)(?=$|[,.;!]|\\s(?:and|or|but|please|on|for|this|in)\\b)");
    private static final Pattern BUDGET = Pattern.compile(
            "(?:under|below|less than|at most|max(?:imum)?|budget(?: of| is)?|no more than|spend)\\s*\\$?\\s*(\\d{2,4})");
    private static final Pattern DOLLARS = Pattern.compile("\\$\\s*(\\d{2,4})");
    private static final Pattern TIMES = Pattern.compile("(?:at most|max(?:imum)?|no more than)\\s+(\\d|once|twice)\\s*(?:times?)?\\s*(?:each|per|a)?");

    private KeywordPlanReader() {
    }

    /**
     * @param request
     *            free text, may be null
     * @param menu
     *            dishes, so pins can be recognized
     * @return the rules it asks for
     */
    public static PlanRules read(final String request, final List<Food> menu) {
        if (request == null || request.isBlank()) {
            return PlanRules.NONE;
        }
        final String t = " " + request.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("\\s+", " ") + " ";

        final Set<String> skip = new LinkedHashSet<>();
        final Matcher sk = SKIP.matcher(t);
        while (sk.find()) {
            skip.add(sk.group(1).toUpperCase(Locale.ROOT));
        }
        final Matcher only = ONLY.matcher(t);
        if (only.find()) {
            final Set<String> keep = new LinkedHashSet<>();
            final Matcher m = MEAL.matcher(only.group(1));
            while (m.find()) {
                keep.add(m.group(1).toUpperCase(Locale.ROOT));
            }
            for (final String meal : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)) {
                if (!keep.contains(meal)) {
                    skip.add(meal);
                }
            }
        }

        final Set<String> avoid = new LinkedHashSet<>();
        final Matcher free = FREE.matcher(t);
        while (free.find()) {
            avoid.add(free.group(1));
        }
        final Matcher av = AVOID.matcher(t.replaceAll("\\b[a-z]+-free\\b", " "));
        while (av.find()) {
            final String what = trimWord(av.group(1));
            if (!what.isEmpty() && !isMealOrFiller(what)) {
                avoid.add(what);
            }
        }

        final Set<String> prefer = new LinkedHashSet<>();
        final Matcher pr = PREFER.matcher(t);
        while (pr.find()) {
            final String what = trimWord(pr.group(1));
            if (!what.isEmpty() && !isMealOrFiller(what) && !what.startsWith("than")) {
                prefer.add(what);
            }
        }

        int maxRepeats = PlanRules.DEFAULT_MAX_REPEATS;
        if (t.matches(".*\\b(no repeats?|don'?t repeat|never repeat|no dish twice|different (?:every|each) (?:day|meal)|all different)\\b.*")) {
            maxRepeats = 1;
        } else {
            final Matcher times = TIMES.matcher(t);
            if (times.find()) {
                final String n = times.group(1);
                maxRepeats = "once".equals(n) ? 1 : "twice".equals(n) ? 2 : Integer.parseInt(n);
            }
        }

        Integer budget = null;
        final Matcher b = BUDGET.matcher(t);
        if (b.find()) {
            budget = Integer.parseInt(b.group(1));
        } else {
            final Matcher d = DOLLARS.matcher(t);
            if (d.find()) {
                budget = Integer.parseInt(d.group(1));
            }
        }

        final Set<String> goals = goals(t);
        // Words that are goals, not dishes, must not also become avoids or favorites.
        avoid.removeIf(KeywordPlanReader::isGoalWord);
        prefer.removeIf(KeywordPlanReader::isGoalWord);
        final List<PlanRules.Pin> pins = pins(t, menu, avoid);
        return new PlanRules(skip, new ArrayList<>(avoid), new ArrayList<>(prefer), pins, maxRepeats, budget, goals);
    }

    /** Phrases for each diet goal. */
    static final java.util.Map<String, Pattern> GOAL_PHRASES = java.util.Map.of(
            PlanRules.HIGH_PROTEIN, Pattern.compile("\\b(high[- ]protein|more protein|lots of protein|protein|bulk(ing)?|"
                    + "build(ing)? muscle|gains)\\b"),
            PlanRules.VEGAN, Pattern.compile("\\b(vegan|plant[- ]based)\\b"),
            PlanRules.VEGETARIAN, Pattern.compile("\\b(vegetarian|meatless|meat[- ]free|no meat|veggie only)\\b"),
            PlanRules.LOW_CARB, Pattern.compile("\\b(low[- ]carbs?|keto|no carbs|(fewer|less|cut(ting)?( down on)?) carbs)\\b"),
            PlanRules.NO_DESSERT, Pattern.compile("\\b((no|without|skip|zero) (desserts?|sweets|sugar))\\b"),
            PlanRules.FEWER_DESSERTS, Pattern.compile("\\b((fewer|less|limit|not too many|cut down on) (desserts?|sweets|sugar)"
                    + "|healthy|healthier|eat(ing)? clean|clean eating)\\b"),
            PlanRules.LIGHTER, Pattern.compile("\\b(lighter|light week|lose weight|losing weight|eat less|smaller portions|"
                    + "cutting(?! (down on )?carbs))\\b"));

    static Set<String> goals(final String text) {
        final Set<String> out = new LinkedHashSet<>();
        GOAL_PHRASES.forEach((goal, pattern) -> {
            if (pattern.matcher(text).find()) {
                out.add(goal);
            }
        });
        return out;
    }

    /** True for words that name a goal rather than a dish ("protein", "sweets"). */
    static boolean isGoalWord(final String word) {
        return word.matches("(high |more )?protein|desserts?|sweets|sugar|carbs?|healthy|meat|calories|"
                + "plant[- ]based|vegan|vegetarian|keto");
    }

    /** A dish word near a weekday ("pizza on friday", "friday: sushi"). */
    private static List<PlanRules.Pin> pins(final String t, final List<Food> menu, final Set<String> avoid) {
        final Set<String> dishWords = new LinkedHashSet<>();
        final Set<String> dishNames = new LinkedHashSet<>();
        for (final Food f : menu == null ? List.<Food>of() : menu) {
            dishNames.add(f.getFoodName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim());
            for (final String w : f.getFoodName().toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                if (w.length() >= 3) {
                    dishWords.add(w);
                }
            }
        }
        final List<PlanRules.Pin> pins = new ArrayList<>();
        // Each clause ("pizza on friday", "steak for saturday dinner") is read on its own,
        // so a dish named in one clause is never pinned to a day named in another.
        for (final String clause : t.split("[.,;!?\\n]|\\band\\b|\\bbut\\b")) {
            final String[] tokens = clause.trim().split("[^a-z']+");
            for (int i = 0; i < tokens.length; i++) {
                final String tok = tokens[i].endsWith("days") ? tokens[i].substring(0, tokens[i].length() - 1)
                        : tokens[i];
                final DayOfWeek day = DAYS.get(tok);
                if (day == null) {
                    continue;
                }
                String dish = null;
                int dishDistance = Integer.MAX_VALUE;
                String meal = null;
                for (int j = 0; j < tokens.length; j++) {
                    final String w = singular(tokens[j]);
                    final boolean negated = j > 0 && List.of("no", "not", "without", "avoid").contains(tokens[j - 1]);
                    if (dishWords.contains(w) && !avoid.contains(w) && !avoid.contains(tokens[j]) && !negated
                            && Math.abs(j - i) < dishDistance) {
                        dish = w;
                        dishDistance = Math.abs(j - i);
                    }
                    if (meal == null && MEAL.matcher(tokens[j]).matches() && Math.abs(j - i) <= 2) {
                        meal = tokens[j].replaceAll("e?s$", "").toUpperCase(Locale.ROOT);
                    }
                }
                final String pinnedDish = dish == null ? null : fullName(tokens, dish, dishNames);
                if (pinnedDish != null && pins.stream().noneMatch(p -> p.day() == day)) {
                    pins.add(new PlanRules.Pin(day, pinnedDish, meal));
                }
            }
        }
        return pins;
    }

    /**
     * @return the full dish name the customer typed around {@code word} ("tofu
     *         bowl"), or the word itself
     */
    private static String fullName(final String[] tokens, final String word, final Set<String> names) {
        String best = word;
        for (int start = 0; start < tokens.length; start++) {
            for (int len = 2; len <= 3 && start + len <= tokens.length; len++) {
                final StringBuilder phrase = new StringBuilder();
                for (int k = start; k < start + len; k++) {
                    phrase.append(k > start ? " " : "").append(singular(tokens[k]));
                }
                final String p = phrase.toString();
                if (names.contains(p) && (" " + p + " ").contains(" " + word + " ") && p.length() > best.length()) {
                    best = p;
                }
            }
        }
        return best;
    }

    /** @return the word without a plural ending ("burritos" to "burrito") */
    public static String singular(final String w) {
        if (w.endsWith("es") && w.length() > 4 && (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("oes"))) {
            return w.substring(0, w.length() - 2);
        }
        return w.endsWith("s") && !w.endsWith("ss") && w.length() > 3 ? w.substring(0, w.length() - 1) : w;
    }

    private static String trimWord(final String phrase) {
        return phrase.trim().replaceAll("^(the|any|some) ", "").replaceAll(" (please|thanks)$", "").trim();
    }

    private static boolean isMealOrFiller(final String what) {
        final String first = what.split(" ")[0];
        return MEAL.matcher(first).matches() || List.of("repeat", "repeats", "dish", "more", "than", "too", "same",
                "twice", "one", "budget", "money", "more").contains(first) || DAYS.containsKey(first);
    }
}
