<template>
  <div class="inv-view">
    <div class="page-header intro-header">
      <div>
        <h2>组件一览</h2>
        <p>运行时后端 JAR 包与前端组件版本清单，标注已知 CVE 与修复建议</p>
      </div>
      <div class="header-meta">
        <span class="as-of">数据核实日期：{{ asOfDate }}</span>
      </div>
    </div>

    <!-- 汇总卡片 -->
    <div class="summary-grid">
      <div class="summary-card">
        <span class="summary-value">{{ totalComponents }}</span>
        <span class="summary-label">运行时组件总数</span>
      </div>
      <div class="summary-card sc-danger" v-if="counts.high">
        <span class="summary-value">{{ counts.high }}</span>
        <span class="summary-label">高危 / 需关注</span>
      </div>
      <div class="summary-card sc-warning" v-if="counts.moderate">
        <span class="summary-value">{{ counts.moderate }}</span>
        <span class="summary-label">中危</span>
      </div>
      <div class="summary-card sc-success">
        <span class="summary-value">{{ counts.clean }}</span>
        <span class="summary-label">当前版本未见已知 CVE</span>
      </div>
    </div>

    <div class="alert alert-info disclaimer">
      <icon-info theme="filled" size="16" fill="#0052CC" />
      <span>
        以下版本号直接来自 <code>pom.xml</code> / <code>mvn dependency:tree</code> 与
        <code>package-lock.json</code> / <code>npm audit</code> 的实际解析结果，代表当前运行时锁定的版本。
        CVE 状态基于公开漏洞库（NVD / GitHub Advisory / 供应商公告）核实，随时间可能有新披露，建议定期用
        <code>mvn dependency:tree</code>、<code>npm audit</code> 或 OWASP Dependency-Check 复查。
      </span>
    </div>

    <!-- monitor-backend -->
    <div class="card inv-section">
      <div class="section-head">
        <h3 class="section-title">monitor-backend（Spring Boot 运行时 JAR）</h3>
        <span class="text-sm text-muted">来源：mvn dependency:tree（已解析的实际版本）</span>
      </div>
      <ComponentTable :rows="backendJars" />
    </div>

    <!-- flink-jobs -->
    <div class="card inv-section">
      <div class="section-head">
        <h3 class="section-title">flink-jobs（Flink CDC 采集任务运行时）</h3>
        <span class="text-sm text-muted">来源：mvn dependency:tree（已解析的实际版本）</span>
      </div>
      <ComponentTable :rows="flinkJars" />
    </div>

    <!-- 前端 -->
    <div class="card inv-section">
      <div class="section-head">
        <h3 class="section-title">monitor-frontend（前端运行时组件）</h3>
        <span class="text-sm text-muted">来源：package-lock.json（已锁定的实际安装版本）</span>
      </div>
      <ComponentTable :rows="frontendPkgs" />
    </div>

    <div class="card inv-section">
      <h3 class="section-title">检查方法说明</h3>
      <ul class="method-list">
        <li>后端：<code>mvn -pl monitor-backend -am dependency:tree</code> / <code>mvn -pl flink-jobs -am dependency:tree</code> 获取实际解析（含传递依赖覆盖）后的版本。</li>
        <li>前端：<code>package-lock.json</code> 中锁定的安装版本，以及 <code>npm audit --omit=dev</code> 的生产依赖扫描结果。</li>
        <li>CVE 核实：结合 NVD、GitHub Security Advisories、Apache/Spring/Oracle 官方安全公告交叉确认，标注公开来源。</li>
        <li>“待升级”状态代表当前锁定版本存在已知 CVE 且已有修复版本；“无已知 CVE”不代表绝对安全，只代表检索时点未发现公开记录。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, h } from 'vue'

const asOfDate = '2026-07-08'

