package FoodSeer.rescue;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import FoodSeer.config.SpringSecurityConfig;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.UserRepository;
import FoodSeer.security.CustomUserDetailsService;
import FoodSeer.security.DriverStatsAccess;
import FoodSeer.security.JwtAuthenticationFilter;
import FoodSeer.security.JwtTokenProvider;
import FoodSeer.service.impl.JwtAccessDeniedHandler;
import FoodSeer.service.impl.JwtAuthenticationEntryPoint;

/** Runs the real security filter chain, as the marketplace controller test does. */
@WebMvcTest(controllers = RescueController.class, properties = {
    "app.jwt-secret=Zm9vZHNlZXItdGVzdC1vbmx5LXNpZ25pbmcta2V5LTMyaXRlbXM=",
    "app.jwt-expiration-milliseconds=60000"
})
@Import({SpringSecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
    CustomUserDetailsService.class, DriverStatsAccess.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class RescueControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserRepository users;
    @MockBean private RescueService service;

    @ParameterizedTest
    @ValueSource(strings = {"/api/rescue/offers/1/matches", "/api/rescue/deals"})
    void anonymousCannotRead(final String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCannotNotify() throws Exception {
        mvc.perform(post("/api/rescue/offers/1/notify")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCanPreviewAndNotify() throws Exception {
        when(service.preview(1L)).thenReturn(new RescueDtos.Preview(1L, "PASTA", 3,
                List.of(new RescueDtos.Match("ann", 0.6, 1, 0, 0.5, 0.5, "you ordered this 3 times", true)),
                List.of(new RescueDtos.Excluded("bob", Exclusion.ALLERGEN, Exclusion.ALLERGEN.description()))));
        when(service.notify(1L)).thenReturn(new RescueDtos.NotifyResult(1L, 3, List.of("ann"), 1));

        mvc.perform(get("/api/rescue/offers/1/matches")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked[0].username").value("ann"))
                .andExpect(jsonPath("$.ranked[0].willAlert").value(true))
                .andExpect(jsonPath("$.excluded[0].reason").value("ALLERGEN"));
        mvc.perform(post("/api/rescue/offers/1/notify")).andExpect(status().isOk())
                .andExpect(jsonPath("$.alerted[0]").value("ann"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanPreviewAndNotify() throws Exception {
        mvc.perform(get("/api/rescue/offers/1/matches")).andExpect(status().isOk());
        mvc.perform(post("/api/rescue/offers/1/notify")).andExpect(status().isOk());
        verify(service).notify(1L);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void customerSeesOwnDealsButCannotPreviewOrNotify() throws Exception {
        when(service.deals("alice")).thenReturn(List.of());
        mvc.perform(get("/api/rescue/deals")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).deals("alice");
        mvc.perform(get("/api/rescue/offers/1/matches")).andExpect(status().isForbidden());
        mvc.perform(post("/api/rescue/offers/1/notify")).andExpect(status().isForbidden());
        verify(service, never()).notify(any());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffHaveNoDealsPage() throws Exception {
        mvc.perform(get("/api/rescue/deals")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driverCannotUseRescueMatching() throws Exception {
        mvc.perform(get("/api/rescue/deals")).andExpect(status().isForbidden());
        mvc.perform(get("/api/rescue/offers/1/matches")).andExpect(status().isForbidden());
        mvc.perform(post("/api/rescue/offers/1/notify")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void unknownOfferIsNotFound() throws Exception {
        when(service.preview(9L)).thenThrow(new ResourceNotFoundException("Surplus offer not found."));
        mvc.perform(get("/api/rescue/offers/9/matches")).andExpect(status().isNotFound());
    }
}
