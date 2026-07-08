<template>
  <transition name="ftue-fade">
    <div v-if="visible" class="ftue-root" role="dialog" aria-modal="true" aria-label="新手引导">
      <!-- 聚光灯遮罩：用 box-shadow 在目标周围铺满暗色，自身区域保持透明 -->
      <!-- pointer-events: none 确保这块遮罩永远不会拦截对目标按钮的点击 -->
      <template v-if="rect">
        <div class="ftue-spotlight" :style="highlightStyle"></div>

        <!-- 高亮描边 + 呼吸光圈（同样不拦截点击） -->
        <div class="ftue-highlight" :style="highlightStyle">
          <span class="ftue-ring"></span>
          <span class="ftue-ring ftue-ring-delay"></span>
        </div>

        <!-- “点这里” 手势与波纹动画 -->
        <div class="ftue-clicker" :style="clickerStyle">
          <span class="ftue-ripple"></span>
          <span class="ftue-ripple ftue-ripple-delay"></span>
          <icon-click theme="filled" size="20" fill="#FFAB00" class="ftue-click-icon" />
        </div>
      </template>

      <!-- 无目标（居中欢迎/结束卡片）时的全屏遮罩，此时没有需要点击的高亮元素，允许拦截点击 -->
      <div v-else class="ftue-mask ftue-mask-full" @click.stop></div>

      <!-- 引导卡片 -->
      <div
        class="ftue-card"
        :class="{ 'ftue-card-centered': !rect }"
        :style="cardStyle"
        ref="cardRef"
      >
        <div class="ftue-card-head">
          <span class="ftue-kicker">
            <icon-guide-board theme="outline" size="13" /> 新手任务 {{ stepIndex + 1 }}/{{ steps.length }}
          </span>
          <button class="ftue-close" aria-label="关闭" @click="skip">
            <icon-close theme="outline" size="16" />
          </button>
        </div>

        <h3 class="ftue-title">{{ current.title }}</h3>
        <p class="ftue-desc">{{ current.desc }}</p>
        <ul v-if="current.points" class="ftue-points">
          <li v-for="(p, i) in current.points" :key="i">{{ p }}</li>
        </ul>

        <div class="ftue-progress">
          <div class="ftue-progress-bar" :style="{ width: progressPct + '%' }"></div>
        </div>

        <div class="ftue-foot">
          <button class="ftue-skip" @click="skip">跳过引导</button>
          <div class="ftue-foot-right">
            <button v-if="stepIndex > 0" class="btn btn-secondary btn-sm" @click="prev">上一步</button>
            <template v-if="current.requireClick">
              <span class="ftue-click-hint">
                <icon-click theme="outline" size="12" /> 请点击高亮位置继续
              </span>
              <button class="ftue-manual-next" @click="next">点击无效？手动继续</button>
            </template>
            <button v-else-if="!isLast" class="btn btn-primary btn-sm" @click="next">下一步</button>
            <button v-else class="btn btn-success btn-sm" @click="finish">
              <icon-check theme="outline" size="13" /> 完成引导
            </button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ONBOARDING_EVENT,
  isOnboardingCompleted,
  markOnboardingCompleted
} from '../onboarding'

const router = useRouter()
const route = useRoute()

