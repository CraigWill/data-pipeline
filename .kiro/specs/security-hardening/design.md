# Design Document: security-hardening

## Overview

This document describes the fix approach for the security vulnerabilities identified in the 2026-04-16 audit. The fixes fall into two independent tracks:

1. **Route Access Control Fixes** — low-risk, no migration required, applied first
2. **Dependency Upgrades** — higher-risk due to Spring Boot 3.x namespace migration and jjwt API changes, applied second

The bug condition methodology is used throughout: `C(X)` identifies the defective input/state, `P(result)` defines the required post-fix behavior, and `¬C(X)` defines the non-buggy cases whose behavior must be preserved.

---

## Bug Condition

```pascal
// Route Access Control Bug Condition
FUNCTION isAccessControlBuggy(request)
  INPUT: HTTP request with path, method, and principal
  OUTPUT: boolean
  RETURN (path = "/api/admin/datasources/reencrypt"
            AND method = "POST"
            AND NOT hasRole(principal, "ROLE_ADMIN"))
      OR (path MATCHES "/api/cdc/events/files/content"
            AND NOT isSafePath(param("path")))
      OR (path = "/api/auth/me"
            AND NOT isAuthenticated(principal))
      OR (path = "/api/system/info"
            AND NOT hasRole(principal, "ROLE_ADMIN"))
      OR (path MATCHES "/api/jobs/.*/stop"
            AND NOT isSafePath(param("targetDirectory")))
      OR (path NOT MATCHES "^/api/.*" AND path NOT MATCHES "^/actuator/.*"
            AND NOT isAuthenticated(principal))
END FUNCTION

// Dependency Vulnerability Bug Condition
FUNCTION isDependencyVulnerable(component, version)
  INPUT: component name and version string
  OUTPUT: boolean
  RETURN (component = "spring-boot"     AND version < "3.0.0")
      OR (component = "ojdbc8"          AND version < "21.9.0.0")
      OR (component = "jackson-databind" AND version < "2.17.0")
      OR (component = "log4j2"          AND version < "2.23.0")
      OR (component = "jjwt"            AND version < "0.12.0")
END FUNCTION
```

---

## Expected Behavior

```pascal
// Fix Checking Property — route access control
FOR ALL request WHERE isAccessControlBuggy(request) DO
  response ← securedHandler'(request)
  ASSERT response.status IN {400, 401, 403}
END FOR

// Fix Checking Property — dependencies
FOR ALL component WHERE isDependencyVulnerable(component, currentVersion) DO
  ASSERT resolvedVersion(component) >= minimumSafeVersion(component)
END FOR

// Preservation Checking Property
FOR ALL request WHERE NOT isAccessControlBuggy(request) DO
  ASSERT securedHandler'(request) = securedHandler(request)
END FOR
```

---

## Preservation Requirements

The following behaviors MUST NOT change after the fix:

- `POST /api/auth/login` with valid credentials → returns JWT (no auth required)
- `POST /api/auth/logout` → clears security context (no auth required)
- `GET /api/auth/me` with valid JWT → returns user profile (auth required, was already working for authenticated users)
- `POST /api/admin/datasources/reencrypt` with ROLE_ADMIN JWT → executes re-encryption
- `GET /api/cdc/events/files/content?path=<valid-within-base-dir>` with valid JWT → returns paginated file content
- `GET /api/system/info` with ROLE_ADMIN JWT → returns system info
- `POST /api/jobs/{jobId}/stop?targetDirectory=<valid-path>` with valid JWT → triggers Flink savepoint
- `GET /actuator/health` → returns health status (no auth required)
- All other authenticated `/api/**` endpoints → continue to function as before

---

## Fix Approach

### Track 1: Route Access Control Fixes (Low Risk)

These changes are confined to `SecurityConfig.java`, `AdminController.java`, `HealthController.java`, `CdcEventsController.java`, and `JobController.java`. No migration is required.

#### 1.1 Split `/api/auth/**` wildcard in SecurityConfig

**Current (defective):**
```java
.antMatchers("/api/auth/**").permitAll()
```
This exposes `/api/auth/me` without authentication.

**Fix:**
```java
.antMatchers("/api/auth/login", "/api/auth/logout").permitAll()
```
Only login and logout are public. `/api/auth/me` falls through to `.antMatchers("/api/**").authenticated()`.

