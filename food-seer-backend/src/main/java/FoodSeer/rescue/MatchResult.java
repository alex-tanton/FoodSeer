package FoodSeer.rescue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one matcher run decided.
 *
 * @param ranked customers who passed every filter, best first
 * @param excluded customers who were left out, in input order, with the first
 *        filter that excluded them
 * @param alertBudget how many of the ranked customers should be alerted
 */
public record MatchResult(List<RankedCandidate> ranked, Map<String, Exclusion> excluded, int alertBudget) {

    public MatchResult {
        ranked = List.copyOf(ranked);
        excluded = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(excluded));
    }

    /** @return the top {@code alertBudget} ranked customers */
    public List<RankedCandidate> toAlert() {
        return ranked.subList(0, Math.min(alertBudget, ranked.size()));
    }
}