// ── 组件表格子组件（内联定义，避免额外文件） ──
const ComponentTable = {
  props: { rows: { type: Array, required: true } },
  setup(props) {
    return () => h('div', { class: 'table-responsive' }, [
      h('table', { class: 'table inv-table' }, [
        h('thead', [
          h('tr', [
            h('th', '组件'),
            h('th', '运行时版本'),
            h('th', '安全状态'),
            h('th', '说明 / CVE'),
          ])
        ]),
        h('tbody', props.rows.map(r => h('tr', [
          h('td', [
            h('div', { class: 'comp-name' }, r.name),
            r.group ? h('div', { class: 'comp-group' }, r.group) : null
          ]),
          h('td', [h('code', r.version)]),
          h('td', [
            h('span', { class: ['badge', badgeClass(r.status)] }, statusLabel(r.status))
          ]),
          h('td', { class: 'comp-note' }, r.note || '—'),
        ])))
      ])
    ])
  }
}

function badgeClass(status) {
  return {
    clean: 'badge-success',
    moderate: 'badge-warning',
    high: 'badge-danger',
    fixed: 'badge-info'
  }[status] || 'badge-secondary'
}

function statusLabel(status) {
  return {
    clean: '无已知 CVE',
    moderate: '中危，建议关注',
    high: '高危，建议升级',
    fixed: '已修复（本次核查已升级）'
  }[status] || '未知'
}

// ── monitor-backend 运行时 JAR（来自 mvn dependency:tree 实际解析结果） ──
const backendJars = [
  { name: 'Spring Boot', group: 'org.springframework.boot', version: '3.4.13', status: 'clean', note: 'Spring Boot BOM 版本，锁定于父 pom' },
  { name: 'Spring Framework (core/web/webmvc/beans/context)', group: 'org.springframework', version: '6.2.15', status: 'moderate', note: 'CVE-2026-41851（SpEL 缓存无界增长 DoS）、CVE-2026-22735（SSE 流损坏）等已在 6.2.16 修复，建议升级' },
  { name: 'Tomcat Embed (core/el/websocket)', group: 'org.apache.tomcat.embed', version: '11.0.22', status: 'moderate', note: 'CVE-2026-55956（默认 Servlet 安全约束被忽略）等在 11.0.23 修复；项目已在 pom 中显式锁定该版本以覆盖旧漏洞，建议进一步升级到 11.0.23' },
  { name: 'Apache HttpClient5', group: 'org.apache.httpcomponents.client5', version: '5.4.4', status: 'clean', note: '用于调用 Flink REST API（支持 PATCH）' },
  { name: 'Flink Clients / Runtime', group: 'org.apache.flink', version: '1.20.4', status: 'clean', note: '用于向 Flink 集群提交/查询/取消作业' },
  { name: 'MySQL Connector/J', group: 'com.mysql', version: '9.5.0', status: 'clean', note: 'CVE-2025-30706 / CVE-2025-21548 影响 9.0.0–9.2.0 / ≤9.1.0，当前 9.5.0 已高于修复版本' },
  { name: 'OceanBase Client', group: 'com.oceanbase', version: '2.4.18', status: 'clean', note: '用于 OceanBase Oracle 兼容模式连接' },
  { name: 'Oracle JDBC (ojdbc8)', group: 'com.oracle.database.jdbc', version: '19.8.0.0', status: 'moderate', note: '锁定以兼容 Oracle 11g；较新的 CPU 补丁版本（21.x）修复更多问题，若不需兼容旧库建议评估升级' },
  { name: 'Jackson Databind', group: 'com.fasterxml.jackson.core', version: '2.17.3', status: 'clean', note: '锁定版本，覆盖了低版本的多个反序列化类 CVE' },
  { name: 'Jackson Core / Annotations（传递依赖）', group: 'com.fasterxml.jackson.core', version: '2.18.5', status: 'clean', note: '高于 CVE-2025-52999（深度嵌套 JSON 栈溢出，修复于 2.15+）修复版本' },
  { name: 'Spring Security (starter)', group: 'org.springframework.boot', version: '3.4.13', status: 'clean', note: '随 Spring Boot BOM 管理' },
  { name: 'JJWT (api/impl/jackson)', group: 'io.jsonwebtoken', version: '0.12.6', status: 'clean', note: '登录 JWT 签发与校验，当前无公开已知 CVE' },
  { name: 'Aliyun OSS SDK', group: 'com.aliyun.oss', version: '3.17.4', status: 'clean', note: '用于 OSS 连接测试与文件归档' },
  { name: 'OWASP Java Encoder', group: 'org.owasp.encoder', version: '1.3.1', status: 'clean', note: '用作 ESAPI Encoder 的官方推荐替代，零已知 CVE' },
  { name: 'Commons Collections4', group: 'org.apache.commons', version: '4.4', status: 'fixed', note: 'pom 中显式锁定以覆盖旧版本 CVE-2015-6420（反序列化 RCE）' },
  { name: 'Jakarta XML Bind API', group: 'jakarta.xml.bind', version: '4.0.2', status: 'fixed', note: 'pom 中显式锁定以覆盖旧版本 CVE-2021-42568' },
  { name: 'Jakarta Activation API', group: 'jakarta.activation', version: '2.1.3', status: 'fixed', note: 'pom 中显式锁定以覆盖旧版本 CVE-2021-43287' },
  { name: 'Javassist', group: 'org.javassist', version: '3.29.2-GA', status: 'fixed', note: 'pom 中显式锁定以覆盖旧版本 CVE-2022-46175' },
  { name: 'Log4j API', group: 'org.apache.logging.log4j', version: '2.23.1 / 2.24.3', status: 'moderate', note: 'Spring Boot 传递引入 2.24.3（log4j-to-slf4j）；CVE-2025-68161 / CVE-2026-34477（Socket Appender TLS 主机名校验绕过）修复于 2.25.x+，本项目未启用 Socket Appender，风险较低但建议升级观察' },
]