// ── 引导步骤定义：selector 指向真实页面元素，requireClick 时需要用户真正点击该元素才能继续 ──
// 整个流程以「数据源接入」为起点：一切数据处理都源于先接入一个数据源。
const steps = [
  {
    title: '欢迎使用实时数据管道平台',
    desc: '一切都从接入数据源开始。接下来带你走一遍完整链路：配置数据源 → 创建 CDC 任务 → 监控作业 → 查看事件与集群状态。跟着高亮提示操作，完成后不会再自动出现。',
    points: ['高亮框标出当前要看/要点的位置', '带 👆 提示的步骤需要你亲自点一下才能继续'],
    centered: true
  },
  {
    title: '第一步 · 前往「数据源」',
    desc: '所有数据处理都从这里开始：先接入一个数据库连接（Oracle / OceanBase 等），后续的 CDC 任务都会引用它。点击导航栏的「数据源」进入。',
    selector: '[data-tour="nav-datasources"]',
    requireClick: true
  },
  {
    title: '第二步 · 点击「新建数据源」',
    desc: '试着点一下这个按钮，打开数据源配置对话框。',
    route: '/datasources',
    selector: '[data-tour="ds-create-btn"]',
    requireClick: true
  },
  {
    title: '第三步 · 选择数据库类型',
    desc: '支持 Oracle、OceanBase（含 OB Oracle 模式）、MySQL、PostgreSQL。选择类型后，端口等字段会自动填入默认值。',
    selector: '[data-tour="ds-type-group"]'
  },
  {
    title: '第四步 · 测试连接',
    desc: '填好主机、端口、SID/库名、用户名密码后，点击「测试连接」验证配置是否可用，避免创建后才发现连不上。',
    selector: '[data-tour="ds-test-btn"]'
  },
  {
    title: '第五步 · 保存数据源',
    desc: '确认无误后点击「创建」保存。保存成功后弹窗会自动关闭，之后就能在任务创建向导里选中它。',
    selector: '[data-tour="ds-save-btn"]',
    requireClick: true,
    // 保存成功后弹窗会关闭；必须等弹窗真正消失才能前进，
    // 否则弹窗仍浮在最上层，会挡住下一步要点击的导航栏。
    waitForGone: '.modal-overlay'
  },
  {
    title: '第六步 · 前往「任务管理」',
    desc: '数据源接入后，下一步是基于它创建 CDC 采集任务。点击导航栏的「任务管理」。',
    selector: '[data-tour="nav-tasks"]',
    requireClick: true
  },
  {
    title: '第七步 · 点击「创建任务」',
    desc: '点击这个按钮进入任务创建向导。',
    route: '/tasks',
    selector: '[data-tour="task-create-btn"]',
    requireClick: true
  },
  {
    title: '第八步 · 选择数据源',
    desc: '这里选择你刚才创建的数据源，任务将从它读取变更数据；接下来还需选择 Schema 与表。',
    route: '/tasks/create',
    selector: '[data-tour="task-select-ds"]'
  },
  {
    title: '第九步 · 点击「下一步」推进向导',
    desc: '选好数据源、Schema、表之后，点击这个按钮逐步完成任务配置并提交。',
    selector: '[data-tour="task-next-btn"]'
  },
  {
    title: '第十步 · 前往「作业监控」',
    desc: '点击导航栏的「作业监控」，查看任务提交到 Flink 集群后的运行情况。',
    selector: '[data-tour="nav-jobs"]',
    requireClick: true
  },
  {
    title: '第十一步 · 查看作业列表',
    desc: '这里展示所有 Flink 作业的状态、运行时长与任务数，可以点击「查看详情」深入排查。',
    route: '/jobs',
    selector: '[data-tour="jobs-refresh-btn"]'
  },
  {
    title: '第十二步 · 前往「CDC事件」',
    desc: '点击导航栏的「CDC事件」，验证数据是否已经被正常采集。',
    selector: '[data-tour="nav-events"]',
    requireClick: true
  },
  {
    title: '第十三步 · 事件统计',
    desc: '这些卡片实时统计今日的 INSERT / UPDATE / DELETE 事件数量，下方还有分布图与文件列表可供查看明细。',
    route: '/events',
    selector: '[data-tour="events-stats"]'
  },
  {
    title: '第十四步 · 前往「集群状态」',
    desc: '点击导航栏的「集群状态」，查看 Flink 集群的健康情况。',
    selector: '[data-tour="nav-cluster"]',
    requireClick: true
  },
  {
    title: '第十五步 · 集群概览',
    desc: '这里可以看到 TaskManager 数量、可用 Slot、运行/完成/失败的作业数等关键指标。',
    route: '/cluster',
    selector: '[data-tour="cluster-overview"]'
  },
  {
    title: '第十六步 · 前往「系统介绍」',
    desc: '点击导航栏的「系统介绍」，可以随时回顾整体架构与数据处理流程，也能在那里重新开始本引导。',
    selector: '[data-tour="nav-intro"]',
    requireClick: true
  },
  {
    title: '最后一步 · 前往「组件一览」',
    desc: '点击导航栏的「组件一览」，查看后端 JAR 包与前端组件的实际运行时版本，以及是否存在已知 CVE 漏洞。',
    selector: '[data-tour="nav-components"]',
    requireClick: true
  },
  {
    title: '🎉 引导完成',
    desc: '你已经体验了从数据源接入、任务创建到作业监控、CDC 事件与集群状态的完整链路。现在可以自由探索系统了！',
    centered: true
  }
]

