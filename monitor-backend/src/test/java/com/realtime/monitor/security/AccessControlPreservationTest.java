package com.realtime.monitor.security;

import com.realtime.monitor.repository.DataSourceRepository;
import com.realtime.monitor.service.CdcEventsService;
import com.realtime.monitor.service.CdcStatsService;
import com.realtime.monitor.service.FlinkService;
import com.realtime.monitor.service.OutputFileService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preservation Property Tests — Legitimate Request Behavior
 *
 * These tests verify that legitimate requests continue to work correctly
 * on UNFIXED code. They MUST PASS on unfixed code — passing confirms the
 * baseline behavior to preserve after the fix is applied.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessControlPreservationTest {

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("JWT_SECRET", "test-secret-key-for-unit-tests-only-32chars!!");
        System.setProperty("AES_ENCRYPTION_KEY", "test-aes-key-16b");
    }

    // Mock infrastructure beans that require real DB/Flink connections
    @MockBean
    DataSource dataSource;

    @MockBean
    DataSourceRepository dataSourceRepository;

    @MockBean
    FlinkService flinkService;

    @MockBean
    CdcEventsService cdcEventsService;

    @MockBean
    CdcStatsService cdcStatsService;

    @MockBean
    OutputFileService outputFileService;

    @Autowired
    private MockMvc mockMvc;

    // -------------------------------------------------------------------------
    // Admin-only endpoints — ROLE_ADMIN should NOT be blocked
    // -------------------------------------------------------------------------

    /**
     * Preservation: ROLE_ADMIN POST /api/admin/datasources/reencrypt → 200
     *
     * On unfixed code there is no @PreAuthorize, so ROLE_ADMIN (and any user)
     * can reach the endpoint. After the fix, ROLE_ADMIN must still get 200.
     *
     * Validates: Requirement 3.3
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN POST /api/admin/datasources/reencrypt should return 200")
    void admin_postReencrypt_shouldReturn200() throws Exception {
        when(dataSourceRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/admin/datasources/reencrypt"))
                .andExpect(status().isOk()); // 200
    }

    /**
     * Preservation: ROLE_ADMIN GET /api/system/info → 200
     *
     * On unfixed code there is no @PreAuthorize, so ROLE_ADMIN can reach it.
     * After the fix, ROLE_ADMIN must still get 200.
     *
     * Validates: Requirement 3.6
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN GET /api/system/info should return 200")
    void admin_getSystemInfo_shouldReturn200() throws Exception {
        when(flinkService.isFlinkHealthy()).thenReturn(true);
        when(outputFileService.isOutputDirExists()).thenReturn(true);

        mockMvc.perform(get("/api/system/info"))
                .andExpect(status().isOk()); // 200
    }

    // -------------------------------------------------------------------------
    // General authenticated endpoints — should NOT be blocked
    // -------------------------------------------------------------------------

    /**
     * Preservation: authenticated GET /api/auth/me → 200
     *
     * On unfixed code, /api/auth/** is permitAll() so any request reaches the
     * controller. With @WithMockUser the authentication is set, so the controller
     * returns the user profile with 200.
     *
     * Validates: Requirement 3.5
     */
    @Test
    @WithMockUser
    @DisplayName("Authenticated GET /api/auth/me should return 200")
    void authenticated_getAuthMe_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk()); // 200
    }

    /**
     * Preservation: authenticated GET /api/cdc/events/files/content with valid path → 200
     *
     * On unfixed code there is no path validation, so the request reaches the
     * service. The mocked service returns a result map, so the controller returns 200.
     *
     * Validates: Requirement 3.4
     */
    @Test
    @WithMockUser
    @DisplayName("Authenticated GET /api/cdc/events/files/content with valid path should return 200")
    void authenticated_getFileContent_validPath_shouldReturn200() throws Exception {
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("content", "log line 1\nlog line 2");
        mockResult.put("total", 2);
        when(cdcEventsService.getFileContent(anyString(), anyInt(), anyInt()))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/cdc/events/files/content")
                        .param("path", "test.log"))
                .andExpect(status().isOk()); // 200
    }

    /**
     * Preservation: authenticated POST /api/jobs/{jobId}/stop with valid savepoint dir → 200
     *
     * On unfixed code there is no targetDirectory validation, so the request
     * reaches the service. The mocked service returns a result map, so the
     * controller returns 200.
     *
     * Validates: Requirement 3.7
     */
    @Test
    @WithMockUser
    @DisplayName("Authenticated POST /api/jobs/{jobId}/stop with valid savepoint dir should return 200")
    void authenticated_stopJob_validSavepointDir_shouldReturn200() throws Exception {
        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("savepoint-path", "file:///opt/flink/savepoints/sp1");
        when(flinkService.stopJobWithSavepoint(anyString(), anyString()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/jobs/test-job-id/stop")
                        .param("targetDirectory", "file:///opt/flink/savepoints/sp1"))
                .andExpect(status().isOk()); // 200
    }

    // -------------------------------------------------------------------------
    // Public endpoints — no authentication required
    // -------------------------------------------------------------------------

    /**
     * Preservation: unauthenticated POST /api/auth/login → NOT 403/404/500
     *
     * The login endpoint must remain publicly accessible. With wrong credentials
     * the AuthController catches the exception and returns ApiResponse.error(...)
     * with HTTP 200. It must NOT return 403 Forbidden (blocked by security).
     *
     * Validates: Requirement 3.1, 3.8
     */
    @Test
    @DisplayName("Unauthenticated POST /api/auth/login should not return 403/404/500")
    void unauthenticated_postLogin_shouldNotBeForbiddenOrError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().is(not(403)))
                .andExpect(status().is(not(404)))
                .andExpect(status().is(not(500)));
    }

    /**
     * Preservation: unauthenticated GET /actuator/health → accessible (not blocked by security)
     *
     * The health endpoint must remain publicly accessible without authentication.
     * It may return 200 (healthy) or 503 (unhealthy/DOWN) depending on the health
     * indicators, but it must NOT return 401/403 (security block).
     *
     * Validates: Requirement 3.9
     */
    @Test
    @DisplayName("Unauthenticated GET /actuator/health should be accessible (not blocked by security)")
    void unauthenticated_getActuatorHealth_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(not(401)))
                .andExpect(status().is(not(403)));
    }
}
