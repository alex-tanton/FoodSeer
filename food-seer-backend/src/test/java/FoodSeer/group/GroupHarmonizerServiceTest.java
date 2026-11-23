package FoodSeer.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import FoodSeer.entity.Food;

public class GroupHarmonizerServiceTest {

    private GroupHarmonizerService harmonizer;
    private GroupSession testSession;

    @BeforeEach
    void setUp() {
        harmonizer = new GroupHarmonizerService();
        testSession = new GroupSession("ROOM01", "Test Room", "host_alex");
    }

    @Test
    void evaluatesDishWithZeroMembersAsUniversallySafe() {
        final Food salad = new Food("SALAD", 10, 10, Collections.emptyList());
        final GroupDtos.DishSafetyReport report = harmonizer.evaluateDishSafety(salad, Collections.emptyList());

        assertTrue(report.isUniversallySafe());
        assertEquals(1.0, report.harmonyScore(), 0.001);
        assertTrue(report.memberConflicts().isEmpty());
    }

    @Test
    void flagsDishWhenMemberHasDirectAllergy() {
        final GroupMember member1 = new GroupMember(testSession, "leo", "Leo", "PEANUTS", "budget");
        final GroupMember member2 = new GroupMember(testSession, "maya", "Maya", "GLUTEN", "moderate");
        final List<GroupMember> members = Arrays.asList(member1, member2);

        final Food peanutDish = new Food("THAI NOODLES", 10, 15, Arrays.asList("PEANUTS", "SOY"));
        final GroupDtos.DishSafetyReport report = harmonizer.evaluateDishSafety(peanutDish, members);

        assertFalse(report.isUniversallySafe());
        assertEquals(0.5, report.harmonyScore(), 0.001); // 1 safe out of 2 members
        assertEquals(1, report.memberConflicts().size());
        assertEquals("leo", report.memberConflicts().get(0).memberUsername());
        assertTrue(report.memberConflicts().get(0).conflictingAtoms().contains("PEANUT"));
    }

    @Test
    void flagsDishViaGroupExpansionForTreeNuts() {
        // Customer avoids TREE NUTS; dish declares CASHEW
        final GroupMember member = new GroupMember(testSession, "vic", "Vic", "TREE NUT", "premium");
        final Food cashewChicken = new Food("CASHEW CHICKEN", 10, 18, Arrays.asList("CASHEW", "CHICKEN"));

        final GroupDtos.DishSafetyReport report = harmonizer.evaluateDishSafety(cashewChicken,
                Collections.singletonList(member));

        assertFalse(report.isUniversallySafe());
        assertEquals(1, report.memberConflicts().size());
        assertEquals("vic", report.memberConflicts().get(0).memberUsername());
    }

    @Test
    void flagsDishWhenFoodNameMatchesMemberRestriction() {
        final GroupMember member = new GroupMember(testSession, "ana", "Ana", "PEANUT", "budget");
        // Dish has empty tags, but food name contains PEANUT
        final Food peanutButterToast = new Food("PEANUT BUTTER TOAST", 5, 6, Collections.emptyList());

        final GroupDtos.DishSafetyReport report = harmonizer.evaluateDishSafety(peanutButterToast,
                Collections.singletonList(member));

        assertFalse(report.isUniversallySafe());
        assertEquals(1, report.memberConflicts().size());
        assertEquals("ana", report.memberConflicts().get(0).memberUsername());
    }

    @Test
    void dishIsUniversallySafeWhenNoMembersConflict() {
        final GroupMember member1 = new GroupMember(testSession, "alex", "Alex", "SHELLFISH", "moderate");
        final GroupMember member2 = new GroupMember(testSession, "maya", "Maya", "GLUTEN", "moderate");
        final List<GroupMember> members = Arrays.asList(member1, member2);

        // Steak has beef, safe for both shellfish & gluten allergic members
        final Food steak = new Food("STEAK", 10, 25, Collections.singletonList("BEEF"));
        final GroupDtos.DishSafetyReport report = harmonizer.evaluateDishSafety(steak, members);

        assertTrue(report.isUniversallySafe());
        assertEquals(1.0, report.harmonyScore(), 0.001);
        assertTrue(report.memberConflicts().isEmpty());
    }

