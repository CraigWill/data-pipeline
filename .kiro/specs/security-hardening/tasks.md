# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Route Access Control Defects
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bugs exist
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate each access control defect
  - **Scoped PBT Approach**: Scope to the five concrete defective request patterns (deterministic bugs)
  - Create `monitor-backend/src/test/java/com/realtime/monitor/security/AccessControlBugConditionTest.java`
  - Test 1 — unauthenticated `GET /api/auth/me` → assert HTTP 401 (currently returns 200 due to `permitAll()` wildcard)
  - Test 2 — non-admin authenticated `POST /api/admin/datasources/reencrypt` → assert HTTP 403 (currently returns 200)
  - Test 3 — non-admin authenticated `GET /api/system/info` → assert HTTP 403 (currently returns 200)
  - Test 4 — authenticated `GET /api/cdc/events/files/content?path=../../../etc/passwd` → assert HTTP 400 (currently passes path through)
  - Test 5 — authenticated `POST /api/jobs/{jobId}/stop?targetDirectory=/tmp/evil` → assert HTTP 400 (currently passes path through)
  - Use `@SpringBootTest` + `MockMvc` with `@WithMockUser` for role-based cases
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct — it proves the bugs exist)
  - Document counterexamples found (e.g., "GET /api/auth/me returns 200 for unauthenticated request")
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.8, 1.9, 1.10, 1.11, 1.12_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Legitimate Request Behavior
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: authenticated `GET /api/auth/me` returns 200 with user profile on unfixed code
  - Observe: ROLE_ADMIN `POST /api/admin/datasources/reencrypt` returns 200 on unfixed code
  - Observe: ROLE_ADMIN `GET /api/system/info` returns 200 with JVM info on unfixed code
  - Observe: authenticated `GET /api/cdc/events/files/content?path=<valid>` returns 200 on unfixed code
  - Observe: unauthenticated `POST /api/auth/login` returns 200 with JWT on unfixed code
  - Observe: unauthenticated `GET /actuator/health` returns 200 on unfixed code
  - Create `monitor-backend/src/test/java/com/realtime/monitor/security/AccessControlPreservationTest.java`
  - Write property-based tests using `@WithMockUser(roles = "ADMIN")` for admin-only endpoints
  - Write property-based tests using `@WithMockUser` for general authenticated endpoints
  - Write tests for public endpoints (login, logout, actuator/health) without authentication
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