const visible = ref(false)
const stepIndex = ref(0)
const rect = ref(null) // { top, left, width, height }
const cardRef = ref(null)
const cardSize = ref({ width: 360, height: 220 })

const current = computed(() => steps[stepIndex.value])
const isLast = computed(() => stepIndex.value === steps.length - 1)
const progressPct = computed(() =>
  Math.round(((stepIndex.value + 1) / steps.length) * 100)
)

let rafId = null
let clickHandler = null

// ── 聚光灯高亮框样式（PAD 为目标外扩的呼吸空间） ──
const PAD = 6

const highlightStyle = computed(() => rect.value ? {
  top: (rect.value.top - PAD) + 'px',
  left: (rect.value.left - PAD) + 'px',
  width: (rect.value.width + PAD * 2) + 'px',
  height: (rect.value.height + PAD * 2) + 'px'
} : {})

const clickerStyle = computed(() => rect.value ? {
  top: (rect.value.top + rect.value.height / 2 - 14) + 'px',
  left: (rect.value.left + rect.value.width / 2 - 14) + 'px'
} : {})

// ── 引导卡片定位：紧贴目标下方，若空间不足则放上方/居中 ──
const cardStyle = computed(() => {
  if (!rect.value) return {}
  const vw = window.innerWidth
  const vh = window.innerHeight
  const { width: cw, height: ch } = cardSize.value
  let top = rect.value.top + rect.value.height + PAD + 14
  if (top + ch > vh - 16) {
    top = Math.max(16, rect.value.top - ch - 14)
  }
  let left = rect.value.left + rect.value.width / 2 - cw / 2
  left = Math.min(Math.max(16, left), vw - cw - 16)
  return { top: top + 'px', left: left + 'px' }
})

async function measureCard() {
  await nextTick()
  if (cardRef.value) {
    cardSize.value = {
      width: cardRef.value.offsetWidth || 360,
      height: cardRef.value.offsetHeight || 220
    }
  }
}

function findVisibleTarget(selector) {
  const els = document.querySelectorAll(selector)
  for (const el of els) {
    const r = el.getBoundingClientRect()
    if (r.width > 0 && r.height > 0) return el
  }
  return null
}

function waitForTarget(selector, timeoutMs = 4000) {
  return new Promise((resolve) => {
    const start = Date.now()
    const tick = () => {
      const el = findVisibleTarget(selector)
      if (el) return resolve(el)
      if (Date.now() - start > timeoutMs) return resolve(null)
      setTimeout(tick, 100)
    }
    tick()
  })
}

// 等待某个元素（如弹窗遮罩）从 DOM 中消失，避免它仍浮在最上层挡住下一步的目标。
function waitForGone(selector, timeoutMs = 4000) {
  return new Promise((resolve) => {
    const start = Date.now()
    const tick = () => {
      if (!findVisibleTarget(selector)) return resolve(true)
      if (Date.now() - start > timeoutMs) return resolve(false)
      setTimeout(tick, 100)
    }
    tick()
  })
}

async function goToStep(index) {
  stepIndex.value = index
  const step = steps[index]

  if (step.centered || !step.selector) {
    rect.value = null
    await measureCard()
    return
  }

  if (step.route && route.path !== step.route) {
    router.push(step.route)
  }

  // 目标可能藏在手机端汉堡菜单里，先展开菜单再定位
  if (!findVisibleTarget(step.selector)) {
    openMobileMenuIfNeeded()
  }

  const el = await waitForTarget(step.selector)
  if (!el) {
    rect.value = null
    await measureCard()
    return
  }
  updateRect(step.selector)
  await measureCard()
}

function openMobileMenuIfNeeded() {
  const hamburger = document.querySelector('.hamburger')
  if (!hamburger) return
  const isHamburgerVisible = getComputedStyle(hamburger).display !== 'none'
  if (isHamburgerVisible && !document.querySelector('.mobile-nav')) {
    hamburger.click()
  }
}