#### 1.2 Change `anyRequest().permitAll()` to `anyRequest().authenticated()`

**Current (defective):**
```java
.anyRequest().permitAll()
```

**Fix:**
```java
.anyRequest().authenticated()
```

#### 1.3 Add `@PreAuthorize("hasRole('ADMIN')")` to admin endpoints

**AdminController** — `reencryptDataSourcePasswords()`:
```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/datasources/reencrypt")
public ApiResponse<Map<String, Object>> reencryptDataSourcePasswords() { ... }
```

**HealthController** — `getSystemInfo()`:
```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/system/info")
public ApiResponse<Map<String, Object>> getSystemInfo() { ... }
```

`@EnableGlobalMethodSecurity(prePostEnabled = true)` is already present in `SecurityConfig`.

#### 1.4 Path traversal validation in CdcEventsController

**Current (defective):** `path` parameter is passed directly to `cdcEventsService.getFileContent(path, ...)` with no validation.

**Fix — add a private helper and call it before delegating:**
```java
private static final String BASE_DIR = System.getProperty("cdc.events.base-dir",
        "/opt/flink/output");

private void validatePath(String path) {
    try {
        Path requested = Paths.get(path).toRealPath();
        Path base = Paths.get(BASE_DIR).toRealPath();
        if (!requested.startsWith(base)) {
            throw new IllegalArgumentException("Path traversal detected: " + path);
        }
    } catch (IOException e) {
        throw new IllegalArgumentException("Invalid path: " + path);
    }
}
```

In `getFileContent()`:
```java
@GetMapping("/files/content")
public ApiResponse<Map<String, Object>> getFileContent(
        @RequestParam String path, ...) {
    try {
        validatePath(path);
        Map<String, Object> result = cdcEventsService.getFileContent(path, page, size);
        return ApiResponse.success(result);
    } catch (IllegalArgumentException e) {
        return ResponseEntity with HTTP 400;
    }
    ...
}
```

Return `ResponseEntity<ApiResponse<?>>` with `ResponseEntity.badRequest()` for invalid paths.

#### 1.5 Path validation in JobController

**Current (defective):** `targetDirectory` is passed directly to `flinkService.stopJobWithSavepoint(jobId, targetDirectory)`.

**Fix — validate against an allowlist prefix:**
```java
private static final String SAVEPOINT_BASE = System.getProperty("flink.savepoint.base-dir",
        "file:///opt/flink/savepoints");

private void validateSavepointDirectory(String dir) {
    if (dir == null || !dir.startsWith(SAVEPOINT_BASE)) {
        throw new IllegalArgumentException(
            "targetDirectory must be under " + SAVEPOINT_BASE);
    }
}
```

In `stopJobWithSavepoint()`:
```java
@PostMapping("/{jobId}/stop")
public ApiResponse<Map<String, Object>> stopJobWithSavepoint(
        @PathVariable String jobId,
        @RequestParam(defaultValue = "file:///opt/flink/savepoints") String targetDirectory) {
    try {
        validateSavepointDirectory(targetDirectory);
        ...
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest()...;
    }
}
```

---

### Track 2: Dependency Upgrades (Higher Risk)

#### 2.1 Spring Boot 2.7.18 → 3.3.x

**File:** `pom.xml`
```xml
<spring.boot.version>3.3.5</spring.boot.version>
```

Spring Boot 3.x uses Spring Framework 6.x and Jakarta EE 10. All `javax.*` imports must be replaced with `jakarta.*` across the codebase.

**Affected files and changes:**

| File | Change |
|------|--------|
| `SecurityConfig.java` | `javax.servlet.*` → `jakarta.servlet.*`; `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`; fluent API updated to lambda DSL |
| `JwtTokenProvider.java` | No servlet imports, no change needed |
| Any `Filter` / `OncePerRequestFilter` | `javax.servlet.*` → `jakarta.servlet.*` |

Spring Boot 3.x also deprecates the chained `.and()` fluent API in favor of lambda DSL:

**Current (Spring Boot 2.x style):**
```java
http
    .csrf().disable()
    .cors().and()
    .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    .and()
    .authorizeRequests()
        .antMatchers(...).permitAll()
        ...
```

**Fix (Spring Boot 3.x lambda DSL):**
```java
http
    .csrf(csrf -> csrf.disable())
    .cors(Customizer.withDefaults())
    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/api/**").authenticated()
        .anyRequest().authenticated()
    );
```