- [x] 3. Fix route access control issues

  - [x] 3.1 Fix SecurityConfig: split `/api/auth/**` and change `anyRequest()`
    - In `SecurityConfig.java`, replace `.antMatchers("/api/auth/**").permitAll()` with `.antMatchers("/api/auth/login", "/api/auth/logout").permitAll()`
    - Replace `.anyRequest().permitAll()` with `.anyRequest().authenticated()`
    - This ensures `/api/auth/me` requires authentication and non-API paths are not publicly accessible
    - _Bug_Condition: `isAccessControlBuggy(request)` where path = "/api/auth/me" AND NOT isAuthenticated(principal)_
    - _Expected_Behavior: unauthenticated GET /api/auth/me → HTTP 401_
    - _Preservation: authenticated GET /api/auth/me → 200; POST /api/auth/login → 200 (no auth); GET /actuator/health → 200 (no auth)_
    - _Requirements: 2.9, 2.12, 3.1, 3.2, 3.5, 3.8, 3.9_

  - [x] 3.2 Add `@PreAuthorize("hasRole('ADMIN')")` to AdminController
    - Add `@PreAuthorize("hasRole('ADMIN')")` annotation to `reencryptDataSourcePasswords()` in `AdminController.java`
    - Add `import org.springframework.security.access.prepost.PreAuthorize;`
    - _Bug_Condition: `isAccessControlBuggy(request)` where path = "/api/admin/datasources/reencrypt" AND NOT hasRole(principal, "ROLE_ADMIN")_
    - _Expected_Behavior: non-admin POST /api/admin/datasources/reencrypt → HTTP 403_
    - _Preservation: ROLE_ADMIN POST /api/admin/datasources/reencrypt → 200 with re-encryption result_
    - _Requirements: 2.7, 3.3_

  - [x] 3.3 Add `@PreAuthorize("hasRole('ADMIN')")` to HealthController.getSystemInfo
    - Add `@PreAuthorize("hasRole('ADMIN')")` annotation to `getSystemInfo()` in `HealthController.java`
    - Add `import org.springframework.security.access.prepost.PreAuthorize;`
    - _Bug_Condition: `isAccessControlBuggy(request)` where path = "/api/system/info" AND NOT hasRole(principal, "ROLE_ADMIN")_
    - _Expected_Behavior: non-admin GET /api/system/info → HTTP 403_
    - _Preservation: ROLE_ADMIN GET /api/system/info → 200 with JVM/Flink info_
    - _Requirements: 2.10, 3.6_

  - [x] 3.4 Add path traversal validation in CdcEventsController
    - Add `BASE_DIR` constant (read from system property `cdc.events.base-dir`, default `/opt/flink/output`)
    - Add private `validatePath(String path)` method using `Paths.get(path).normalize()` and `startsWith(base)` check
    - Change `getFileContent()` return type to `ResponseEntity<ApiResponse<Map<String, Object>>>`
    - Call `validatePath(path)` before delegating to service; catch `IllegalArgumentException` and return `ResponseEntity.badRequest().body(ApiResponse.error(...))`
    - _Bug_Condition: `isAccessControlBuggy(request)` where path MATCHES "/api/cdc/events/files/content" AND NOT isSafePath(param("path"))_
    - _Expected_Behavior: path traversal attempt → HTTP 400_
    - _Preservation: valid path within base dir → 200 with paginated file content_
    - _Requirements: 2.8, 3.4_

  - [x] 3.5 Add savepoint directory validation in JobController
    - Add `SAVEPOINT_BASE` constant (read from system property `flink.savepoint.base-dir`, default `file:///opt/flink/savepoints`)
    - Add private `validateSavepointDirectory(String dir)` method checking `dir.startsWith(SAVEPOINT_BASE)`
    - Change `stopJobWithSavepoint()` return type to `ResponseEntity<ApiResponse<Map<String, Object>>>`
    - Call `validateSavepointDirectory(targetDirectory)` before delegating; catch `IllegalArgumentException` and return `ResponseEntity.badRequest().body(ApiResponse.error(...))`
    - _Bug_Condition: `isAccessControlBuggy(request)` where path MATCHES "/api/jobs/.*/stop" AND NOT isSafePath(param("targetDirectory"))_
    - _Expected_Behavior: invalid targetDirectory → HTTP 400_
    - _Preservation: valid targetDirectory under SAVEPOINT_BASE → 200 with savepoint result_
    - _Requirements: 2.11, 3.7_

  - [x] 3.6 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Route Access Control Defects
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied
    - Run `AccessControlBugConditionTest` from step 1
    - **EXPECTED OUTCOME**: All 5 test cases PASS (confirms all route bugs are fixed)
    - _Requirements: 2.7, 2.8, 2.9, 2.10, 2.11, 2.12_

  - [x] 3.7 Verify preservation tests still pass
    - **Property 2: Preservation** - Legitimate Request Behavior
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run `AccessControlPreservationTest` from step 2
    - **EXPECTED OUTCOME**: All preservation tests PASS (confirms no regressions)
    - Confirm all tests still pass after route fixes (no regressions)

- [x] 4. Upgrade low-risk dependencies (ojdbc8, Jackson, Log4j2)

  - [x] 4.1 Upgrade Oracle JDBC ojdbc8 21.1.0.0 → 21.9.0.0
    - In `pom.xml` (parent), update `dependencyManagement` entry for `com.oracle.database.jdbc:ojdbc8` to version `21.9.0.0`
    - No code changes required
    - _Requirements: 2.3_

  - [x] 4.2 Upgrade Jackson 2.13.5 → 2.17.x
    - In `pom.xml` (parent), update `<jackson.version>2.13.5</jackson.version>` to `<jackson.version>2.17.3</jackson.version>`
    - No code changes required; 2.17.x is API-compatible with 2.13.x
    - _Requirements: 2.4_

  - [x] 4.3 Upgrade Log4j2 2.20.0 → 2.23.x
    - In `pom.xml` (parent), update `<log4j.version>2.20.0</log4j.version>` to `<log4j.version>2.23.1</log4j.version>`
    - No code changes required
    - _Requirements: 2.5_

  - [x] 4.4 Verify build compiles and tests pass after low-risk dependency upgrades
    - Run `mvn clean test -pl monitor-backend` to confirm no compilation errors or test failures
    - Confirm `AccessControlBugConditionTest` and `AccessControlPreservationTest` still pass

