# monitor-backend 组件漏洞扫描报告

**项目**: monitor-backend  
**扫描日期**: 2026-04-16  
**扫描工具**: java-vuln-scanner (手动 CVE 比对)  
**审计员**: Java 安全审计专家

---

## 1. 依赖版本清单

| 组件 | 版本 | 来源 |
|------|------|------|
| Spring Boot | **2.7.18** | parent pom.xml |
| Spring Framework | ~5.3.x (由 SB 2.7.18 管理) | 传递依赖 |
| Spring Security | ~5.7.x (由 SB 2.7.18 管理) | 传递依赖 |
| Jackson Databind | **2.13.5** | parent pom.xml |
| Jackson YAML | **2.13.5** | monitor-backend/pom.xml |
| Log4j2 | **2.20.0** | parent pom.xml |
| jjwt-api / jjwt-impl / jjwt-jackson | **0.11.5** | monitor-backend/pom.xml |
| Lombok | **1.18.44** | monitor-backend/pom.xml |
| Oracle JDBC (ojdbc8) | **21.1.0.0** | parent pom.xml |
| Apache Flink | **1.20.0** | parent pom.xml |
| Flink CDC | **3.4.0** | parent pom.xml |

---

## 2. 漏洞分析

### 2.1 Spring Boot 2.7.18

| 字段 | 内容 |
|------|------|
| **风险等级** | 中 |
| **CVE** | Spring Boot 2.7.x 已于 2023-11 EOL，不再接收安全补丁 |
| **位置** | `pom.xml` → `<spring.boot.version>2.7.18</spring.boot.version>` |
| **描述** | Spring Boot 2.7.18 是 2.7.x 系列最后一个版本，官方已停止维护。未来发现的漏洞将不会有官方补丁。此外，该版本依赖的 Spring Framework 5.3.x 同样已 EOL。 |
| **PoC** | 无直接 PoC，但 EOL 版本意味着任何新发现的 Spring Framework/Security 漏洞均无法获得修复。 |
| **修复建议** | 升级至 Spring Boot **3.2.x** 或 **3.3.x** (LTS)，对应 Spring Framework 6.x。注意 Jakarta EE 命名空间迁移（`javax.*` → `jakarta.*`）。 |

---

### 2.2 Spring Framework ~5.3.x (传递依赖)

| 字段 | 内容 |
|------|------|
| **风险等级** | 高 |
| **CVE** | CVE-2022-22965 (Spring4Shell), CVE-2023-20860, CVE-2023-20861 |
| **位置** | 由 `spring-boot-dependencies:2.7.18` BOM 管理 |
| **描述** | **CVE-2022-22965 (Spring4Shell, CVSS 9.8)**: 在 JDK 9+ 环境下，通过 `@RequestMapping` 处理器的数据绑定可实现远程代码执行。Spring Boot 2.7.18 中该漏洞已修复（需 >= 5.3.18），但 5.3.x 系列整体已 EOL。**CVE-2023-20860 (CVSS 7.5)**: Spring Security 中 `**` 通配符与 Spring MVC 路径匹配不一致，可能导致鉴权绕过。**CVE-2023-20861 (CVSS 5.3)**: SpEL 表达式注入。 |
| **PoC (CVE-2022-22965)** | `POST /api/cdc/tasks?class.module.classLoader.resources.context.parent.pipeline.first.pattern=%25%7Bc2%7Di%20...` |
| **修复建议** | 升级至 Spring Framework **6.1.x**（通过升级 Spring Boot 3.x 实现）。 |

---

### 2.3 Spring Security ~5.7.x (传递依赖)

| 字段 | 内容 |
|------|------|
| **风险等级** | 中 |
| **CVE** | CVE-2023-34034, CVE-2024-22234 |
| **位置** | 由 `spring-boot-dependencies:2.7.18` BOM 管理 |
| **描述** | **CVE-2023-34034 (CVSS 9.8)**: WebFlux 应用中路径匹配绕过（本项目使用 WebMVC，影响较低）。**CVE-2024-22234 (CVSS 7.4)**: `AuthorizationFilter` 在某些配置下可能被绕过。Spring Security 5.7.x 已 EOL。 |
| **PoC** | 路径末尾添加 `//` 或 `/./` 可能绕过部分 `antMatchers` 规则。 |
| **修复建议** | 升级至 Spring Security **6.2.x**（通过升级 Spring Boot 3.x 实现）。 |

