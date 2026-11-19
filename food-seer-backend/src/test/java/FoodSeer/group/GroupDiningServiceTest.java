package FoodSeer.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import FoodSeer.dto.OrderDto;
import FoodSeer.entity.Food;
import FoodSeer.entity.User;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.FoodRepository;
import FoodSeer.repositories.UserRepository;
import FoodSeer.service.OrderService;

public class GroupDiningServiceTest {

    private GroupSessionRepository sessionRepo;
    private GroupMemberRepository memberRepo;
    private GroupCartItemRepository cartItemRepo;
    private FoodRepository foodRepo;
    private UserRepository userRepo;
    private OrderService orderService;
    private GroupHarmonizerService harmonizer;
    private GroupDiningServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(GroupSessionRepository.class);
        memberRepo = mock(GroupMemberRepository.class);
        cartItemRepo = mock(GroupCartItemRepository.class);
        foodRepo = mock(FoodRepository.class);
        userRepo = mock(UserRepository.class);
        orderService = mock(OrderService.class);
        harmonizer = new GroupHarmonizerService();

        service = new GroupDiningServiceImpl(sessionRepo, memberRepo, cartItemRepo, foodRepo, userRepo, orderService,
                harmonizer);
    }

    @Test
    void createSessionGeneratesCodeAndAddsHost() {
        when(sessionRepo.existsBySessionCode(any())).thenReturn(false);

        final GroupSession savedSession = new GroupSession("CODE01", "Lunch", "host_alex");
        savedSession.setId(1L);
        when(sessionRepo.save(any(GroupSession.class))).thenReturn(savedSession);
        when(sessionRepo.findBySessionCode(any())).thenReturn(Optional.of(savedSession));

        final User hostUser = new User();
        hostUser.setUsername("host_alex");
        hostUser.setDietaryRestrictions("GLUTEN");
        hostUser.setCostPreference("budget");
        when(userRepo.findByUsername("host_alex")).thenReturn(Optional.of(hostUser));

        final GroupDtos.CreateSessionRequest req = new GroupDtos.CreateSessionRequest("Lunch");
        final GroupDtos.GroupSessionDto result = service.createSession("host_alex", req);

        assertNotNull(result);
        assertEquals("Lunch", result.sessionName());
        assertEquals("host_alex", result.hostUsername());
        verify(memberRepo).save(any(GroupMember.class));
    }

    @Test
    void createSessionRejectsBlankName() {
        final GroupDtos.CreateSessionRequest req = new GroupDtos.CreateSessionRequest("   ");
        assertThrows(IllegalArgumentException.class, () -> service.createSession("host_alex", req));
    }

    @Test
    void joinSessionAddsNewMember() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        session.setId(1L);
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));
        when(memberRepo.findBySessionAndUsername(session, "maya")).thenReturn(Optional.empty());

        final GroupDtos.JoinSessionRequest req = new GroupDtos.JoinSessionRequest("Maya", "PEANUTS", "moderate");
        service.joinSession("ROOM01", "maya", req);

        verify(memberRepo).save(any(GroupMember.class));
    }

    @Test
    void joinSessionFailsIfSessionInactive() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        session.setStatus(GroupSession.STATUS_SUBMITTED);
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));

        final GroupDtos.JoinSessionRequest req = new GroupDtos.JoinSessionRequest("Maya", "PEANUTS", "moderate");
        assertThrows(IllegalStateException.class, () -> service.joinSession("ROOM01", "maya", req));
    }

    @Test
    void addToTrayCreatesCartItem() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        session.setId(1L);
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));

        final GroupMember member = new GroupMember(session, "alex", "Alex", null, null);
        when(memberRepo.findBySessionAndUsername(session, "alex")).thenReturn(Optional.of(member));

        final Food food = new Food("SALAD", 10, 12, Collections.emptyList());
        food.setId(10L);
        when(foodRepo.findById(10L)).thenReturn(Optional.of(food));

        final GroupDtos.AddCartItemRequest req = new GroupDtos.AddCartItemRequest(10L, 2, "Extra dressing");
        service.addToTray("ROOM01", "alex", req);

        verify(cartItemRepo).save(any(GroupCartItem.class));
    }

    @Test
    void removeFromTrayAllowsOwnerOrHost() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        session.setId(1L);
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));

        final GroupMember memberMaya = new GroupMember(session, "maya", "Maya", null, null);
        final GroupCartItem item = new GroupCartItem(session, memberMaya, 5L, "SOUP", 8, 1, "");
        item.setId(50L);
        when(cartItemRepo.findById(50L)).thenReturn(Optional.of(item));

        // Maya removes her own item
        service.removeFromTray("ROOM01", "maya", 50L);
        verify(cartItemRepo).delete(item);

        // Host can also remove item
        service.removeFromTray("ROOM01", "host_alex", 50L);
        verify(cartItemRepo, org.mockito.Mockito.times(2)).delete(item);
    }

    @Test
    void removeFromTrayRejectsUnauthorizedMember() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        session.setId(1L);
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));

        final GroupMember memberMaya = new GroupMember(session, "maya", "Maya", null, null);
        final GroupCartItem item = new GroupCartItem(session, memberMaya, 5L, "SOUP", 8, 1, "");
        item.setId(50L);
        when(cartItemRepo.findById(50L)).thenReturn(Optional.of(item));

        // Stranger tries to delete Maya's item
        assertThrows(IllegalStateException.class, () -> service.removeFromTray("ROOM01", "stranger_bob", 50L));
    }

    @Test
    void computesSplitBillWithItemizedShares() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        session.setId(1L);
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));

        final GroupMember memberAlex = new GroupMember(session, "alex", "Alex", null, null);
        final GroupMember memberMaya = new GroupMember(session, "maya", "Maya", null, null);
        when(memberRepo.findBySessionOrderByJoinedAtAsc(session)).thenReturn(List.of(memberAlex, memberMaya));

        // Alex: 1 item at $10. Maya: 2 items at $15 ($30 total)
        final GroupCartItem itemAlex = new GroupCartItem(session, memberAlex, 1L, "BURRITO", 10, 1, "");
        final GroupCartItem itemMaya = new GroupCartItem(session, memberMaya, 2L, "STEAK", 15, 2, "");
        when(cartItemRepo.findBySessionOrderByAddedAtAsc(session)).thenReturn(List.of(itemAlex, itemMaya));

        final GroupDtos.SplitBillResponse response = service.getSplitBill("ROOM01");

        // Subtotal = 10 + 30 = 40.00
        assertEquals(new BigDecimal("40.00"), response.overallSubtotal());
        // Tax 8% = 3.20
        assertEquals(new BigDecimal("3.20"), response.taxAndFees());
        // Total = 43.20
        assertEquals(new BigDecimal("43.20"), response.totalAmount());

        assertEquals(2, response.memberShares().size());
        final GroupDtos.SplitBillShare shareAlex = response.memberShares().stream()
                .filter(s -> s.memberUsername().equals("alex")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("10.00"), shareAlex.itemsSubtotal());
        assertEquals(new BigDecimal("0.80"), shareAlex.taxShare());
        assertEquals(new BigDecimal("10.80"), shareAlex.totalOwed());

        final GroupDtos.SplitBillShare shareMaya = response.memberShares().stream()
                .filter(s -> s.memberUsername().equals("maya")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("30.00"), shareMaya.itemsSubtotal());
        assertEquals(new BigDecimal("2.40"), shareMaya.taxShare());
        assertEquals(new BigDecimal("32.40"), shareMaya.totalOwed());
    }

    @Test
    void checkoutRejectsNonHost() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> service.checkout("ROOM01", "guest_leo"));
    }

    @Test
    void checkoutRejectsEmptyCart() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));
        when(cartItemRepo.findBySessionOrderByAddedAtAsc(session)).thenReturn(Collections.emptyList());

        assertThrows(IllegalStateException.class, () -> service.checkout("ROOM01", "host_alex"));
    }

    @Test
    void checkoutCreatesOrderAndTransitionsSession() {
        final GroupSession session = new GroupSession("ROOM01", "Dinner", "host_alex");
        session.setId(1L);
        when(sessionRepo.findBySessionCode("ROOM01")).thenReturn(Optional.of(session));

        final GroupMember member = new GroupMember(session, "host_alex", "Alex", "PEANUTS", null);
        when(memberRepo.findBySessionOrderByJoinedAtAsc(session)).thenReturn(List.of(member));

        final Food food = new Food("SALAD", 10, 10, Collections.emptyList());
        food.setId(100L);
        final GroupCartItem item = new GroupCartItem(session, member, 100L, "SALAD", 10, 1, "");
        when(cartItemRepo.findBySessionOrderByAddedAtAsc(session)).thenReturn(List.of(item));
        when(foodRepo.findAllById(any())).thenReturn(List.of(food));

        final OrderDto createdOrder = new OrderDto();
        createdOrder.setId(999L);
        createdOrder.setStatus("PENDING");
        when(orderService.createOrder(any())).thenReturn(createdOrder);

        final GroupDtos.CheckoutResponse res = service.checkout("ROOM01", "host_alex");

        assertEquals(999L, res.orderId());
        assertEquals("PENDING", res.orderStatus());
        assertEquals(GroupSession.STATUS_SUBMITTED, session.getStatus());
        verify(sessionRepo).save(session);
    }
}
