package FoodSeer.mealplan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import FoodSeer.entity.Food;
import FoodSeer.service.AllergenMatcher;

/**
 * A customer's saved restrictions, resolved so the planner can check dishes
 * against them. Built on {@link AllergenMatcher} (the allergy trace-back
 * matcher, which handles synonyms and groups), plus what it does not know:
 * ingredient names ("tahini" means sesame), diets ("vegetarian") and word
 * fallback ("milk protein" means milk). A restriction that still cannot be
 * mapped is kept, blocks any dish whose name contains it, and is reported.
 */
public final class Restrictions {

    /** Allergens customers commonly name, plus everything the seed menu tags. */
    static final List<String> KNOWN = List.of("PEANUT", "TREE NUT", "NUT", "MILK", "EGG", "FISH", "SHELLFISH",
            "CRUSTACEAN", "MOLLUSK", "WHEAT", "GLUTEN", "SOY", "SESAME", "MUSTARD", "SULFITE", "CORN", "MEAT", "BEEF",
            "PORK", "POULTRY", "GELATIN", "CAFFEINE", "CELERY", "LUPIN");

    /** Ingredient and diet names that imply allergen tags. */
    static final Map<String, List<String>> IMPLIES = Map.ofEntries(Map.entry("TAHINI", List.of("SESAME")),
            Map.entry("HUMMUS", List.of("SESAME")), Map.entry("MISO", List.of("SOY")),
            Map.entry("TOFU", List.of("SOY")), Map.entry("EDAMAME", List.of("SOY")),
            Map.entry("BUTTER", List.of("MILK")), Map.entry("CHEESE", List.of("MILK")),
            Map.entry("CREAM", List.of("MILK")), Map.entry("WHEY", List.of("MILK")),
            Map.entry("MAYONNAISE", List.of("EGG")), Map.entry("ANCHOVY", List.of("FISH")),
            Map.entry("BARLEY", List.of("GLUTEN")), Map.entry("RYE", List.of("GLUTEN")),
            Map.entry("CHICKEN", List.of("POULTRY")), Map.entry("TURKEY", List.of("POULTRY")),
            Map.entry("BACON", List.of("PORK")), Map.entry("HAM", List.of("PORK")),
            Map.entry("COFFEE", List.of("CAFFEINE")),
            Map.entry("VEGETARIAN", List.of("MEAT", "FISH", "SHELLFISH")),
            Map.entry("VEGAN", List.of("MEAT", "FISH", "SHELLFISH", "MILK", "EGG", "GELATIN")));

    /** No restrictions. */
    public static final Restrictions NONE = new Restrictions(List.of(), List.of());

    private final List<String> terms;
    private final List<String> unrecognized;

    private Restrictions(final List<String> terms, final List<String> unrecognized) {
        this.terms = terms;
        this.unrecognized = unrecognized;
    }

    /**
     * @param text
     *            restrictions as saved or typed, such as "PEANUTS, milk protein"
     * @return the resolved restrictions
     */
    public static Restrictions parse(final String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        final Set<String> terms = new LinkedHashSet<>();
        final Set<String> unknown = new LinkedHashSet<>();
        for (final String piece : text.split("[,;\\n]")) {
            for (final String term : AllergenMatcher.parseTerms(piece)) {
                final List<String> resolved = resolve(term);
                if (resolved.isEmpty()) {
                    terms.add(term);
                    unknown.add(piece.trim());
                } else {
                    terms.addAll(resolved);
                }
            }
        }
        return new Restrictions(List.copyOf(terms), List.copyOf(unknown));
    }

    private static List<String> resolve(final String term) {
        if (AllergenMatcher.foodDeclaresAllergen(KNOWN, term)) {
            return List.of(term);
        }
        if (IMPLIES.containsKey(term)) {
            return IMPLIES.get(term);
        }
        // Allergen words win ("peanut butter" is a peanut restriction, not dairy).
        final List<String> known = new ArrayList<>();
        final List<String> implied = new ArrayList<>();
        for (final String word : term.split(" ")) {
            final String w = AllergenMatcher.normalizeTerm(word);
            if (w.isEmpty()) {
                continue;
            }
            if (AllergenMatcher.foodDeclaresAllergen(KNOWN, w)) {
                known.add(w);
            } else if (IMPLIES.containsKey(w)) {
                implied.addAll(IMPLIES.get(w));
            }
        }
        return known.isEmpty() ? implied : known;
    }

    /**
     * @param other
     *            more restrictions, such as "no salads" from a plan request
     * @return both combined
     */
    public Restrictions plus(final Restrictions other) {
        final Set<String> t = new LinkedHashSet<>(terms);
        t.addAll(other.terms);
        final Set<String> u = new LinkedHashSet<>(unrecognized);
        u.addAll(other.unrecognized);
        return new Restrictions(List.copyOf(t), List.copyOf(u));
    }

    /**
     * @param food
     *            a dish
     * @return the restriction it breaks, or null if it is safe
     */
    public String violatedBy(final Food food) {
        for (final String term : terms) {
            if (AllergenMatcher.foodDeclaresAllergen(food.getAllergies(), term)) {
                return term;
            }
        }
        final String name = " " + AllergenMatcher.normalizeTerm(food.getFoodName()) + " ";
        for (final String term : terms) {
            if (name.contains(" " + term + " ")) {
                return term;
            }
        }
        return null;
    }

    /** @return restrictions as typed that could not be mapped to allergen tags */
    public List<String> unrecognized() {
        return unrecognized;
    }
}