---

### 2.4 Jackson Databind 2.13.5

| 字段 | 内容 |
|------|------|
| **风险等级** | 低 |
| **CVE** | 无已知高危 CVE（2.13.5 已修复 CVE-2022-42003, CVE-2022-42004） |
| **位置** | `pom.xml` → `<jackson.version>2.13.5</jackson.version>` |
| **描述** | 2.13.5 修复了 2.13.x 系列的主要反序列化漏洞。但 2.13.x 已非最新稳定版，建议升级。 |
| **修复建议** | 升级至 Jackson **2.17.x** 或 **2.18.x**。 |

---

### 2.5 Log4j2 2.20.0

| 字段 | 内容 |
|------|------|
| **风险等级** | 低（已修复 Log4Shell） |
| **CVE** | CVE-2021-44228 (Log4Shell) 已在 2.17.0+ 修复 |
| **位置** | `pom.xml` → `<log4j.version>2.20.0</log4j.version>` |
| **描述** | 2.20.0 已修复 Log4Shell (CVE-2021-44228, CVSS 10.0) 及后续 CVE-2021-45046、CVE-2021-45105、CVE-2021-44832。当前版本安全。 |
| **修复建议** | 建议升级至 **2.23.x** 以获取最新安全修复。 |

---

### 2.6 jjwt 0.11.5

| 字段 | 内容 |
|------|------|
| **风险等级** | 低 |
| **CVE** | 无已知高危 CVE |
| **位置** | `monitor-backend/pom.xml` |
| **描述** | 0.11.5 是较新版本，无已知高危漏洞。但代码层面存在弱密钥默认值问题（见鉴权审计报告）。 |
| **修复建议** | 升级至 **0.12.x** 系列（API 有变化，需代码适配）。 |

---

### 2.7 Oracle JDBC ojdbc8 21.1.0.0

| 字段 | 内容 |
|------|------|
| **风险等级** | 中 |
| **CVE** | CVE-2021-2351 (CVSS 8.3) |
| **位置** | `pom.xml` → `<version>21.1.0.0</version>` |
| **描述** | **CVE-2021-2351**: Oracle JDBC 驱动在处理 JNDI 连接时存在漏洞，可能被利用进行 SSRF 或 RCE。21.1.0.0 是较旧版本。 |
| **修复建议** | 升级至 ojdbc8 **21.9.0.0** 或更高版本。 |

---

### 2.8 Apache Flink 1.20.0

| 字段 | 内容 |
|------|------|
| **风险等级** | 中 |
| **CVE** | CVE-2023-41834 (CVSS 6.1), CVE-2020-17518, CVE-2020-17519 |
| **位置** | `pom.xml` → `<flink.version>1.20.0</flink.version>` |
| **描述** | Flink 历史上存在多个路径遍历和 REST API 未授权访问漏洞。1.20.0 是较新版本，但 Flink REST API 默认无认证，若暴露在公网存在高风险。 |
| **修复建议** | 确保 Flink REST API (8081) 不对外暴露；升级至最新稳定版本。 |

---

## 3. 漏洞汇总

| 编号 | 组件 | 版本 | 风险等级 | CVE |
|------|------|------|----------|-----|
| V-001 | Spring Boot | 2.7.18 | 中 | EOL |
| V-002 | Spring Framework | ~5.3.x | **高** | CVE-2022-22965, CVE-2023-20860 |
| V-003 | Spring Security | ~5.7.x | 中 | CVE-2023-34034, CVE-2024-22234 |
| V-004 | Jackson Databind | 2.13.5 | 低 | 无高危 CVE |
| V-005 | Log4j2 | 2.20.0 | 低 | 已修复 Log4Shell |
| V-006 | jjwt | 0.11.5 | 低 | 无高危 CVE |
| V-007 | Oracle JDBC | 21.1.0.0 | 中 | CVE-2021-2351 |
| V-008 | Apache Flink | 1.20.0 | 中 | REST API 无认证风险 |

---

## 4. 修复优先级

1. **立即处理**: 升级 Spring Boot 至 3.x（解决 V-001/V-002/V-003）
2. **短期处理**: 升级 Oracle JDBC 至 21.9.0.0（V-007）
3. **中期处理**: 升级 Jackson 至 2.17.x（V-004）、Log4j2 至 2.23.x（V-005）
4. **长期规划**: 评估 Flink REST API 访问控制（V-008）
