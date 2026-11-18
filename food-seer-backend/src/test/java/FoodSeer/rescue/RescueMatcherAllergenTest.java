package FoodSeer.rescue;

import static FoodSeer.rescue.RescueFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import FoodSeer.service.AllergenMatcher;

/**
 * Allergen safety goes through the inherited {@link AllergenMatcher}, so
 * synonyms, plurals and groups behave the same as in the marketplace and the
 * trace-back feature.
 */
class RescueMatcherAllergenTest {

    @ParameterizedTest(name = "restriction \"{0}\" vs tag {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
        "dairy | MILK | ALLERGEN",
        "lactose intolerant | MILK | ALLERGEN",
        "Peanuts | PEANUTS | ALLERGEN",
        "peanut allergy | PEANUTS | ALLERGEN",
        "groundnuts | PEANUTS | ALLERGEN",
        "almonds | TREE-NUTS | ALLERGEN",
        "almonds and cashews | TREE-NUTS | ALLERGEN",
        "tree nuts | TREE-NUTS | ALLERGEN",
        "nuts | PEANUTS | ALLERGEN",
        "shrimp | SHELLFISH | ALLERGEN",
        "crustaceans | SHELLFISH | ALLERGEN",
        "gluten | WHEAT | ALLERGEN",
        "wheat | GLUTEN | ALLERGEN",
        "wheat flour | WHEAT | ALLERGEN",
        "soya | SOY | ALLERGEN",
        "salmon and tuna | FISH | ALLERGEN",
        "sesame seeds | SESAME | ALLERGEN",
        "eggs | EGGS | ALLERGEN"
    })
    void synonymsAndGroupsAreExcluded(final String restriction, final String tag, final Exclusion expected) {
        MatchResult result = run(listing(List.of(tag)), restricted("ann", restriction));
        assertEquals(expected, result.excluded().get("ann"));
    }

    @ParameterizedTest(name = "restriction \"{0}\" vs tag {1} is safe")
    @CsvSource(delimiter = '|', value = {
        "peanuts | TREE-NUTS",
        "fish | SHELLFISH",
        "shellfish | FISH",
        "sesame | SOY",
        "milk | EGGS"
    })
    void nonOverlappingAllergensAreMatched(final String restriction, final String tag) {
        MatchResult result = run(listing(List.of(tag)), restricted("ann", restriction));
        assertTrue(result.excluded().isEmpty(), () -> "excluded as " + result.excluded().get("ann"));
    }

    @ParameterizedTest(name = "\"{0}\" is not recognized")
    @CsvSource(delimiter = '|', value = {"soy sauce", "egg whites", "tahini", "peanut butter", "finned fish",
        "no dairy products", "milk protein"})
    void freeTextTheMatcherCannotReadIsWithheld(final String restriction) {
        MatchResult result = run(listing(List.of("CORN")), restricted("ann", restriction));
        assertEquals(Exclusion.UNCERTAIN_RESTRICTION, result.excluded().get("ann"));
    }

    @ParameterizedTest(name = "restriction \"{0}\" agrees with AllergenMatcher for tag {1}")
    @CsvSource(delimiter = '|', value = {"dairy | MILK", "peanuts | TREE-NUTS", "gluten | WHEAT",
        "shrimp | SHELLFISH", "fish | SHELLFISH"})
    void allergenDecisionMatchesTheInheritedCheck(final String restriction, final String tag) {
        boolean inheritedSaysConflict = AllergenMatcher.restrictionsAffectedBy(restriction, tag);
        MatchResult result = run(listing(List.of(tag)), restricted("ann", restriction));
        assertEquals(inheritedSaysConflict, result.excluded().get("ann") == Exclusion.ALLERGEN);
    }
}