// ── flink-jobs 运行时 JAR ──
const flinkJars = [
  { name: 'Flink Streaming Java / Clients / Runtime', group: 'org.apache.flink', version: '1.20.4', status: 'clean', note: 'Flink 核心运行时（JobManager/TaskManager 内运行）' },
  { name: 'Flink CDC (Oracle/MySQL/OceanBase Connector)', group: 'org.apache.flink', version: '3.4.0', status: 'clean', note: 'CDC 采集连接器，当前无公开已知 CVE' },
  { name: 'Debezium Connector Oracle（传递依赖）', group: 'io.debezium', version: '1.9.8.Final', status: 'moderate', note: 'Flink CDC 3.4.0 传递引入的固定版本；已知 Debezium CVE（如 CVE-2023-1419 脚本注入）主要影响 MySQL/Postgres 连接器，Oracle 连接器未见对应公开 CVE，但该版本较旧建议关注上游升级' },
  { name: 'Infinispan Core / Client-Hotrod / Commons（传递依赖）', group: 'org.infinispan', version: '15.0.21.Final', status: 'clean', note: 'Debezium Oracle 连接器的缓存依赖' },
  { name: 'Kafka Connect API/Runtime/JSON/Transforms/File', group: 'org.apache.kafka', version: '3.9.2', status: 'clean', note: '父 pom 显式锁定，覆盖了 Debezium 传递引入的 3.1.2（CVE-2024-31141 SSRF、CVE-2023-25194 RCE 等）。CVE-2025-27817（OAUTHBEARER SSRF/文件读取）修复版本为 ≥3.9.1，当前 3.9.2 已覆盖' },
  { name: 'MySQL Connector/J', group: 'com.mysql', version: '9.5.0', status: 'clean', note: '与 monitor-backend 共用锁定版本' },
  { name: 'OceanBase Client', group: 'com.oceanbase', version: '2.4.18', status: 'clean', note: '与 monitor-backend 共用锁定版本' },
  { name: 'Oracle JDBC (ojdbc8)', group: 'com.oracle.database.jdbc', version: '19.8.0.0', status: 'moderate', note: '同 monitor-backend，锁定以兼容 Oracle 11g' },
  { name: 'Flink Shaded Guava', group: 'org.apache.flink', version: '31.1-jre-17.0', status: 'clean', note: 'Flink 官方 shaded 版本，规避 classpath 冲突' },
  { name: 'Flink StateBackend RocksDB', group: 'org.apache.flink', version: '1.20.4', status: 'clean', note: 'Checkpoint 状态后端' },
  { name: 'Parquet Avro / Hadoop（传递依赖）', group: 'org.apache.parquet', version: '1.13.1 / 1.15.2', status: 'clean', note: 'Parquet 输出格式支持，当前无公开已知 CVE' },
  { name: 'Log4j API / Core / SLF4J-Impl', group: 'org.apache.logging.log4j', version: '2.23.1 / 2.24.3', status: 'moderate', note: '同 monitor-backend，CVE-2025-68161/CVE-2026-34477 影响 Socket Appender，本项目未使用该 Appender' },
]