Note: `antMatchers` → `requestMatchers`; `authorizeRequests` → `authorizeHttpRequests`; `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`.

#### 2.2 Oracle JDBC ojdbc8 21.1.0.0 → 21.9.0.0

**File:** `pom.xml` (parent, `dependencyManagement` section)
```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>21.9.0.0</version>
</dependency>
```

No code changes required.

#### 2.3 Jackson 2.13.5 → 2.17.x

**File:** `pom.xml`
```xml
<jackson.version>2.17.3</jackson.version>
```

No code changes required; 2.17.x is API-compatible with 2.13.x.

#### 2.4 Log4j2 2.20.0 → 2.23.x

**File:** `pom.xml`
```xml
<log4j.version>2.23.1</log4j.version>
```

No code changes required.

#### 2.5 jjwt 0.11.5 → 0.12.x

**File:** `monitor-backend/pom.xml`
```xml
<version>0.12.6</version>  <!-- for jjwt-api, jjwt-impl, jjwt-jackson -->
```

**API changes in 0.12.x** (affects `JwtTokenProvider.java`):

| 0.11.x API | 0.12.x API |
|------------|------------|
| `Jwts.parserBuilder()` | `Jwts.parser()` |
| `.setSigningKey(key)` | `.verifyWith(key)` |
| `.build().parseClaimsJws(token)` | `.build().parseSignedClaims(token)` |
| `.getBody()` | `.getPayload()` |
| `Jwts.builder().setSubject(...)` | `Jwts.builder().subject(...)` |
| `.setIssuedAt(...)` | `.issuedAt(...)` |
| `.setExpiration(...)` | `.expiration(...)` |
| `.signWith(key, alg)` | `.signWith(key, alg)` *(unchanged)* |

**Fixed `JwtTokenProvider.java` key methods:**

```java
public String generateToken(Authentication authentication) {
    String username = authentication.getName();
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtExpiration);

    return Jwts.builder()
            .subject(username)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}

public String getUsernameFromToken(String token) {
    return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
}

public boolean validateToken(String token) {
    try {
        Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
        return true;
    } catch (JwtException | IllegalArgumentException ex) {
        log.error("Invalid JWT token: {}", ex.getMessage());
    }
    return false;
}
```

---

## Correctness Properties

### Property 1: Bug Condition — Route Access Control

For each defective request pattern defined in `isAccessControlBuggy(request)`, the fixed handler must return HTTP 400, 401, or 403.

**Scoped cases (deterministic):**
- Unauthenticated `GET /api/auth/me` → 401
- Non-admin authenticated `POST /api/admin/datasources/reencrypt` → 403
- Non-admin authenticated `GET /api/system/info` → 403
- Authenticated `GET /api/cdc/events/files/content?path=../../../etc/passwd` → 400
- Authenticated `POST /api/jobs/{jobId}/stop?targetDirectory=/tmp/evil` → 400

### Property 2: Preservation — Non-Buggy Requests

For all requests where `isAccessControlBuggy(request)` is false, the response from the fixed system must equal the response from the unfixed system:
- Authenticated `GET /api/auth/me` → same user profile response
- ROLE_ADMIN `POST /api/admin/datasources/reencrypt` → same re-encryption result
- ROLE_ADMIN `GET /api/system/info` → same system info response
- Authenticated `GET /api/cdc/events/files/content?path=<valid>` → same file content
- Authenticated `POST /api/jobs/{jobId}/stop?targetDirectory=file:///opt/flink/savepoints/x` → same savepoint result
- Unauthenticated `POST /api/auth/login` → same JWT response
- Unauthenticated `GET /actuator/health` → same health response

---

## Risk Assessment

| Track | Risk | Reason |
|-------|------|--------|
| Route fixes | Low | Confined to annotations and SecurityConfig; no data model changes |
| ojdbc8 / Jackson / Log4j2 upgrades | Low | Drop-in version bumps; no API changes |
| Spring Boot 3.x migration | Medium | `javax.*` → `jakarta.*` namespace change; Security DSL changes |
| jjwt 0.12.x migration | Low-Medium | API method renames in one class (`JwtTokenProvider`) |

**Mitigation:** Apply route fixes first and verify all tests pass before starting dependency upgrades. Apply Spring Boot upgrade and jjwt upgrade together since both affect `SecurityConfig` and `JwtTokenProvider`.
