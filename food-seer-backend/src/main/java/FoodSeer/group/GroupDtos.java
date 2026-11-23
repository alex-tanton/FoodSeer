package FoodSeer.group;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Data transfer objects for group dining sessions, consensus filtering, and split billing.
 */
public final class GroupDtos {

    private GroupDtos() {
        // utility record holder
    }

    public record CreateSessionRequest(String sessionName) {
    }

    public record JoinSessionRequest(String displayName, String dietaryRestrictions, String costPreference) {
    }

    public record AddCartItemRequest(Long foodId, int quantity, String specialInstructions) {
    }

    public record GroupMemberDto(Long id, String username, String displayName, String dietaryRestrictions,
            String costPreference, Instant joinedAt) {

        static GroupMemberDto of(final GroupMember member) {
            return new GroupMemberDto(member.getId(), member.getUsername(), member.getDisplayName(),
                    member.getDietaryRestrictions(), member.getCostPreference(), member.getJoinedAt());
        }
    }

    public record GroupCartItemDto(Long id, Long memberId, String memberUsername, String memberDisplayName,
            Long foodId, String foodName, int price, int quantity, String specialInstructions, Instant addedAt) {

        static GroupCartItemDto of(final GroupCartItem item) {
            return new GroupCartItemDto(item.getId(), item.getMember().getId(), item.getMember().getUsername(),
                    item.getMember().getDisplayName(), item.getFoodId(), item.getFoodName(), item.getPrice(),
                    item.getQuantity(), item.getSpecialInstructions(), item.getAddedAt());
        }
    }

    public record MemberConflict(String memberUsername, String memberDisplayName, String conflictReason,
            List<String> conflictingAtoms) {
    }

    public record DishSafetyReport(Long foodId, String foodName, int price, Integer calories,
            List<String> allergies, boolean isUniversallySafe, List<MemberConflict> memberConflicts,
            double harmonyScore) {
    }

    public record HarmonizedMenuResponse(String sessionCode, String sessionName, int totalMembers,
            int safeDishCount, List<DishSafetyReport> dishes) {
    }

    public record CrossContaminationAlert(String affectedMemberUsername, String affectedMemberDisplayName,
            String allergen, String sourceDishName, String orderedByUsername, String orderedByDisplayName,
            String recommendation) {
    }

    public record SplitBillShare(String memberUsername, String memberDisplayName, int itemCount,
            BigDecimal itemsSubtotal, BigDecimal taxShare, BigDecimal totalOwed) {
    }

    public record SplitBillResponse(String sessionCode, BigDecimal overallSubtotal, BigDecimal taxAndFees,
            BigDecimal totalAmount, List<SplitBillShare> memberShares,
            List<CrossContaminationAlert> contaminationAlerts) {
    }

    public record GroupSessionDto(Long id, String sessionCode, String sessionName, String hostUsername,
            String status, Instant createdAt, Long orderId, List<GroupMemberDto> members,
            List<GroupCartItemDto> cartItems, List<CrossContaminationAlert> alerts) {
    }

    public record CheckoutResponse(String sessionCode, Long orderId, String orderStatus, BigDecimal totalCost,
            String kitchenNotes) {
    }
}
