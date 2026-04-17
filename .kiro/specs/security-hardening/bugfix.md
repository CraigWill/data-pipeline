# Bugfix Requirements Document

## Introduction

The monitor-backend service contains multiple security vulnerabilities identified in the 2026-04-16 audit. These span two categories: (1) vulnerable/EOL dependency versions with known CVEs enabling remote code execution, authentication bypass, and SSRF; and (2) broken access control on API routes allowing privilege escalation, path traversal, and sensitive information disclosure. This document captures the defective behaviors, the correct behaviors that must replace them, and the existing behaviors that must be preserved without regression.

---

## Bug Analysis

### Current Behavior (Defect)

**Dependency Vulnerabilities**

1.1 WHEN the application runs with Spring Boot 2.7.18 / Spring Framework 5.3.x on JDK 9+ THEN the system is vulnerable to remote code execution via data binding (CVE-2022-22965, Spring4Shell, CVSS 9.8)

1.2 WHEN the application runs with Spring Boot 2.7.18 / Spring Framework 5.3.x THEN the system is vulnerable to authorization filter bypass via path manipulation (CVE-2023-20860, CVSS 7.5)

1.3 WHEN the application runs with Spring Security 5.7.x THEN the system is vulnerable to authentication bypass (CVE-2023-34034, CVE-2024-22234)

1.4 WHEN the application uses Oracle JDBC ojdbc8 21.1.0.0 THEN the system is vulnerable to JNDI-based SSRF/RCE via malicious JDBC connection strings (CVE-2021-2351, CVSS 8.3)

1.5 WHEN the application uses Jackson Databind 2.13.5 THEN the system runs on an outdated version that will not receive future security patches

1.6 WHEN the application uses Log4j2 2.20.0 THEN the system runs on an outdated version that will not receive future security patches

1.7 WHEN the application uses jjwt 0.11.5 THEN the system runs on an outdated version that will not receive future security patches

**Route Access Control Vulnerabilities**

1.8 WHEN any authenticated user sends a POST request to `/api/admin/datasources/reencrypt` THEN the system executes the sensitive password re-encryption operation without verifying the ROLE_ADMIN role

1.9 WHEN any user sends a GET request to `/api/cdc/events/files/content?path=<user-controlled-value>` THEN the system reads the file at the attacker-controlled path, enabling directory traversal outside the intended data directory

1.10 WHEN an unauthenticated user sends a GET request to `/api/auth/me` THEN the system returns current user information because the `/api/auth/**` wildcard `permitAll()` rule exposes this endpoint without authentication

1.11 WHEN any authenticated user sends a GET request to `/api/system/info` THEN the system returns sensitive internal details including JVM memory usage, Java version, and the Flink cluster URL without verifying the ROLE_ADMIN role

1.12 WHEN any authenticated user sends a POST request to `/api/jobs/{jobId}/stop?targetDirectory=<user-controlled-value>` THEN the system passes the attacker-controlled directory path to the Flink savepoint operation without validation

1.13 WHEN any unauthenticated request targets a non-`/api/**` path THEN the system permits access because `anyRequest().permitAll()` leaves all non-API routes publicly accessible

---

### Expected Behavior (Correct)

**Dependency Upgrades**

2.1 WHEN the application runs with Spring Boot 3.3.x / Spring Framework 6.1.x THEN the system SHALL be protected against CVE-2022-22965 and CVE-2023-20860 and receive continued security patches

2.2 WHEN the application runs with Spring Boot 3.3.x / Spring Security 6.2.x THEN the system SHALL be protected against CVE-2023-34034 and CVE-2024-22234

2.3 WHEN the application uses Oracle JDBC ojdbc8 21.9.0.0 or later THEN the system SHALL be protected against CVE-2021-2351

2.4 WHEN the application uses Jackson Databind 2.17.x or later THEN the system SHALL use a supported version eligible for future security patches

2.5 WHEN the application uses Log4j2 2.23.x or later THEN the system SHALL use a supported version eligible for future security patches

2.6 WHEN the application uses jjwt 0.12.x or later THEN the system SHALL use a supported version eligible for future security patches

**Route Access Control Fixes**

2.7 WHEN a user without ROLE_ADMIN sends a POST request to `/api/admin/datasources/reencrypt` THEN the system SHALL reject the request with HTTP 403 Forbidden

