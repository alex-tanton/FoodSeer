package FoodSeer.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import FoodSeer.config.SpringSecurityConfig;
import FoodSeer.exception.ResourceNotFoundException;
import FoodSeer.repositories.UserRepository;
import FoodSeer.security.*;
import FoodSeer.service.SurplusService;
import FoodSeer.service.impl.JwtAccessDeniedHandler;
import FoodSeer.service.impl.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SurplusController.class, properties = {
    "app.jwt-secret=Zm9vZHNlZXItdGVzdC1vbmx5LXNpZ25pbmcta2V5LTMyaXRlbXM=",
    "app.jwt-expiration-milliseconds=60000"
})
@Import({SpringSecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
    CustomUserDetailsService.class, DriverStatsAccess.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SurplusControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserRepository users;
    @MockBean private SurplusService service;

    private static final String OFFER = """
            {"foodId":1,"ingredients":"Milk","discountedPrice":2.50,"portions":5,
             "pickupLocation":"Desk","pickupStart":"2026-11-16T18:00:00Z",
             "pickupEnd":"2026-11-16T20:00:00Z","expiresAt":"2026-11-16T19:00:00Z"}
            """;
    private static final String RESERVE = "{\"quantity\":2,\"pickupAt\":\"2026-11-16T19:00:00Z\"}";

    @ParameterizedTest
    @ValueSource(strings = {"/api/surplus/offers", "/api/surplus/offers/manage", "/api/surplus/reservations"})
    void anonymousCannotRead(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/surplus/offers", "/api/surplus/offers/1/reservations",
            "/api/surplus/reservations/1/cancel", "/api/surplus/reservations/1/collect"})
    void anonymousCannotWrite(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(OFFER))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void customersBrowseAndSeeOnlyTheirReservations() throws Exception {
        when(service.browse(false)).thenReturn(List.of());
        when(service.listReservations("alice", false)).thenReturn(List.of());
        mvc.perform(get("/api/surplus/offers")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/surplus/reservations")).andExpect(status().isOk());
        verify(service).listReservations("alice", false);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void customerCanReserveAndCancelUsingAuthenticatedIdentity() throws Exception {
        mvc.perform(post("/api/surplus/offers/1/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(RESERVE)).andExpect(status().isCreated());
        verify(service).reserve(eq(1L), eq("alice"), argThat(request -> request.quantity() == 2));
        mvc.perform(post("/api/surplus/reservations/2/cancel")).andExpect(status().isOk());
        verify(service).cancel(2L, "alice");
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotPublishManageOrCollect() throws Exception {
        mvc.perform(post("/api/surplus/offers").contentType(MediaType.APPLICATION_JSON).content(OFFER))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/surplus/offers/manage")).andExpect(status().isForbidden());
        mvc.perform(post("/api/surplus/reservations/1/collect")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCanPublishManageAndCollectButCannotReserveOrCancel() throws Exception {
        mvc.perform(post("/api/surplus/offers").contentType(MediaType.APPLICATION_JSON).content(OFFER))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/surplus/offers/manage")).andExpect(status().isOk());
        verify(service).browse(true);
        mvc.perform(get("/api/surplus/reservations")).andExpect(status().isOk());
        verify(service).listReservations("staff", true);
        mvc.perform(post("/api/surplus/reservations/1/collect")).andExpect(status().isOk());
        mvc.perform(post("/api/surplus/offers/1/reservations").contentType(MediaType.APPLICATION_JSON).content(RESERVE))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/surplus/reservations/1/cancel")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanManageMarketplace() throws Exception {
        mvc.perform(post("/api/surplus/offers").contentType(MediaType.APPLICATION_JSON).content(OFFER))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/surplus/reservations")).andExpect(status().isOk());
        verify(service).listReservations("admin", true);
        mvc.perform(post("/api/surplus/reservations/1/collect")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driverCannotAccessMarketplace() throws Exception {
        mvc.perform(get("/api/surplus/offers")).andExpect(status().isForbidden());
        mvc.perform(get("/api/surplus/reservations")).andExpect(status().isForbidden());
        mvc.perform(post("/api/surplus/offers").contentType(MediaType.APPLICATION_JSON).content(OFFER))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/surplus/offers/1/reservations").contentType(MediaType.APPLICATION_JSON).content(RESERVE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void errorsHaveMeaningfulHttpStatuses() throws Exception {
        when(service.reserve(eq(1L), eq("alice"), any()))
                .thenThrow(new IllegalArgumentException("Choose a valid pickup time."))
                .thenThrow(new IllegalStateException("Sold out."))
                .thenThrow(new ResourceNotFoundException("Offer not found."));
        mvc.perform(post("/api/surplus/offers/1/reservations").contentType(MediaType.APPLICATION_JSON).content(RESERVE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Choose a valid pickup time."));
        mvc.perform(post("/api/surplus/offers/1/reservations").contentType(MediaType.APPLICATION_JSON).content(RESERVE))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Sold out."));
        mvc.perform(post("/api/surplus/offers/1/reservations").contentType(MediaType.APPLICATION_JSON).content(RESERVE))
                .andExpect(status().isNotFound());
        when(service.cancel(2L, "alice")).thenThrow(new AccessDeniedException("Not yours."));
        mvc.perform(post("/api/surplus/reservations/2/cancel")).andExpect(status().isForbidden());
    }
}
