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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug Condition Exploration Tests — Route Access Control Defects
 *
 * These tests encode the EXPECTED (fixed) behavior.
 * On UNFIXED code they are expected to FAIL — failure confirms the bugs exist.
 *
 * Validates: Requirements 1.8, 1.9, 1.10, 1.11, 1.12
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessControlBugConditionTest {

    /**
     * Set required env-var substitutes as system properties before the Spring context loads.
     * DataSourceConfig reads DATABASE_PASSWORD via System.getenv(), so we mock the DataSource
     * bean directly. JWT_SECRET and AES_ENCRYPTION_KEY are set here so Spring can resolve
     * the ${JWT_SECRET} and ${AES_ENCRYPTION_KEY} placeholders in application.yml.
     */
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

    /**
     * Test 1 — Bug condition: unauthenticated GET /api/auth/me
     *
     * Current (buggy): SecurityConfig has .antMatchers("/api/auth/**").permitAll()
     * which exposes /api/auth/me without authentication → returns 200.
     * Expected (fixed): should return 401 Unauthorized.
     *
     * Validates: Requirement 1.10
     */
    @Test
    @DisplayName("Unauthenticated GET /api/auth/me should return 401 (currently returns 200 — bug)")
    void unauthenticated_getAuthMe_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized()); // 401
    }

    /**
     * Test 2 — Bug condition: non-admin POST /api/admin/datasources/reencrypt
     *
     * Current (buggy): no @PreAuthorize on the endpoint → any authenticated user
     * can trigger the sensitive re-encryption operation → returns 200.
     * Expected (fixed): should return 403 Forbidden for non-admin users.
     *
     * Validates: Requirement 1.8
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Non-admin POST /api/admin/datasources/reencrypt should return 403 (currently returns 200 — bug)")
    void nonAdmin_postReencrypt_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/admin/datasources/reencrypt"))
                .andExpect(status().isForbidden()); // 403
    }

    /**
     * Test 3 — Bug condition: non-admin GET /api/system/info
     *
     * Current (buggy): no @PreAuthorize on the endpoint → any authenticated user
     * can read sensitive JVM/Flink info → returns 200.
     * Expected (fixed): should return 403 Forbidden for non-admin users.
     *
     * Validates: Requirement 1.11
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Non-admin GET /api/system/info should return 403 (currently returns 200 — bug)")
    void nonAdmin_getSystemInfo_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/system/info"))
                .andExpect(status().isForbidden()); // 403
    }

    /**
     * Test 4 — Bug condition: path traversal in GET /api/cdc/events/files/content
     *
     * Current (buggy): path parameter is passed directly to the service with no
     * validation → attacker can read arbitrary files → returns 200 (or 500 if file
     * doesn't exist, but never 400).
     * Expected (fixed): should return 400 Bad Request for traversal paths.
     *
     * Validates: Requirement 1.9
     */
    @Test
    @WithMockUser
    @DisplayName("Authenticated GET /api/cdc/events/files/content?path=../../../etc/passwd should return 400 (currently passes through — bug)")
    void authenticated_getFileContent_pathTraversal_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/cdc/events/files/content")
                        .param("path", "../../../etc/passwd"))
                .andExpect(status().isBadRequest()); // 400
    }

    /**
     * Test 5 — Bug condition: unsafe targetDirectory in POST /api/jobs/{jobId}/stop
     *
     * Current (buggy): targetDirectory parameter is passed directly to the Flink
     * savepoint operation with no validation → attacker can direct savepoints to
     * arbitrary paths → returns 200 (or 500 on Flink error, but never 400).
     * Expected (fixed): should return 400 Bad Request for paths outside the
     * allowed savepoint base directory.
     *
     * Validates: Requirement 1.12
     */
    @Test
    @WithMockUser
    @DisplayName("Authenticated POST /api/jobs/{jobId}/stop?targetDirectory=/tmp/evil should return 400 (currently passes through — bug)")
    void authenticated_stopJob_unsafeTargetDirectory_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/jobs/test-job-id/stop")
                        .param("targetDirectory", "/tmp/evil"))
                .andExpect(status().isBadRequest()); // 400
    }
}
