package FoodSeer.mealplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

/** Runs the real security filter chain, like the other controller security tests. */
@WebMvcTest(controllers = MealPlanController.class, properties = {
    "app.jwt-secret=Zm9vZHNlZXItdGVzdC1vbmx5LXNpZ25pbmcta2V5LTMyaXRlbXM=",
    "app.jwt-expiration-milliseconds=60000"
})
@Import({SpringSecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
    CustomUserDetailsService.class, DriverStatsAccess.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class MealPlanControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserRepository users;
    @MockBean private MealPlanService service;

    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private static PlanDtos.Plan plan() {
        final PlanDtos.Meal lunch = new PlanDtos.Meal("LUNCH", 700, List.of(new PlanDtos.Dish(1L, "BURRITO", 650, 11,
                List.of("WHEAT"))), 650, 11, false, null, true);
        return new PlanDtos.Plan(5L, DAY, 1800, 200, 2000, 150, 11, true, 0, "no salads", "avoid salads", "OFF",
                List.of("Monday is 68% under your calorie target."),
                List.of(new PlanDtos.Day(DAY, "Monday", 2000, 650, 11, false, 0, List.of(lunch))));
    }

    @Test
    void customerCreatesAPlan() throws Exception {
        when(service.create(eq("maya"), any())).thenReturn(plan());
        mvc.perform(post("/api/meal-plans").with(user("maya").roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":5,\"weeklyBudget\":150,\"request\":\"no salads\",\"useAi\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.understood").value("avoid salads"))
                .andExpect(jsonPath("$.days[0].meals[0].dishes[0].name").value("BURRITO"))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-23"));
        verify(service).create("maya", new PlanDtos.CreateRequest(5, 150, "no salads", true, null));
    }

    @Test
    void customerReadsSwapsOrdersAndDeletes() throws Exception {
        when(service.current("maya")).thenReturn(plan());
        when(service.swap(eq("maya"), any())).thenReturn(plan());
        when(service.order(eq("maya"), any())).thenReturn(plan());
        mvc.perform(get("/api/meal-plans/current").with(user("maya").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        mvc.perform(post("/api/meal-plans/current/swap").with(user("maya").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-23\",\"meal\":\"LUNCH\"}"))
                .andExpect(status().isOk());
        verify(service).swap("maya", new PlanDtos.SlotRequest(DAY, "LUNCH"));
        mvc.perform(post("/api/meal-plans/current/order").with(user("maya").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-23\",\"meal\":\"LUNCH\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/meal-plans/current").with(user("maya").roles("CUSTOMER")))
                .andExpect(status().isNoContent());
        verify(service).delete("maya");
    }

    @Test
    void errorsComeBackWithTheirMessage() throws Exception {
        when(service.current("maya")).thenThrow(new ResourceNotFoundException("You have no meal plan yet."));
        when(service.create(eq("maya"), any()))
                .thenThrow(new IllegalStateException("Set a daily calorie goal on Personal Diet first."));
        when(service.order(eq("maya"), any())).thenThrow(new IllegalArgumentException("Only today's meals can be ordered."));
        mvc.perform(get("/api/meal-plans/current").with(user("maya").roles("CUSTOMER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("You have no meal plan yet."));
        mvc.perform(post("/api/meal-plans").with(user("maya").roles("CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Set a daily calorie goal on Personal Diet first."));
        mvc.perform(post("/api/meal-plans/current/order").with(user("maya").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-24\",\"meal\":\"LUNCH\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "ADMIN", "DRIVER"})
    void onlyCustomersHavePlans(final String role) throws Exception {
        mvc.perform(get("/api/meal-plans/current").with(user("u").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/meal-plans/current")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
