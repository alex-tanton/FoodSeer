package FoodSeer.group;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import FoodSeer.dto.OrderDto;
import FoodSeer.entity.Food;
import FoodSeer.entity.User;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.OrderService;

@Service
@Transactional
public class GroupDiningServiceImpl implements GroupDiningService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigDecimal TAX_RATE = BigDecimal.valueOf(0.08); // 8% sales tax

    private final GroupSessionRepository sessionRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupCartItemRepository cartItemRepository;
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final GroupHarmonizerService harmonizerService;

    public GroupDiningServiceImpl(final GroupSessionRepository sessionRepository,
            final GroupMemberRepository memberRepository, final GroupCartItemRepository cartItemRepository,
            final FoodRepository foodRepository, final UserRepository userRepository,
            final OrderService orderService, final GroupHarmonizerService harmonizerService) {
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
        this.cartItemRepository = cartItemRepository;
        this.foodRepository = foodRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.harmonizerService = harmonizerService;
    }

    private String generateUniqueCode() {
        for (int attempts = 0; attempts < 50; attempts++) {
            final StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            final String code = sb.toString();
            if (!sessionRepository.existsBySessionCode(code)) {
                return code;
            }
        }
        return "GRP" + System.currentTimeMillis() % 1000;
    }

    @Override
    public GroupDtos.GroupSessionDto createSession(final String hostUsername,
            final GroupDtos.CreateSessionRequest request) {
        if (request == null || request.sessionName() == null || request.sessionName().trim().isEmpty()) {
            throw new IllegalArgumentException("Session name cannot be blank.");
        }

        final String code = generateUniqueCode();
        final GroupSession session = new GroupSession(code, request.sessionName().trim(), hostUsername);
        final GroupSession savedSession = sessionRepository.save(session);

        // Auto-join host as the initial member
        final Optional<User> hostUser = userRepository.findByUsername(hostUsername);
        final String restrictions = hostUser.map(User::getDietaryRestrictions).orElse(null);
        final String costPref = hostUser.map(User::getCostPreference).orElse(null);
        final String displayName = hostUsername;

        final GroupMember hostMember = new GroupMember(savedSession, hostUsername, displayName, restrictions, costPref);
        memberRepository.save(hostMember);

        return getSession(code);
    }

    @Override
    public GroupDtos.GroupSessionDto joinSession(final String sessionCode, final String username,
            final GroupDtos.JoinSessionRequest request) {
        final GroupSession session = findActiveSession(sessionCode);

        String restrictions = request != null ? request.dietaryRestrictions() : null;
        String costPref = request != null ? request.costPreference() : null;
        String displayName = request != null ? request.displayName() : null;

        // Fallback to user account defaults if not explicitly provided
        final Optional<User> userOpt = userRepository.findByUsername(username);
        if ((restrictions == null || restrictions.isBlank()) && userOpt.isPresent()) {
            restrictions = userOpt.get().getDietaryRestrictions();
        }
        if ((costPref == null || costPref.isBlank()) && userOpt.isPresent()) {
            costPref = userOpt.get().getCostPreference();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }

        final Optional<GroupMember> existing = memberRepository.findBySessionAndUsername(session, username);
        if (existing.isPresent()) {
            final GroupMember member = existing.get();
            member.setDisplayName(displayName);
            member.setDietaryRestrictions(restrictions);
            member.setCostPreference(costPref);
            memberRepository.save(member);
        } else {
            final GroupMember newMember = new GroupMember(session, username, displayName, restrictions, costPref);
            memberRepository.save(newMember);
        }

        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        return getSession(sessionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDtos.GroupSessionDto getSession(final String sessionCode) {
        final GroupSession session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Group session not found: " + sessionCode));

        final List<GroupMember> members = memberRepository.findBySessionOrderByJoinedAtAsc(session);
        final List<GroupCartItem> cartItems = cartItemRepository.findBySessionOrderByAddedAtAsc(session);

        final Map<Long, Food> foodLookup = loadFoodLookup(cartItems);
        final List<GroupDtos.CrossContaminationAlert> alerts = harmonizerService.auditCrossContamination(cartItems,
                members, foodLookup);

        final List<GroupDtos.GroupMemberDto> memberDtos = members.stream()
                .map(GroupDtos.GroupMemberDto::of)
                .collect(Collectors.toList());

        final List<GroupDtos.GroupCartItemDto> itemDtos = cartItems.stream()
                .map(GroupDtos.GroupCartItemDto::of)
                .collect(Collectors.toList());

        return new GroupDtos.GroupSessionDto(session.getId(), session.getSessionCode(), session.getSessionName(),
                session.getHostUsername(), session.getStatus(), session.getCreatedAt(), session.getOrderId(),
                memberDtos, itemDtos, alerts);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDtos.HarmonizedMenuResponse getHarmonizedMenu(final String sessionCode) {
        final GroupSession session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Group session not found: " + sessionCode));

        final List<Food> foods = foodRepository.findAll();
        final List<GroupMember> members = memberRepository.findBySessionOrderByJoinedAtAsc(session);

        final List<GroupDtos.DishSafetyReport> reports = new ArrayList<>();
        int safeCount = 0;

        for (final Food food : foods) {
            final GroupDtos.DishSafetyReport report = harmonizerService.evaluateDishSafety(food, members);
            reports.add(report);
            if (report.isUniversallySafe()) {
                safeCount++;
            }
        }

        return new GroupDtos.HarmonizedMenuResponse(session.getSessionCode(), session.getSessionName(),
                members.size(), safeCount, reports);
    }

    @Override
    public GroupDtos.GroupSessionDto addToTray(final String sessionCode, final String username,
            final GroupDtos.AddCartItemRequest request) {
        final GroupSession session = findActiveSession(sessionCode);

        final GroupMember member = memberRepository.findBySessionAndUsername(session, username)
                .orElseThrow(() -> new IllegalStateException("User " + username + " is not a member of this session."));

        final Food food = foodRepository.findById(request.foodId())
                .orElseThrow(() -> new ResourceNotFoundException("Food not found with id: " + request.foodId()));

        final int quantity = request.quantity() > 0 ? request.quantity() : 1;
        final GroupCartItem item = new GroupCartItem(session, member, food.getId(), food.getFoodName(),
                food.getPrice(), quantity, request.specialInstructions());

        cartItemRepository.save(item);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        return getSession(sessionCode);
    }

    @Override
    public GroupDtos.GroupSessionDto removeFromTray(final String sessionCode, final String username,
            final Long itemId) {
        final GroupSession session = findActiveSession(sessionCode);

        final GroupCartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + itemId));

        if (!item.getSession().getId().equals(session.getId())) {
            throw new IllegalArgumentException("Cart item does not belong to this session.");
        }

        // Allow item owner or session host to remove item
        final boolean isOwner = item.getMember().getUsername().equalsIgnoreCase(username);
        final boolean isHost = session.getHostUsername().equalsIgnoreCase(username);
        if (!isOwner && !isHost) {
            throw new IllegalStateException("Only the item owner or room host may remove this item.");
        }

        cartItemRepository.delete(item);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        return getSession(sessionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDtos.SplitBillResponse getSplitBill(final String sessionCode) {
        final GroupSession session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Group session not found: " + sessionCode));

        final List<GroupMember> members = memberRepository.findBySessionOrderByJoinedAtAsc(session);
        final List<GroupCartItem> cartItems = cartItemRepository.findBySessionOrderByAddedAtAsc(session);

        BigDecimal overallSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        final Map<String, BigDecimal> memberSubtotals = new HashMap<>();
        final Map<String, Integer> memberItemCounts = new HashMap<>();

        for (final GroupMember member : members) {
            memberSubtotals.put(member.getUsername(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            memberItemCounts.put(member.getUsername(), 0);
        }

        for (final GroupCartItem item : cartItems) {
            final BigDecimal itemTotal = BigDecimal.valueOf((long) item.getPrice() * item.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);
            overallSubtotal = overallSubtotal.add(itemTotal);

            final String username = item.getMember().getUsername();
            memberSubtotals.put(username, memberSubtotals.getOrDefault(username, BigDecimal.ZERO).add(itemTotal));
            memberItemCounts.put(username, memberItemCounts.getOrDefault(username, 0) + item.getQuantity());
        }

        final BigDecimal taxAndFees = overallSubtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        final BigDecimal totalAmount = overallSubtotal.add(taxAndFees);

        final List<GroupDtos.SplitBillShare> shares = new ArrayList<>();
        for (final GroupMember member : members) {
            final BigDecimal mSub = memberSubtotals.getOrDefault(member.getUsername(), BigDecimal.ZERO);
            final int count = memberItemCounts.getOrDefault(member.getUsername(), 0);
            final BigDecimal taxShare = overallSubtotal.compareTo(BigDecimal.ZERO) > 0
                    ? mSub.divide(overallSubtotal, 4, RoundingMode.HALF_UP).multiply(taxAndFees).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            final BigDecimal totalOwed = mSub.add(taxShare);

            shares.add(new GroupDtos.SplitBillShare(member.getUsername(), member.getDisplayName(), count, mSub,
                    taxShare, totalOwed));
        }

        final Map<Long, Food> foodLookup = loadFoodLookup(cartItems);
        final List<GroupDtos.CrossContaminationAlert> alerts = harmonizerService.auditCrossContamination(cartItems,
                members, foodLookup);

        return new GroupDtos.SplitBillResponse(session.getSessionCode(), overallSubtotal, taxAndFees, totalAmount,
                shares, alerts);
    }

    @Override
    public GroupDtos.CheckoutResponse checkout(final String sessionCode, final String hostUsername) {
        final GroupSession session = findActiveSession(sessionCode);

        if (!session.getHostUsername().equalsIgnoreCase(hostUsername)) {
            throw new IllegalStateException("Only the session host can checkout the group order.");
        }

        final List<GroupCartItem> cartItems = cartItemRepository.findBySessionOrderByAddedAtAsc(session);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty group cart.");
        }

        final List<GroupMember> members = memberRepository.findBySessionOrderByJoinedAtAsc(session);
        final Map<Long, Food> foodLookup = loadFoodLookup(cartItems);

        // Build list of Food items for standard Order
        final List<Food> foods = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (final GroupCartItem item : cartItems) {
            final Food food = foodLookup.get(item.getFoodId());
            if (food != null) {
                for (int i = 0; i < item.getQuantity(); i++) {
                    foods.add(food);
                }
                subtotal = subtotal.add(BigDecimal.valueOf((long) item.getPrice() * item.getQuantity()));
            }
        }

        final BigDecimal deliveryCost = BigDecimal.valueOf(3.00);
        final BigDecimal totalCost = subtotal.add(deliveryCost).setScale(2, RoundingMode.HALF_UP);

        // Compile composite dietary instructions for kitchen
        final StringBuilder kitchenNotes = new StringBuilder();
        kitchenNotes.append("GROUP ORDER [").append(session.getSessionCode()).append(": ")
                .append(session.getSessionName()).append("]\n");
        kitchenNotes.append("Participants (").append(members.size()).append("): ");
        kitchenNotes.append(members.stream()
                .map(m -> m.getDisplayName() + (m.getDietaryRestrictions() != null ? " (" + m.getDietaryRestrictions() + ")" : ""))
                .collect(Collectors.joining(", ")));
        kitchenNotes.append("\n");

        final List<GroupDtos.CrossContaminationAlert> alerts = harmonizerService.auditCrossContamination(cartItems,
                members, foodLookup);
        if (!alerts.isEmpty()) {
            kitchenNotes.append("CROSS-CONTAMINATION ALERTS:\n");
            for (final GroupDtos.CrossContaminationAlert alert : alerts) {
                kitchenNotes.append("- ").append(alert.recommendation()).append("\n");
            }
        }

        final OrderDto orderDto = new OrderDto();
        orderDto.setName("Group: " + session.getSessionName() + " (" + session.getSessionCode() + ")");
        orderDto.setFoods(foods);
        orderDto.setCost(totalCost);
        orderDto.setStatus("PENDING");
        orderDto.setDeliveryCost(deliveryCost);
        orderDto.setAllergyRestrictions(kitchenNotes.toString());

        final OrderDto createdOrder = orderService.createOrder(orderDto);

        session.setStatus(GroupSession.STATUS_SUBMITTED);
        session.setSubmittedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        session.setOrderId(createdOrder.getId());
        sessionRepository.save(session);

        return new GroupDtos.CheckoutResponse(session.getSessionCode(), createdOrder.getId(),
                createdOrder.getStatus(), totalCost, kitchenNotes.toString());
    }

    private GroupSession findActiveSession(final String sessionCode) {
        final GroupSession session = sessionRepository.findBySessionCode(sessionCode.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Group session not found: " + sessionCode));
        if (!GroupSession.STATUS_ACTIVE.equals(session.getStatus())) {
            throw new IllegalStateException("Group session is not active. Status: " + session.getStatus());
        }
        return session;
    }

    private Map<Long, Food> loadFoodLookup(final List<GroupCartItem> cartItems) {
        final List<Long> foodIds = cartItems.stream().map(GroupCartItem::getFoodId).distinct().collect(Collectors.toList());
        final List<Food> foods = foodRepository.findAllById(foodIds);
        return foods.stream().collect(Collectors.toMap(Food::getId, f -> f));
    }
}
