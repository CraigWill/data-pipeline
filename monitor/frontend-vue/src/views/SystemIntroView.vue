<template>
  <div class="intro-view">
    <!-- 页头 -->
    <div class="page-header intro-header">
      <div>
        <h2>系统介绍</h2>
        <p>了解实时数据管道平台的整体架构与数据处理流程</p>
      </div>
      <button class="btn btn-secondary" @click="startTour">
        <icon-guide-board theme="outline" size="14" /> 重新开始新手引导
      </button>
    </div>

    <!-- 概述 -->
    <div class="card intro-section">
      <h3 class="section-title">平台概述</h3>
      <p class="intro-text">
        本平台是一套基于 <strong>Flink CDC</strong> 的实时数据管道系统，用于从
        Oracle / OceanBase 等数据库实时捕获数据变更（CDC），经过处理后写入文件系统或阿里云 OSS 对象存储，
        并通过可视化界面进行任务管理与运行监控。系统由三个核心模块协作完成：
        <strong>数据采集（flink-jobs）</strong>、<strong>管理后端（monitor-backend）</strong>
        与 <strong>可视化前端（monitor-frontend）</strong>。
      </p>
    </div>

    <!-- 架构图 -->
    <div class="card intro-section">
      <h3 class="section-title">整体架构</h3>
      <p class="intro-text mb-3">
        前端通过 HTTP 调用后端接口；后端通过 Flink REST API 管理集群任务；
        采集任务运行在 Flink 集群内部，从数据库读取变更并写入共享文件系统。
      </p>

      <div class="arch-flow">
        <div class="arch-node node-frontend">
          <span class="node-title">Monitor Frontend</span>
          <span class="node-sub">Vue 3 可视化界面</span>
        </div>
        <div class="arch-arrow" data-label="HTTP REST">
          <span class="arrow-line"></span>
        </div>
        <div class="arch-node node-backend">
          <span class="node-title">Monitor Backend</span>
          <span class="node-sub">Spring Boot 管理服务</span>
        </div>
        <div class="arch-arrow" data-label="Flink REST API">
          <span class="arrow-line"></span>
        </div>
        <div class="arch-node node-cluster">
          <span class="node-title">Flink Cluster</span>
          <span class="node-sub">JobManager + TaskManager ×N</span>
          <div class="node-inner">flink-jobs（CDC 采集任务）</div>
        </div>
        <div class="arch-arrow" data-label="CDC / LogMiner">
          <span class="arrow-line"></span>
        </div>
        <div class="arch-node node-db">
          <span class="node-title">源数据库</span>
          <span class="node-sub">Oracle / OceanBase</span>
        </div>
        <div class="arch-arrow" data-label="flink-jobs 写入 CSV">
          <span class="arrow-line"></span>
        </div>
        <div class="arch-node node-fs">
          <span class="node-title">文件系统</span>
          <span class="node-sub">output/cdc/*.csv（本地 / 共享存储）</span>
        </div>
        <div class="arch-arrow" data-label="上传 / 归档">
          <span class="arrow-line"></span>
        </div>
        <div class="arch-node node-oss">
          <span class="node-title">阿里云 OSS</span>
          <span class="node-sub">对象存储（CSV 归档 / 分发）</span>
        </div>
      </div>
    </div>

    <!-- 网络拓扑 -->
    <div class="card intro-section">
      <h3 class="section-title">网络拓扑（容器地址）</h3>
      <p class="intro-text mb-3">
        以下地址来自 <code>docker-compose.yml</code> 的实际配置：容器名可用于 <code>docker logs/exec</code>，
        主机名是容器间通过 Docker 网络互相访问时使用的地址（DNS 由 Docker 自动解析）。
      </p>

      <!-- 拓扑图 -->
      <div class="topo-diagram-wrap">
        <svg viewBox="0 0 900 600" class="topo-svg" role="img" aria-label="网络拓扑图">
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M0,0 L10,5 L0,10 z" fill="#5E6C84" />
            </marker>
            <marker id="arrowDashed" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M0,0 L10,5 L0,10 z" fill="#FF8B00" />
            </marker>
          </defs>

          <!-- 用户浏览器 -->
          <rect x="360" y="14" width="180" height="40" rx="8" class="topo-box topo-box-client" />
          <text x="450" y="38" class="topo-box-title" text-anchor="middle">用户浏览器</text>

          <!-- flink-network 边界 -->
          <rect x="40" y="76" width="780" height="392" rx="10" class="topo-net-box" />
          <text x="54" y="98" class="topo-net-label">flink-network（bridge，Docker 内部网络）</text>

          <!-- monitor-frontend -->
          <rect x="370" y="112" width="160" height="44" rx="6" class="topo-box topo-box-frontend" />
          <text x="450" y="130" class="topo-box-title" text-anchor="middle">monitor-frontend</text>
          <text x="450" y="146" class="topo-box-sub" text-anchor="middle">flink-monitor-frontend · 8888:80</text>

          <!-- monitor-backend -->
          <rect x="370" y="188" width="160" height="44" rx="6" class="topo-box topo-box-backend" />
          <text x="450" y="206" class="topo-box-title" text-anchor="middle">monitor-backend</text>
          <text x="450" y="222" class="topo-box-sub" text-anchor="middle">flink-monitor-backend · 5001:5001</text>

          <!-- zookeeper -->
          <rect x="70" y="268" width="130" height="44" rx="6" class="topo-box topo-box-infra" />
          <text x="135" y="286" class="topo-box-title" text-anchor="middle">zookeeper</text>
          <text x="135" y="302" class="topo-box-sub" text-anchor="middle">zookeeper · 2181:2181</text>

          <!-- jobmanager -->
          <rect x="230" y="268" width="150" height="44" rx="6" class="topo-box topo-box-flink" />
          <text x="305" y="286" class="topo-box-title" text-anchor="middle">jobmanager（主）</text>
          <text x="305" y="302" class="topo-box-sub" text-anchor="middle">flink-jobmanager · 8081:8081</text>

          <!-- jobmanager-standby -->
          <rect x="410" y="268" width="170" height="44" rx="6" class="topo-box topo-box-flink" />
          <text x="495" y="286" class="topo-box-title" text-anchor="middle">jobmanager-standby（备）</text>
          <text x="495" y="302" class="topo-box-sub" text-anchor="middle">flink-jobmanager-standby · 8082:8081</text>

          <!-- obbinlog -->
          <rect x="70" y="356" width="130" height="44" rx="6" class="topo-box topo-box-db" />
          <text x="135" y="374" class="topo-box-title" text-anchor="middle">obbinlog</text>
          <text x="135" y="390" class="topo-box-sub" text-anchor="middle">obbinlog · 2983:2983</text>

          <!-- taskmanager (stacked ×N) -->
          <rect x="240" y="350" width="150" height="44" rx="6" class="topo-box topo-box-flink topo-box-stack" />
          <rect x="230" y="356" width="150" height="44" rx="6" class="topo-box topo-box-flink" />
          <text x="305" y="374" class="topo-box-title" text-anchor="middle">taskmanager ×N</text>
          <text x="305" y="390" class="topo-box-sub" text-anchor="middle">(scale 动态命名) · 6121-6130</text>

          <!-- ob43-default 外部网络边界 -->
          <rect x="650" y="330" width="150" height="96" rx="10" class="topo-net-box topo-net-box-external" />
          <text x="662" y="350" class="topo-net-label topo-net-label-external">ob43-default（外部网络）</text>
          <rect x="662" y="362" width="126" height="44" rx="6" class="topo-box topo-box-db" />
          <text x="725" y="380" class="topo-box-title" text-anchor="middle">observer</text>
          <text x="725" y="396" class="topo-box-sub" text-anchor="middle">172.22.0.2 · 2882</text>

          <!-- centos-ob 宿主机别名 -->
          <rect x="70" y="500" width="220" height="46" rx="6" class="topo-box topo-box-host" />
          <text x="180" y="519" class="topo-box-title" text-anchor="middle">centos-ob（宿主机别名）</text>
          <text x="180" y="535" class="topo-box-sub" text-anchor="middle">172.22.0.1 · extra_hosts</text>

          <!-- 阿里云 OSS -->
          <rect x="560" y="500" width="260" height="46" rx="6" class="topo-box topo-box-oss" />
          <text x="690" y="519" class="topo-box-title" text-anchor="middle">阿里云 OSS</text>
          <text x="690" y="535" class="topo-box-sub" text-anchor="middle">oss-cn-shanghai.aliyuncs.com · 443</text>

          <!-- 连接线：浏览器 → 前端 -->
          <path d="M450,54 L450,112" class="topo-link" marker-end="url(#arrow)" />
          <text x="458" y="86" class="topo-link-label">8888 (HTTP)</text>

          <!-- 前端 → 后端 -->
          <path d="M450,156 L450,188" class="topo-link" marker-end="url(#arrow)" />
          <text x="458" y="176" class="topo-link-label">nginx /api → :5001</text>

          <!-- 后端 → jobmanager -->
          <path d="M430,232 L430,250 L305,250 L305,268" class="topo-link" marker-end="url(#arrow)" />
          <!-- 后端 → jobmanager-standby -->
          <path d="M470,232 L470,250 L495,250 L495,268" class="topo-link" marker-end="url(#arrow)" />
          <text x="345" y="248" class="topo-link-label">Flink REST :8081（主/备）</text>

          <!-- zookeeper ↔ jobmanager -->
          <path d="M200,290 L230,290" class="topo-link" marker-end="url(#arrow)" marker-start="url(#arrow)" />
          <!-- jobmanager ↔ jobmanager-standby -->
          <path d="M380,290 L410,290" class="topo-link topo-link-dashed2" marker-end="url(#arrow)" marker-start="url(#arrow)" />
          <text x="205" y="264" class="topo-link-label">HA :2181</text>

          <!-- jobmanager → taskmanager -->
          <path d="M305,312 L305,356" class="topo-link" marker-end="url(#arrow)" />
          <text x="313" y="336" class="topo-link-label">RPC :6123</text>

          <!-- taskmanager → obbinlog -->
          <path d="M230,378 L200,378" class="topo-link" marker-end="url(#arrow)" />
          <text x="203" y="372" class="topo-link-label-sm">CDC :2983</text>

          <!-- obbinlog → observer（跨网络） -->
          <path d="M135,400 L135,460 L725,460 L725,406" class="topo-link topo-link-cross" marker-end="url(#arrow)" />
          <text x="380" y="453" class="topo-link-label">clog 拉取 :2882（跨网络直连）</text>

          <!-- 后端 → centos-ob（宿主机别名，虚线） -->
          <path d="M400,232 L400,480 L180,480 L180,500" class="topo-link topo-link-dashed" marker-end="url(#arrowDashed)" />
          <text x="220" y="474" class="topo-link-label-warn">CDC_ADMIN :2881</text>

          <!-- jobmanager-standby → OSS（虚线） -->
          <path d="M580,312 L580,486 L690,486 L690,500" class="topo-link topo-link-dashed" marker-end="url(#arrowDashed)" />
          <!-- 后端 → OSS（虚线） -->
          <path d="M530,206 L610,206 L610,486 L680,486" class="topo-link topo-link-dashed" marker-end="url(#arrowDashed)" />
          <text x="600" y="480" class="topo-link-label-warn">checkpoint / savepoint / 归档（可选）</text>
        </svg>

        <div class="topo-legend">
          <span class="legend-item"><span class="legend-line legend-solid"></span>网络内实时连接</span>
          <span class="legend-item"><span class="legend-line legend-cross"></span>跨 Docker 网络直连</span>
          <span class="legend-item"><span class="legend-line legend-dashed"></span>跨主机 / 外部依赖（可选）</span>
        </div>
      </div>

      <div v-for="net in networks" :key="net.name" class="topo-net">
        <div class="topo-net-head">
          <icon-network-tree theme="outline" size="14" />
          <span class="topo-net-name">{{ net.name }}</span>
          <span class="topo-net-tag" :class="{ external: net.external }">
            {{ net.external ? 'external network' : 'bridge network' }}
          </span>
        </div>
        <div class="topo-nodes">
          <div v-for="n in net.nodes" :key="n.container" class="topo-node">
            <div class="topo-node-head">
              <span class="topo-dot" :class="n.dotClass"></span>
              <span class="topo-service">{{ n.service }}</span>
              <span v-if="n.profile" class="topo-profile">{{ n.profile }}</span>
            </div>
            <div class="topo-row">
              <span class="topo-label">容器名</span>
              <code class="topo-value">{{ n.container }}</code>
            </div>
            <div class="topo-row">
              <span class="topo-label">主机名</span>
              <code class="topo-value">{{ n.hostname }}</code>
            </div>
            <div class="topo-row" v-if="n.ip">
              <span class="topo-label">固定 IP</span>
              <code class="topo-value">{{ n.ip }}</code>
            </div>
            <div class="topo-row">
              <span class="topo-label">端口映射</span>
              <span class="topo-ports">
                <code v-for="(p, i) in n.ports" :key="i" class="topo-port">{{ p }}</code>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="topo-net topo-net-external">
        <div class="topo-net-head">
          <icon-cloud-storage theme="outline" size="14" />
          <span class="topo-net-name">宿主机 / 外部服务</span>
          <span class="topo-net-tag external">external host</span>
        </div>
        <div class="topo-nodes">
          <div v-for="n in externalHosts" :key="n.container" class="topo-node topo-node-external">
            <div class="topo-node-head">
              <span class="topo-dot dot-external"></span>
              <span class="topo-service">{{ n.service }}</span>
            </div>
            <div class="topo-row">
              <span class="topo-label">别名</span>
              <code class="topo-value">{{ n.hostname }}</code>
            </div>
            <div class="topo-row">
              <span class="topo-label">地址</span>
              <code class="topo-value">{{ n.ip }}</code>
            </div>
            <div class="topo-row" v-if="n.ports">
              <span class="topo-label">端口</span>
              <span class="topo-ports">
                <code v-for="(p, i) in n.ports" :key="i" class="topo-port">{{ p }}</code>
              </span>
            </div>
            <div class="topo-row" v-if="n.note">
              <span class="topo-note">{{ n.note }}</span>
            </div>
          </div>
        </div>
      </div>

      <p class="topo-footnote">
        提示：容器名/主机名仅在 Docker 网络内部可解析；从宿主机或浏览器访问需使用左侧“端口映射”中冒号左边的宿主机端口（如 <code>localhost:8081</code>）。
      </p>
    </div>

    <!-- 核心模块 -->
    <div class="card intro-section">
      <h3 class="section-title">核心模块</h3>
      <div class="module-grid">
        <div v-for="m in modules" :key="m.title" class="module-card">
          <div class="module-head">
            <span class="module-badge" :class="m.badgeClass">{{ m.tag }}</span>
            <h4 class="module-title">{{ m.title }}</h4>
          </div>
          <p class="module-desc">{{ m.desc }}</p>
          <ul class="module-list">
            <li v-for="(f, i) in m.features" :key="i">{{ f }}</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 数据处理流程 -->
    <div class="card intro-section">
      <h3 class="section-title">数据处理流程</h3>
      <div class="steps-flow">
        <div v-for="(s, i) in dataSteps" :key="i" class="step-item">
          <div class="step-index">{{ i + 1 }}</div>
          <div class="step-body">
            <div class="step-title">{{ s.title }}</div>
            <div class="step-desc">{{ s.desc }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 技术栈 -->
    <div class="card intro-section">
      <h3 class="section-title">技术栈</h3>
      <div class="stack-wrap">
        <span v-for="t in techStack" :key="t" class="stack-chip">{{ t }}</span>
      </div>
    </div>

    <!-- 组件一览入口 -->
    <div class="card intro-section inv-cta">
      <div>
        <h3 class="section-title">组件一览</h3>
        <p class="intro-text">
          查看所有运行时后端 JAR 包与前端组件的实际锁定版本，以及是否存在已知 CVE 安全漏洞。
        </p>
      </div>
      <button class="btn btn-primary" @click="$router.push('/components')">
        查看组件一览 <icon-arrow-right theme="outline" size="14" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { ONBOARDING_EVENT } from '../onboarding'

// ── 网络拓扑数据：直接取自 docker-compose.yml 的实际容器配置 ──
const networks = [
  {
    name: 'flink-network',
    external: false,
    nodes: [
      {
        service: 'ZooKeeper', container: 'zookeeper', hostname: 'zookeeper',
        ports: ['2181:2181'], dotClass: 'dot-infra'
      },
      {
        service: 'Flink JobManager（主）', container: 'flink-jobmanager', hostname: 'jobmanager',
        ports: ['8081:8081 (Web UI)', '6123:6123 (RPC)', '6124:6124 (Blob)', '9249:9249 (Metrics)'],
        dotClass: 'dot-flink'
      },
      {
        service: 'Flink JobManager（备）', container: 'flink-jobmanager-standby', hostname: 'jobmanager-standby',
        ports: ['8082:8081 (Web UI)', '6125:6123 (RPC)', '6126:6124 (Blob)', '9250:9249 (Metrics)'],
        dotClass: 'dot-flink'
      },
      {
        service: 'Flink TaskManager ×N', container: '(scale 动态命名)', hostname: 'taskmanager',
        ports: ['6121-6130:6121 (Data)', '6122 (RPC 动态)', '9249 (Metrics 动态)'],
        dotClass: 'dot-flink'
      },
      {
        service: 'OceanBase Binlog Service', container: 'obbinlog', hostname: 'obbinlog',
        ports: ['2983:2983'], profile: 'profile: oceanbase-cdc', dotClass: 'dot-db'
      },
      {
        service: 'Monitor Backend', container: 'flink-monitor-backend', hostname: 'monitor-backend',
        ports: ['5001:5001'], dotClass: 'dot-backend'
      },
      {
        service: 'Monitor Frontend', container: 'flink-monitor-frontend', hostname: 'monitor-frontend',
        ports: ['8888:80'], dotClass: 'dot-frontend'
      },
      {
        service: 'Monitor Frontend（原生版，对比用）', container: 'flink-monitor-frontend-legacy', hostname: 'monitor-frontend-legacy',
        ports: ['8889:80'], profile: 'profile: legacy', dotClass: 'dot-frontend'
      }
    ]
  },
  {
    name: 'ob43-default',
    external: true,
    nodes: [
      {
        service: 'OceanBase Observer', container: '(外部 OB 集群，非本项目管理)', hostname: 'observer',
        ip: '172.22.0.2', ports: ['2882（clog 拉取，libobcdc 直连）'], dotClass: 'dot-db'
      }
    ]
  }
]

const externalHosts = [
  {
    service: 'centos-ob（宿主机 OceanBase 别名）',
    hostname: 'centos-ob',
    ip: '172.22.0.1',
    note: '通过 extra_hosts 注入到 obbinlog 与 monitor-backend 容器；CDC_ADMIN_HOST 默认指向此别名'
  },
  {
    service: '阿里云 OSS Endpoint',
    hostname: 'oss-cn-shanghai.aliyuncs.com',
    ip: 'https://oss-cn-shanghai.aliyuncs.com',
    ports: ['443 (HTTPS)'],
    note: 'Checkpoint/Savepoint 与 CDC 输出文件的 OSS 存储端点'
  }
]

const modules = [
  {
    tag: '采集',
    badgeClass: 'badge-info',
    title: 'flink-jobs (CDC 采集)',
    desc: '运行在 Flink 集群内部，负责从数据库实时捕获变更数据。',
    features: [
      '基于 Flink CDC Connector 读取 redo log',
      '处理 DML（增/改/删）与 DDL 事件',
      '过滤、转换后写入 CSV 文件',
      '支持 checkpoint 与故障恢复'
    ]
  },
  {
    tag: '管理',
    badgeClass: 'badge-success',
    title: 'monitor-backend (管理后端)',
    desc: '独立的 Spring Boot 应用，向前端提供 REST API。',
    features: [
      '提交 / 停止 / 查询 Flink 任务',
      '管理数据源与 OSS 配置',
      '查询 CDC 事件与统计信息',
      '集群监控与健康检查'
    ]
  },
  {
    tag: '展示',
    badgeClass: 'badge-secondary',
    title: 'monitor-frontend (可视化前端)',
    desc: '基于 Vue 3 的可视化界面，串联所有管理与监控操作。',
    features: [
      '仪表盘与作业列表展示',
      '数据源与任务的可视化配置',
      'CDC 事件浏览与模拟',
      '集群状态实时监控'
    ]
  }
]

const dataSteps = [
  { title: '接入数据源', desc: '一切都从这里开始：在「数据源」页面登记并测试数据库连接（Oracle / OceanBase 等），作为后续 CDC 任务的数据来源。' },
  { title: '捕获变更', desc: 'flink-jobs 通过 CDC（LogMiner）从源数据库读取 redo log 中的变更记录。' },
  { title: '过滤与转换', desc: '分离 DDL / DML 事件，按表过滤并进行格式转换。' },
  { title: '写入存储', desc: '通过 File Sink 将处理后的数据按时间分区写入 CSV，输出路径可为本地/共享文件系统，或直接写入阿里云 OSS（oss:// 路径）。' },
  { title: '归档到 OSS', desc: 'CSV 结果可上传/归档到阿里云 OSS 对象存储，便于长期保存与跨系统分发；OSS 连接在「OSS配置」中管理。' },
  { title: '读取与统计', desc: 'monitor-backend 读取共享文件系统或 OSS 中的输出文件，提供文件列表、内容与统计信息。' },
  { title: '可视化呈现', desc: 'monitor-frontend 通过 REST API 展示任务状态、CDC 事件与集群指标。' }
]

const techStack = [
  'Vue 3', 'Vue Router', 'Pinia', 'Spring Boot', 'Apache Flink',
  'Flink CDC 3.4', 'Oracle / OceanBase', '阿里云 OSS', 'Docker Compose', 'ZooKeeper (HA)'
]

function startTour() {
  window.dispatchEvent(new CustomEvent(ONBOARDING_EVENT))
}
</script>

<style scoped>
.intro-view {
  padding-bottom: 40px;
}

.intro-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.intro-section {
  margin-bottom: 20px;
}

.intro-section .section-title {
  margin-bottom: 12px;
}

.intro-text {
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.intro-text strong {
  color: var(--color-text-primary);
}

/* ── 架构流程图 ── */
.arch-flow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}