// 关键修复：不缓存旧的 DOM 节点，每次都用选择器重新查询。
// 之前缓存单个节点会在弹窗关闭/菜单展开收起等 DOM 变化后失效或指向被卸载的元素，
// 导致高亮圈停在一个已经点不到任何东西的位置——这正是“点击高亮按钮没反应”的根因之一。
function updateRect(selector) {
  const sel = selector || (steps[stepIndex.value] && steps[stepIndex.value].selector)
  if (!sel) return
  const el = findVisibleTarget(sel)
  if (!el) return
  const r = el.getBoundingClientRect()
  if (r.width === 0 && r.height === 0) return
  rect.value = { top: r.top, left: r.left, width: r.width, height: r.height }
}

function startRafLoop() {
  const loop = () => {
    if (!visible.value) return
    updateRect()
    rafId = requestAnimationFrame(loop)
  }
  rafId = requestAnimationFrame(loop)
}

function stopRafLoop() {
  if (rafId) cancelAnimationFrame(rafId)
  rafId = null
}

function next() {
  if (stepIndex.value < steps.length - 1) goToStep(stepIndex.value + 1)
}

function prev() {
  if (stepIndex.value > 0) goToStep(stepIndex.value - 1)
}

function finish() {
  markOnboardingCompleted()
  close()
}

function skip() {
  markOnboardingCompleted()
  close()
}

function open() {
  visible.value = true
  goToStep(0)
  startRafLoop()
  attachClickHandler()
}

function close() {
  visible.value = false
  rect.value = null
  stopRafLoop()
  detachClickHandler()
}

function attachClickHandler() {
  detachClickHandler()
  clickHandler = (e) => {
    const step = steps[stepIndex.value]
    if (!step || !step.requireClick || !step.selector) return
    if (e.target.closest && e.target.closest(step.selector)) {
      const advance = async () => {
        // 若该步骤会触发一个需要关闭的浮层（如保存后的弹窗），
        // 等它真正从 DOM 消失，避免残留在最上层挡住下一步目标。
        if (step.waitForGone) {
          await waitForGone(step.waitForGone)
        }
        if (stepIndex.value < steps.length - 1) goToStep(stepIndex.value + 1)
      }
      // 让真实的点击（导航/表单操作）先执行，再推进引导
      setTimeout(advance, 200)
    }
  }
  document.addEventListener('click', clickHandler, true)
}

function detachClickHandler() {
  if (clickHandler) {
    document.removeEventListener('click', clickHandler, true)
    clickHandler = null
  }
}

onMounted(() => {
  const token = localStorage.getItem('token')
  if (token && !isOnboardingCompleted()) {
    open()
  }
  window.addEventListener(ONBOARDING_EVENT, open)
  window.addEventListener('resize', updateRect)
})

onUnmounted(() => {
  window.removeEventListener(ONBOARDING_EVENT, open)
  window.removeEventListener('resize', updateRect)
  stopRafLoop()
  detachClickHandler()
})
</script>

<style scoped>
/*
 * 关键修复：.ftue-root 是铺满全屏的容器，默认 pointer-events 是 auto，
 * 会拦截整屏点击（哪怕自身透明）。必须设为 none，再由需要交互的
 * 子元素（遮罩、卡片、按钮）自行声明 pointer-events: auto。
 * 这也是之前“点击高亮按钮没反应”的根本原因。
 */
.ftue-root {
  position: fixed;
  inset: 0;
  z-index: 3000;
  pointer-events: none;
}

/* ── 全屏遮罩（仅在无高亮目标时使用，需要显式声明可拦截点击） ── */
.ftue-mask {
  position: fixed;
  background: rgba(9, 30, 66, 0.62);
  pointer-events: auto; /* 父级 .ftue-root 为 none，这里必须显式开启 */
}

.ftue-mask-full {
  top: 0; left: 0; width: 100%; height: 100%;
}

/*
 * ── 聚光灯遮罩 ──
 * 用巨大的 box-shadow 在目标区域周围铺满暗色，目标区域本身保持完全透明。
 * 关键点：pointer-events: none，且不对 top/left/width/height 做 transition，
 * 这样遮罩永远不会（哪怕只有一帧）盖住目标按钮，点击可以直达真实元素。
 */
