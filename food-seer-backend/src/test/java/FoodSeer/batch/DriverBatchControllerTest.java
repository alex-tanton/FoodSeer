package FoodSeer.batch;

import FoodSeer.config.SpringSecurityConfig;
import FoodSeer.repositories.UserRepository;
import FoodSeer.security.CustomUserDetailsService;
import FoodSeer.security.DriverStatsAccess;
import FoodSeer.security.JwtAuthenticationFilter;
import FoodSeer.security.JwtTokenProvider;
import FoodSeer.service.UserService;
import FoodSeer.service.impl.JwtAccessDeniedHandler;
import FoodSeer.service.impl.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DriverBatchController.class, properties = {
        "app.jwt-secret=Zm9vZHNlZXItdGVzdC1vbmx5LXNpZ25pbmcta2V5LTMyaXRlbXM=",
        "app.jwt-expiration-milliseconds=60000"
})
@Import({SpringSecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        CustomUserDetailsService.class, DriverStatsAccess.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class DriverBatchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserRepository users;

    @MockBean
    private UserService userService;

    @MockBean
    private DriverBatchService driverBatchService;

    @Test
    @WithMockUser(username = "driver_bob", roles = "DRIVER")
    void driverCanGetBatchSuggestions() throws Exception {
        BatchDtos.BatchDto batchDto = new BatchDtos.BatchDto();
        batchDto.setId(10L);
        batchDto.setBatchCode("BATCH-TEST01");
        batchDto.setStatus("PROPOSED");
        batchDto.setTotalPayout(BigDecimal.valueOf(11.50));

        when(driverBatchService.getSuggestedBatches()).thenReturn(List.of(batchDto));

        mvc.perform(get("/api/driver/batches/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].batchCode").value("BATCH-TEST01"))
                .andExpect(jsonPath("$[0].status").value("PROPOSED"));
    }

    @Test
    @WithMockUser(username = "driver_bob", roles = "DRIVER")
    void driverCanClaimBatch() throws Exception {
        BatchDtos.BatchDto batchDto = new BatchDtos.BatchDto();
        batchDto.setId(10L);
        batchDto.setBatchCode("BATCH-TEST01");
        batchDto.setStatus("CLAIMED");
        batchDto.setDriverUsername("driver_bob");

        when(driverBatchService.claimBatch(10L)).thenReturn(batchDto);

        mvc.perform(post("/api/driver/batches/10/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andExpect(jsonPath("$.driverUsername").value("driver_bob"));
    }

    @Test
    @WithMockUser(username = "driver_bob", roles = "DRIVER")
    void driverCanAdvanceBatchStep() throws Exception {
        BatchDtos.BatchStepResponseDto stepResp = new BatchDtos.BatchStepResponseDto();
        stepResp.setBatchId(10L);
        stepResp.setCurrentStopIndex(1);
        stepResp.setTotalStops(4);
        stepResp.setStatus("IN_TRANSIT");
        stepResp.setMessage("Picked up Order #101");
        stepResp.setBatchCompleted(false);

        when(driverBatchService.advanceBatchStep(10L)).thenReturn(stepResp);

        mvc.perform(post("/api/driver/batches/10/step"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(10))
                .andExpect(jsonPath("$.currentStopIndex").value(1))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.message").value("Picked up Order #101"));
    }

    @Test
    @WithMockUser(username = "driver_bob", roles = "DRIVER")
    void driverCanGetActiveBatch() throws Exception {
        BatchDtos.BatchDto batchDto = new BatchDtos.BatchDto();
        batchDto.setId(10L);
        batchDto.setStatus("IN_TRANSIT");
        batchDto.setDriverUsername("driver_bob");

        when(driverBatchService.getActiveBatchForDriver("driver_bob")).thenReturn(batchDto);

        mvc.perform(get("/api/driver/batches/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    @WithMockUser(username = "driver_bob", roles = "DRIVER")
    void driverCanGetBatchById() throws Exception {
        BatchDtos.BatchDto batchDto = new BatchDtos.BatchDto();
        batchDto.setId(10L);
        batchDto.setStatus("PROPOSED");

        when(driverBatchService.getBatchById(10L)).thenReturn(batchDto);

        mvc.perform(get("/api/driver/batches/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(username = "regular_customer", roles = "CUSTOMER")
    void customerIsForbiddenFromDriverBatchEndpoints() throws Exception {
        mvc.perform(get("/api/driver/batches/suggestions"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/driver/batches/10/claim"))
                .andExpect(status().isForbidden());
    }
}