.arch-node {
  width: 100%;
  max-width: 460px;
  border-radius: var(--radius-md);
  padding: 14px 18px;
  text-align: center;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
}

.node-title {
  display: block;
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
}

.node-sub {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  margin-top: 2px;
}

.node-inner {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.node-frontend { border-top: 3px solid #6554C0; }
.node-backend  { border-top: 3px solid var(--color-success); }
.node-cluster  { border-top: 3px solid var(--color-primary); }
.node-db       { border-top: 3px solid var(--color-warning); }
.node-fs       { border-top: 3px solid var(--color-text-secondary); }
.node-oss      { border-top: 3px solid #FF6B00; }

.arch-arrow {
  position: relative;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.arrow-line {
  width: 2px;
  height: 100%;
  background: var(--color-border);
  position: relative;
}

.arrow-line::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 6px solid var(--color-border);
}

.arch-arrow[data-label]::before {
  content: attr(data-label);
  position: absolute;
  left: calc(50% + 12px);
  top: 50%;
  transform: translateY(-50%);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  white-space: nowrap;
}

/* ── 模块卡片 ── */
.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}

.module-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 16px;
  background: var(--color-bg);
}

.module-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.module-title {
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}

.module-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.module-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: 10px;
  line-height: 1.6;
}

.module-list {
  margin: 0;
  padding-left: 18px;
}

