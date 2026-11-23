package FoodSeer.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import FoodSeer.entity.Food;
import FoodSeer.service.AllergenMatcher;

/**
 * Consensus evaluation and cross-contamination auditing engine for group dining.
 * Applies AllergenMatcher without modifying any inherited matching semantics.
 */
@Service
public class GroupHarmonizerService {

    /**
     * Evaluates a single dish's safety across all participants in a dining session.
     */
    public GroupDtos.DishSafetyReport evaluateDishSafety(final Food food,
            final Collection<GroupMember> members) {
        if (members == null || members.isEmpty()) {
            return new GroupDtos.DishSafetyReport(food.getId(), food.getFoodName(), food.getPrice(),
                    food.getCalories(), food.getAllergies() != null ? food.getAllergies() : Collections.emptyList(),
                    true, Collections.emptyList(), 1.0);
        }

        final List<String> allergies = food.getAllergies() != null ? food.getAllergies() : Collections.emptyList();
        final List<GroupDtos.MemberConflict> conflicts = new ArrayList<>();

        for (final GroupMember member : members) {
            final String restrictions = member.getDietaryRestrictions();
            if (restrictions == null || restrictions.trim().isEmpty()) {
                continue;
            }

            final Set<String> conflictingAtoms = new LinkedHashSet<>();
            for (final String tag : allergies) {
                if (AllergenMatcher.restrictionsAffectedBy(restrictions, tag)) {
                    conflictingAtoms.add(AllergenMatcher.normalizeTerm(tag));
                }
            }

            // Also check individual words in dish name against member restrictions
            if (food.getFoodName() != null) {
                for (final String word : food.getFoodName().split("[\\s,;/-]+")) {
                    if (!word.isBlank() && AllergenMatcher.restrictionsAffectedBy(restrictions, word)) {
                        conflictingAtoms.add(AllergenMatcher.normalizeTerm(word));
                    }
                }
            }

            if (!conflictingAtoms.isEmpty()) {
                conflicts.add(new GroupDtos.MemberConflict(member.getUsername(), member.getDisplayName(),
                        "Contains " + String.join(", ", conflictingAtoms), new ArrayList<>(conflictingAtoms)));
            }
        }

        final boolean universallySafe = conflicts.isEmpty();
        final double harmonyScore = (double) (members.size() - conflicts.size()) / members.size();

        return new GroupDtos.DishSafetyReport(food.getId(), food.getFoodName(), food.getPrice(),
                food.getCalories(), allergies, universallySafe, conflicts, harmonyScore);
    }

    /**
     * Audits table-wide cart items for cross-contamination risks against seated members' allergies.
     */
    public List<GroupDtos.CrossContaminationAlert> auditCrossContamination(
            final Collection<GroupCartItem> cartItems, final Collection<GroupMember> members,
            final Map<Long, Food> foodLookup) {
        if (cartItems == null || cartItems.isEmpty() || members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        final List<GroupDtos.CrossContaminationAlert> alerts = new ArrayList<>();
        final Set<String> deduplicationKeys = new HashSet<>();

        for (final GroupCartItem item : cartItems) {
            final Food food = foodLookup.get(item.getFoodId());
            final List<String> tags = food != null && food.getAllergies() != null ? food.getAllergies()
                    : Collections.emptyList();

            for (final GroupMember otherMember : members) {
                // We focus on cross-member table contamination
                if (otherMember.getUsername().equalsIgnoreCase(item.getMember().getUsername())) {
                    continue;
                }

                final String restrictions = otherMember.getDietaryRestrictions();
                if (restrictions == null || restrictions.trim().isEmpty()) {
                    continue;
                }

                for (final String tag : tags) {
                    if (AllergenMatcher.restrictionsAffectedBy(restrictions, tag)) {
                        final String normTag = AllergenMatcher.normalizeTerm(tag);
                        final String dedupKey = otherMember.getUsername() + ":" + item.getFoodId() + ":" + normTag;
                        if (deduplicationKeys.add(dedupKey)) {
                            alerts.add(new GroupDtos.CrossContaminationAlert(otherMember.getUsername(),
                                    otherMember.getDisplayName(), normTag, item.getFoodName(),
                                    item.getMember().getUsername(), item.getMember().getDisplayName(),
                                    "Tray contains " + normTag + " which conflicts with "
                                            + otherMember.getDisplayName() + "'s allergy. Request separate packaging."));
                        }
                    }
                }
            }
        }

        return alerts;
    }

    /**
     * Calculates overall menu harmony percentage for a given group.
     */
    public double computeHarmonyScore(final List<Food> foods, final Collection<GroupMember> members) {
        if (foods == null || foods.isEmpty() || members == null || members.isEmpty()) {
            return 1.0;
        }

        int safeCount = 0;
        for (final Food food : foods) {
            final GroupDtos.DishSafetyReport report = evaluateDishSafety(food, members);
            if (report.isUniversallySafe()) {
                safeCount++;
            }
        }
        return (double) safeCount / foods.size();
    }
}