    @Test
    void auditsCrossContaminationBetweenDifferentMembersTrays() {
        final GroupMember memberAlex = new GroupMember(testSession, "alex", "Alex", null, "moderate");
        final GroupMember memberLeo = new GroupMember(testSession, "leo", "Leo", "PEANUTS", "budget");
        final List<GroupMember> members = Arrays.asList(memberAlex, memberLeo);

        final Food peanutCurry = new Food("PEANUT CURRY", 10, 14, Collections.singletonList("PEANUTS"));
        peanutCurry.setId(101L);

        final Map<Long, Food> foodLookup = new HashMap<>();
        foodLookup.put(101L, peanutCurry);

        // Alex orders peanut curry, but Leo has a peanut allergy!
        final GroupCartItem item = new GroupCartItem(testSession, memberAlex, 101L, "PEANUT CURRY", 14, 1, "");
        final List<GroupCartItem> cartItems = Collections.singletonList(item);

        final List<GroupDtos.CrossContaminationAlert> alerts = harmonizer.auditCrossContamination(cartItems, members,
                foodLookup);

        assertEquals(1, alerts.size());
        final GroupDtos.CrossContaminationAlert alert = alerts.get(0);
        assertEquals("leo", alert.affectedMemberUsername());
        assertEquals("alex", alert.orderedByUsername());
        assertEquals("PEANUT", alert.allergen());
        assertTrue(alert.recommendation().contains("Leo's allergy"));
    }

    @Test
    void doesNotTriggerCrossContaminationForMembersOwnTray() {
        final GroupMember memberLeo = new GroupMember(testSession, "leo", "Leo", "PEANUTS", "budget");
        final List<GroupMember> members = Collections.singletonList(memberLeo);

        final Food peanutItem = new Food("PEANUT SNACK", 10, 5, Collections.singletonList("PEANUTS"));
        peanutItem.setId(202L);

        final Map<Long, Food> foodLookup = new HashMap<>();
        foodLookup.put(202L, peanutItem);

        // Leo added peanut snack to his own tray (e.g. for a friend or override)
        final GroupCartItem item = new GroupCartItem(testSession, memberLeo, 202L, "PEANUT SNACK", 5, 1, "");
        final List<GroupCartItem> cartItems = Collections.singletonList(item);

        final List<GroupDtos.CrossContaminationAlert> alerts = harmonizer.auditCrossContamination(cartItems, members,
                foodLookup);

        // Table cross-member alert is not triggered for oneself
        assertTrue(alerts.isEmpty());
    }

    @Test
    void deduplicatesIdenticalCrossContaminationAlertsAcrossMultipleOrders() {
        final GroupMember memberAlex = new GroupMember(testSession, "alex", "Alex", null, "moderate");
        final GroupMember memberLeo = new GroupMember(testSession, "leo", "Leo", "PEANUTS", "budget");
        final List<GroupMember> members = Arrays.asList(memberAlex, memberLeo);

        final Food peanutCurry = new Food("PEANUT CURRY", 10, 14, Collections.singletonList("PEANUTS"));
        peanutCurry.setId(301L);

        final Map<Long, Food> foodLookup = new HashMap<>();
        foodLookup.put(301L, peanutCurry);

        // Alex adds two separate peanut items of the same dish
        final GroupCartItem item1 = new GroupCartItem(testSession, memberAlex, 301L, "PEANUT CURRY", 14, 1, "");
        final GroupCartItem item2 = new GroupCartItem(testSession, memberAlex, 301L, "PEANUT CURRY", 14, 2, "");
        final List<GroupCartItem> cartItems = Arrays.asList(item1, item2);

        final List<GroupDtos.CrossContaminationAlert> alerts = harmonizer.auditCrossContamination(cartItems, members,
                foodLookup);

        assertEquals(1, alerts.size(), "Should deduplicate multiple items of the same dish for the same member");
    }

    @Test
    void computesOverallMenuHarmonyScoreAccurately() {
        final GroupMember member = new GroupMember(testSession, "leo", "Leo", "PEANUT", "budget");
        final List<GroupMember> members = Collections.singletonList(member);

        final Food safeFood1 = new Food("SALAD", 10, 8, Collections.emptyList());
        final Food safeFood2 = new Food("FRUIT BOWL", 10, 6, Collections.emptyList());
        final Food unsafeFood = new Food("PEANUTS", 10, 5, Collections.singletonList("PEANUT"));
        final List<Food> foods = Arrays.asList(safeFood1, safeFood2, unsafeFood);

        final double harmonyScore = harmonizer.computeHarmonyScore(foods, members);
        assertEquals(2.0 / 3.0, harmonyScore, 0.001);
    }
}