.module-list li {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  line-height: 1.7;
}

/* ── 数据流程步骤 ── */
.steps-flow {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step-item {
  display: flex;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--color-border);
}

.step-item:last-child {
  border-bottom: none;
}

.step-index {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-size: var(--font-size-sm);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.step-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-top: 2px;
  line-height: 1.6;
}

/* ── 技术栈 ── */
.stack-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stack-chip {
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.inv-cta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.inv-cta .intro-text {
  margin: 0;
}

/* ── 网络拓扑图（SVG） ── */
.topo-diagram-wrap {
  margin-bottom: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px;
  background: var(--color-surface);
  overflow-x: auto;
}

.topo-svg {
  width: 100%;
  min-width: 720px;
  height: auto;
  display: block;
}

.topo-box {
  fill: var(--color-surface);
  stroke: var(--color-border);
  stroke-width: 1.5;
}

.topo-box-client   { fill: #EBECF0; stroke: #97A0AF; }
.topo-box-frontend { fill: #EAE6FF; stroke: #6554C0; }
.topo-box-backend  { fill: var(--color-success-light); stroke: var(--color-success); }
.topo-box-flink    { fill: var(--color-primary-light); stroke: var(--color-primary); }
.topo-box-infra    { fill: #F4F5F7; stroke: #5E6C84; }
.topo-box-db       { fill: var(--color-warning-light); stroke: var(--color-warning); }
.topo-box-host     { fill: #FFEBE6; stroke: #FF6B00; stroke-dasharray: 4 3; }
.topo-box-oss      { fill: #FFEBE6; stroke: #FF6B00; stroke-dasharray: 4 3; }
.topo-box-stack    { opacity: 0.55; }

.topo-box-title {
  font-size: 12px;
  font-weight: 700;
  fill: var(--color-text-primary);
  font-family: var(--font-family);
}

.topo-box-sub {
  font-size: 10px;
  fill: var(--color-text-secondary);
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.topo-net-box {
  fill: none;
  stroke: var(--color-border);
  stroke-width: 1.5;
  stroke-dasharray: 5 4;
}

.topo-net-box-external {
  stroke: var(--color-warning);
}

.topo-net-label {
  font-size: 11px;
  font-weight: 600;
  fill: var(--color-text-muted);
  font-family: var(--font-family);
}

.topo-net-label-external {
  fill: #974F0C;
}

.topo-link {
  fill: none;
  stroke: #5E6C84;
  stroke-width: 1.5;
}

.topo-link-dashed {
  stroke: #FF8B00;
  stroke-dasharray: 5 4;
}

.topo-link-dashed2 {
  stroke: var(--color-primary);
  stroke-dasharray: 3 3;
}

.topo-link-cross {
  stroke: var(--color-warning);
}

.topo-link-label {
  font-size: 10px;
  fill: var(--color-text-secondary);
  font-family: var(--font-family);
}

.topo-link-label-sm {
  font-size: 9px;
  fill: var(--color-text-muted);
  font-family: var(--font-family);
}

.topo-link-label-warn {
  font-size: 10px;
  fill: #974F0C;
  font-family: var(--font-family);
}

.topo-legend {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.legend-line {
  display: inline-block;
  width: 22px;
  height: 0;
  border-top: 2px solid #5E6C84;
}

.legend-solid { border-top-style: solid; border-color: #5E6C84; }
.legend-cross { border-top-style: solid; border-color: var(--color-warning); }
.legend-dashed { border-top-style: dashed; border-color: #FF8B00; }

/* ── 网络拓扑（地址详情卡片） ── */
.topo-net {
  margin-bottom: 18px;
}
.topo-net:last-of-type {
  margin-bottom: 12px;
}

.topo-net-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.topo-net-name {
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: 'SFMono-Regular', Consolas, Menlo, monospace;
}

.topo-net-tag {
  font-size: var(--font-size-xs);
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 600;
}

.topo-net-tag.external {
  background: var(--color-warning-light);
  color: #974F0C;
}

.topo-nodes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.topo-node {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  background: var(--color-bg);
}

.topo-node-external {
  border-style: dashed;
}

.topo-node-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.topo-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-infra    { background: var(--color-text-secondary); }
.dot-flink    { background: var(--color-primary); }
.dot-db       { background: var(--color-warning); }
.dot-backend  { background: var(--color-success); }
.dot-frontend { background: #6554C0; }
.dot-external { background: #FF6B00; }

.topo-service {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.topo-profile {
  font-size: 10px;
  color: var(--color-text-muted);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 1px 5px;
  margin-left: auto;
}

.topo-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.topo-label {
  flex-shrink: 0;
  width: 52px;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.topo-value {
  font-size: var(--font-size-xs);
  color: var(--color-text-primary);
  word-break: break-all;
}

.topo-ports {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.topo-port {
  font-size: 10px;
  color: var(--color-text-secondary);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 1px 5px;
  white-space: nowrap;
}

.topo-note {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.topo-footnote {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  margin-top: 4px;
  line-height: 1.6;
}

@media (max-width: 767px) {
  .arch-arrow[data-label]::before { display: none; }
  .inv-cta { flex-direction: column; align-items: flex-start; }
  .topo-nodes { grid-template-columns: 1fr; }
}
</style>