2.8 WHEN a user sends a GET request to `/api/cdc/events/files/content?path=<value>` THEN the system SHALL validate and canonicalize the path, reject any path that traverses outside the permitted base directory, and return HTTP 400 Bad Request for invalid paths

2.9 WHEN an unauthenticated user sends a GET request to `/api/auth/me` THEN the system SHALL reject the request with HTTP 401 Unauthorized

2.10 WHEN a user without ROLE_ADMIN sends a GET request to `/api/system/info` THEN the system SHALL reject the request with HTTP 403 Forbidden

2.11 WHEN a user sends a POST request to `/api/jobs/{jobId}/stop?targetDirectory=<value>` THEN the system SHALL validate the targetDirectory value against an allowlist or safe path pattern and reject invalid values with HTTP 400 Bad Request

2.12 WHEN any unauthenticated request targets a non-`/api/**` path THEN the system SHALL require authentication by default (`anyRequest().authenticated()`) unless the path is explicitly whitelisted

---

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a user sends a POST request to `/api/auth/login` with valid credentials THEN the system SHALL CONTINUE TO return a valid JWT token

3.2 WHEN a user sends a POST request to `/api/auth/logout` THEN the system SHALL CONTINUE TO clear the security context and return success

3.3 WHEN an authenticated user with ROLE_ADMIN sends a POST request to `/api/admin/datasources/reencrypt` THEN the system SHALL CONTINUE TO execute the password re-encryption operation successfully

3.4 WHEN an authenticated user sends a GET request to `/api/cdc/events/files/content?path=<valid-path-within-base-dir>` THEN the system SHALL CONTINUE TO return the file content with pagination

3.5 WHEN an authenticated user sends a GET request to `/api/auth/me` THEN the system SHALL CONTINUE TO return the current user's profile information

3.6 WHEN an authenticated user with ROLE_ADMIN sends a GET request to `/api/system/info` THEN the system SHALL CONTINUE TO return system information

3.7 WHEN an authenticated user sends a POST request to `/api/jobs/{jobId}/stop` with a valid targetDirectory THEN the system SHALL CONTINUE TO trigger the Flink savepoint stop operation

3.8 WHEN an unauthenticated user accesses `/api/auth/login` THEN the system SHALL CONTINUE TO serve the login endpoint without requiring prior authentication

3.9 WHEN any client accesses `/actuator/health` THEN the system SHALL CONTINUE TO return the health status without requiring authentication

3.10 WHEN authenticated users access any existing `/api/**` endpoint not mentioned above THEN the system SHALL CONTINUE TO function as before with no behavioral change

---

## Bug Condition Summary

```pascal
// Dependency Vulnerability Condition
FUNCTION isDependencyVulnerable(component, version)
  INPUT: component name and version string
  OUTPUT: boolean
  RETURN (component = "spring-boot" AND version < "3.0.0")
      OR (component = "ojdbc8" AND version < "21.9.0.0")
      OR (component = "jackson-databind" AND version < "2.17.0")
      OR (component = "log4j2" AND version < "2.23.0")
      OR (component = "jjwt" AND version < "0.12.0")
END FUNCTION

// Route Access Control Bug Condition
FUNCTION isAccessControlBuggy(request)
  INPUT: HTTP request with path, method, and principal
  OUTPUT: boolean
  RETURN (path = "/api/admin/datasources/reencrypt" AND NOT hasRole(principal, "ROLE_ADMIN"))
      OR (path MATCHES "/api/cdc/events/files/content" AND NOT isSafePath(param("path")))
      OR (path = "/api/auth/me" AND NOT isAuthenticated(principal))
      OR (path = "/api/system/info" AND NOT hasRole(principal, "ROLE_ADMIN"))
      OR (path MATCHES "/api/jobs/.*/stop" AND NOT isSafePath(param("targetDirectory")))
END FUNCTION

// Fix Checking Property
FOR ALL request WHERE isAccessControlBuggy(request) DO
  response ← securedHandler'(request)
  ASSERT response.status IN {400, 401, 403}
END FOR

// Preservation Checking Property
FOR ALL request WHERE NOT isAccessControlBuggy(request) DO
  ASSERT securedHandler'(request) = securedHandler(request)
END FOR
```