.ftue-spotlight {
  position: fixed;
  border-radius: 8px;
  box-shadow: 0 0 0 9999px rgba(9, 30, 66, 0.62);
  pointer-events: none;
  z-index: 3000;
}

/* ── 高亮描边 ── */
.ftue-highlight {
  position: fixed;
  border-radius: 8px;
  box-shadow: 0 0 0 2px #FFAB00, 0 0 16px 2px rgba(255,171,0,0.5);
  pointer-events: none;
  z-index: 3001;
}

.ftue-ring {
  position: absolute;
  inset: -6px;
  border: 2px solid rgba(255,171,0,0.7);
  border-radius: 12px;
  animation: ftue-pulse 1.6s ease-out infinite;
}
.ftue-ring-delay {
  animation-delay: 0.8s;
}

@keyframes ftue-pulse {
  0%   { transform: scale(1);   opacity: 0.9; }
  70%  { transform: scale(1.08); opacity: 0; }
  100% { transform: scale(1.08); opacity: 0; }
}

/* ── “点这里” 波纹 ── */
.ftue-clicker {
  position: fixed;
  width: 28px;
  height: 28px;
  pointer-events: none;
  z-index: 3002;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ftue-click-icon {
  position: relative;
  z-index: 2;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.35));
}

.ftue-ripple {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(255,171,0,0.45);
  animation: ftue-ripple 1.4s ease-out infinite;
}
.ftue-ripple-delay {
  animation-delay: 0.7s;
}

@keyframes ftue-ripple {
  0%   { transform: scale(0.6); opacity: 0.8; }
  100% { transform: scale(2.6); opacity: 0; }
}

/* ── 引导卡片 ── */
.ftue-card {
  position: fixed;
  z-index: 3003;
  pointer-events: auto;
  width: 340px;
  max-width: calc(100vw - 32px);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-overlay);
  padding: 16px 18px;
  transition: top 0.25s ease, left 0.25s ease;
}

.ftue-card-centered {
  top: 50% !important;
  left: 50% !important;
  transform: translate(-50%, -50%);
  width: 380px;
}

.ftue-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.ftue-kicker {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  font-weight: 700;
  color: var(--color-primary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.ftue-close {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-text-muted);
  padding: 2px;
  border-radius: var(--radius-sm);
  display: flex;
  transition: background 0.15s, color 0.15s;
}
.ftue-close:hover {
  background: var(--color-bg);
  color: var(--color-text-primary);
}

.ftue-title {
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 6px;
}

.ftue-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin: 0 0 8px;
}

.ftue-points {
  margin: 0 0 10px;
  padding-left: 16px;
}
.ftue-points li {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.ftue-progress {
  height: 4px;
  background: var(--color-bg);
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 12px;
}
.ftue-progress-bar {
  height: 100%;
  background: var(--color-primary);
  border-radius: 999px;
  transition: width 0.3s ease;
}

.ftue-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.ftue-skip {
  background: none;
  border: none;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  cursor: pointer;
  padding: 4px 2px;
}
.ftue-skip:hover {
  color: var(--color-text-primary);
  text-decoration: underline;
}

.ftue-foot-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ftue-click-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: #B25E00;
  background: var(--color-warning-light);
  padding: 5px 10px;
  border-radius: var(--radius-md);
  animation: ftue-hint-blink 1.6s ease-in-out infinite;
}

@keyframes ftue-hint-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

/* 手动兜底：万一目标检测异常，用户仍可自行跳到下一步，不会被卡住 */
.ftue-manual-next {
  background: none;
  border: none;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  text-decoration: underline;
  cursor: pointer;
  padding: 4px 0;
  white-space: nowrap;
}
.ftue-manual-next:hover {
  color: var(--color-primary);
}

/* ── 过渡 ── */
.ftue-fade-enter-active,
.ftue-fade-leave-active {
  transition: opacity 0.2s;
}
.ftue-fade-enter-from,
.ftue-fade-leave-to {
  opacity: 0;
}

@media (max-width: 767px) {
  .ftue-card {
    width: calc(100vw - 32px);
  }
}
</style>
