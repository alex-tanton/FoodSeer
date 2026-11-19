package FoodSeer.group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import FoodSeer.config.SpringSecurityConfig;
import FoodSeer.repositories.UserRepository;
import FoodSeer.security.CustomUserDetailsService;
import FoodSeer.security.DriverStatsAccess;
import FoodSeer.security.JwtAuthenticationFilter;
import FoodSeer.security.JwtTokenProvider;
import FoodSeer.service.impl.JwtAccessDeniedHandler;
import FoodSeer.service.impl.JwtAuthenticationEntryPoint;

@WebMvcTest(controllers = GroupDiningController.class, properties = {
    "app.jwt-secret=Zm9vZHNlZXItdGVzdC1vbmx5LXNpZ25pbmcta2V5LTMyaXRlbXM=",
    "app.jwt-expiration-milliseconds=60000"
})
@Import({SpringSecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
    CustomUserDetailsService.class, DriverStatsAccess.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class GroupDiningControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserRepository users;

    @MockBean
    private GroupDiningService groupDiningService;

    @Test
    @WithMockUser(username = "host_alex", roles = "CUSTOMER")
    void customerCanCreateGroupSession() throws Exception {
        final GroupDtos.GroupSessionDto sessionDto = new GroupDtos.GroupSessionDto(1L, "TEST26", "Study Group",
                "host_alex", GroupSession.STATUS_ACTIVE, Instant.now(), null, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        when(groupDiningService.createSession(eq("host_alex"), any())).thenReturn(sessionDto);

        mvc.perform(post("/api/group/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\": \"Study Group\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionCode").value("TEST26"))
                .andExpect(jsonPath("$.sessionName").value("Study Group"))
                .andExpect(jsonPath("$.hostUsername").value("host_alex"));

        verify(groupDiningService).createSession(eq("host_alex"), any());
    }

    @Test
    @WithMockUser(username = "maya", roles = "CUSTOMER")
    void customerCanJoinSession() throws Exception {
        final GroupDtos.GroupSessionDto sessionDto = new GroupDtos.GroupSessionDto(1L, "TEST26", "Study Group",
                "host_alex", GroupSession.STATUS_ACTIVE, Instant.now(), null, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        when(groupDiningService.joinSession(eq("TEST26"), eq("maya"), any())).thenReturn(sessionDto);

        mvc.perform(post("/api/group/sessions/TEST26/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\": \"Maya\", \"dietaryRestrictions\": \"PEANUTS\", \"costPreference\": \"moderate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("TEST26"));

        verify(groupDiningService).joinSession(eq("TEST26"), eq("maya"), any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void canGetHarmonizedMenu() throws Exception {
        final GroupDtos.DishSafetyReport dish = new GroupDtos.DishSafetyReport(10L, "SALAD", 10, 250,
                Collections.emptyList(), true, Collections.emptyList(), 1.0);
        final GroupDtos.HarmonizedMenuResponse menu = new GroupDtos.HarmonizedMenuResponse("TEST26", "Study Group", 2, 1,
                List.of(dish));

        when(groupDiningService.getHarmonizedMenu("TEST26")).thenReturn(menu);

        mvc.perform(get("/api/group/sessions/TEST26/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").value("TEST26"))
                .andExpect(jsonPath("$.safeDishCount").value(1))
                .andExpect(jsonPath("$.dishes[0].foodName").value("SALAD"))
                .andExpect(jsonPath("$.dishes[0].isUniversallySafe").value(true));
    }

    @Test
    @WithMockUser(username = "leo", roles = "CUSTOMER")
    void canAddAndRemoveItemsFromTray() throws Exception {
        final GroupDtos.GroupSessionDto sessionDto = new GroupDtos.GroupSessionDto(1L, "TEST26", "Study Group",
                "host_alex", GroupSession.STATUS_ACTIVE, Instant.now(), null, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        when(groupDiningService.addToTray(eq("TEST26"), eq("leo"), any())).thenReturn(sessionDto);
        when(groupDiningService.removeFromTray(eq("TEST26"), eq("leo"), eq(42L))).thenReturn(sessionDto);

        mvc.perform(post("/api/group/sessions/TEST26/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"foodId\": 10, \"quantity\": 1, \"specialInstructions\": \"No onions\"}"))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/group/sessions/TEST26/items/42"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void canGetSplitBill() throws Exception {
        final GroupDtos.SplitBillShare share = new GroupDtos.SplitBillShare("alex", "Alex", 1,
                new BigDecimal("10.00"), new BigDecimal("0.80"), new BigDecimal("10.80"));
        final GroupDtos.SplitBillResponse bill = new GroupDtos.SplitBillResponse("TEST26", new BigDecimal("10.00"),
                new BigDecimal("0.80"), new BigDecimal("10.80"), List.of(share), Collections.emptyList());

        when(groupDiningService.getSplitBill("TEST26")).thenReturn(bill);

        mvc.perform(get("/api/group/sessions/TEST26/split-bill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallSubtotal").value(10.00))
                .andExpect(jsonPath("$.totalAmount").value(10.80))
                .andExpect(jsonPath("$.memberShares[0].memberUsername").value("alex"));
    }

    @Test
    @WithMockUser(username = "host_alex", roles = "CUSTOMER")
    void hostCanCheckoutSession() throws Exception {
        final GroupDtos.CheckoutResponse res = new GroupDtos.CheckoutResponse("TEST26", 888L, "PENDING",
                new BigDecimal("23.00"), "Group notes");

        when(groupDiningService.checkout("TEST26", "host_alex")).thenReturn(res);

        mvc.perform(post("/api/group/sessions/TEST26/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(888))
                .andExpect(jsonPath("$.totalCost").value(23.00));
    }
}