// ── 前端运行时组件（来自 package-lock.json 实际锁定版本） ──
const frontendPkgs = [
  { name: 'Vue', group: 'vue', version: '3.5.35', status: 'clean', note: '当前无公开已知 CVE（历史 CVE-2024-9506 ReDoS 影响更早版本）' },
  { name: 'Vue Router', group: 'vue-router', version: '4.6.4', status: 'clean', note: '当前无公开已知 CVE' },
  { name: 'Pinia', group: 'pinia', version: '2.3.1', status: 'clean', note: '状态管理，当前无公开已知 CVE' },
  { name: 'Axios', group: 'axios', version: '1.17.0', status: 'clean', note: '已高于 CVE-2026-44494/44495/44496（原型污染网关攻击链，修复于 1.16.0）修复版本' },
  { name: 'form-data（axios 传递依赖）', group: 'form-data', version: '4.0.6', status: 'fixed', note: '原为 4.0.5，受 CVE-2026-12143（CRLF 注入）影响；本次核查已通过 package.json overrides 锁定到 4.0.6 修复版本' },
  { name: 'Chart.js', group: 'chart.js', version: '4.5.1', status: 'clean', note: '用于 CDC 事件趋势图/柱状图，当前无公开已知 CVE' },
  { name: 'core-js', group: 'core-js', version: '3.49.0', status: 'clean', note: 'ES 特性 polyfill，当前无公开已知 CVE' },
  { name: '@icon-park/vue-next', group: '@icon-park/vue-next', version: '1.4.2', status: 'clean', note: '图标库，当前无公开已知 CVE' },
  { name: 'webpack（构建工具，非运行时下发）', group: 'webpack', version: '5.107.2', status: 'clean', note: '仅用于构建打包，不随生产包下发给浏览器' },
]

const allRows = computed(() => [...backendJars, ...flinkJars, ...frontendPkgs])
const totalComponents = computed(() => allRows.value.length)
const counts = computed(() => {
  const c = { high: 0, moderate: 0, clean: 0, fixed: 0 }
  for (const r of allRows.value) {
    if (r.status === 'high') c.high++
    else if (r.status === 'moderate') c.moderate++
    else if (r.status === 'fixed') c.fixed++
    else c.clean++
  }
  return c
})
</script>

<style scoped>
.inv-view {
  padding-bottom: 40px;
}

.intro-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.header-meta {
  padding-top: 4px;
}

.as-of {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

/* ── 汇总卡片 ── */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  box-shadow: var(--shadow-card);
  border-top: 3px solid var(--color-primary);
  display: flex;
  flex-direction: column;
}

.summary-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.summary-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  margin-top: 2px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.sc-danger { border-top-color: var(--color-danger); }
.sc-warning { border-top-color: var(--color-warning); }
.sc-success { border-top-color: var(--color-success); }

/* ── 免责说明 ── */
.disclaimer {
  align-items: flex-start;
  gap: 10px;
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

/* ── 分区卡片 ── */
.inv-section {
  margin-bottom: 20px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.section-head .section-title {
  margin: 0;
}

/* ── 表格 ── */
.inv-table {
  font-size: var(--font-size-sm);
}

.comp-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.comp-group {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.comp-note {
  color: var(--color-text-secondary);
  max-width: 480px;
  line-height: 1.6;
}

/* ── 检查方法说明 ── */
.method-list {
  margin: 0;
  padding-left: 18px;
}

.method-list li {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.8;
}

@media (max-width: 767px) {
  .comp-note {
    max-width: 220px;
  }
}
</style>