- [x] 5. Upgrade jjwt 0.11.5 → 0.12.x and migrate JwtTokenProvider API

  - [x] 5.1 Update jjwt versions in monitor-backend/pom.xml
    - Update all three jjwt artifacts (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) from `0.11.5` to `0.12.6`
    - _Requirements: 2.6_

  - [x] 5.2 Migrate JwtTokenProvider to jjwt 0.12.x API
    - In `generateToken()`: replace `.setSubject(username)` → `.subject(username)`, `.setIssuedAt(now)` → `.issuedAt(now)`, `.setExpiration(expiryDate)` → `.expiration(expiryDate)`
    - In `getUsernameFromToken()`: replace `Jwts.parserBuilder()` → `Jwts.parser()`, `.setSigningKey(key)` → `.verifyWith(key)`, `.parseClaimsJws(token)` → `.parseSignedClaims(token)`, `.getBody()` → `.getPayload()`
    - In `validateToken()`: replace `Jwts.parserBuilder()` → `Jwts.parser()`, `.setSigningKey(key)` → `.verifyWith(key)`, `.parseClaimsJws(token)` → `.parseSignedClaims(token)`; consolidate catch blocks to `JwtException | IllegalArgumentException`
    - _Requirements: 2.6_

  - [x] 5.3 Verify JWT functionality after migration
    - Run `mvn clean test -pl monitor-backend` to confirm no compilation errors
    - Confirm token generation, parsing, and validation work correctly
    - Confirm `AccessControlPreservationTest` login test still passes (JWT is issued and accepted)

- [x] 6. Upgrade Spring Boot 2.7.18 → 3.3.x and migrate javax.* to jakarta.*

  - [x] 6.1 Update spring.boot.version in parent pom.xml
    - In `pom.xml` (parent), update `<spring.boot.version>2.7.18</spring.boot.version>` to `<spring.boot.version>3.3.5</spring.boot.version>`
    - _Requirements: 2.1, 2.2_

  - [x] 6.2 Migrate javax.servlet.* imports to jakarta.servlet.* in SecurityConfig
    - In `SecurityConfig.java`, replace all `import javax.servlet.*` with `import jakarta.servlet.*`
    - Replace `@EnableGlobalMethodSecurity(prePostEnabled = true)` with `@EnableMethodSecurity`
    - Migrate Security DSL from chained `.and()` style to lambda DSL:
      - `http.csrf().disable()` → `http.csrf(csrf -> csrf.disable())`
      - `http.cors().and()` → `http.cors(Customizer.withDefaults())`
      - `http.sessionManagement().sessionCreationPolicy(...)` → `http.sessionManagement(sm -> sm.sessionCreationPolicy(...))`
      - `http.authorizeRequests().antMatchers(...)` → `http.authorizeHttpRequests(auth -> auth.requestMatchers(...)...)`
      - `http.exceptionHandling().authenticationEntryPoint(...)` → `http.exceptionHandling(ex -> ex.authenticationEntryPoint(...))`
    - Apply the route fixes from task 3.1 in the new lambda DSL form (login/logout only, anyRequest().authenticated())
    - _Requirements: 2.1, 2.2_

  - [x] 6.3 Scan and migrate any remaining javax.* imports across the codebase
    - Search for any remaining `import javax.servlet` or `import javax.persistence` in `monitor-backend/src/main/java`
    - Replace with corresponding `jakarta.*` equivalents
    - Common replacements: `javax.servlet.Filter` → `jakarta.servlet.Filter`, `javax.servlet.http.HttpServletRequest` → `jakarta.servlet.http.HttpServletRequest`
    - _Requirements: 2.1_

  - [x] 6.4 Verify full build and all tests pass after Spring Boot 3.x migration
    - Run `mvn clean test -pl monitor-backend`
    - Confirm no compilation errors from namespace migration
    - Confirm `AccessControlBugConditionTest` passes (all 5 bug condition cases return correct status codes)
    - Confirm `AccessControlPreservationTest` passes (all preservation cases unchanged)

- [x] 7. Checkpoint — Ensure all tests pass
  - Run `mvn clean test -pl monitor-backend` and confirm all tests pass
  - Verify `AccessControlBugConditionTest` — all 5 property cases pass (bugs fixed)
  - Verify `AccessControlPreservationTest` — all preservation cases pass (no regressions)
  - Confirm application starts successfully with `mvn spring-boot:run -pl monitor-backend` (or Docker build)
  - Ask the user if any questions arise
