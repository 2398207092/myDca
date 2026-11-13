<script setup lang="ts">
/**
 * 指标设置（四层信息骨架 · 2026-11-08）
 * 章 01 已选指标：主角数 + 六格刻度 + 一组行（≤5 行 + 折叠）
 * 章 02 全部指标：一组行（右侧自制 toggle）+ 折叠；at-max 禁用逻辑不变
 * 业务逻辑与原页面一致，只重排信息层级。
 */
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ALL_METRICS, loadEnabledKeys, saveEnabledKeys } from '@/api/metrics'
import AurosNavBar from '@/auros-skin/components/auros/AurosNavBar.vue'
import AurosChapter from '@/auros-skin/components/auros/AurosChapter.vue'
import AurosStat from '@/auros-skin/components/auros/AurosStat.vue'
import AurosRail from '@/auros-skin/components/auros/AurosRail.vue'
import AurosGroup from '@/auros-skin/components/auros/AurosGroup.vue'
import AurosRow from '@/auros-skin/components/auros/AurosRow.vue'
import AurosDisclosure from '@/auros-skin/components/auros/AurosDisclosure.vue'
import AurosButton from '@/auros-skin/components/auros/AurosButton.vue'

const router = useRouter()

const enabledKeys = ref<string[]>(loadEnabledKeys())

const MAX_COUNT = 6
const isAtMax = computed(() => enabledKeys.value.length >= MAX_COUNT)

function isEnabled(key: string) {
  return enabledKeys.value.includes(key)
}

function canToggle(key: string) {
  return isEnabled(key) || !isAtMax.value
}

function toggle(key: string) {
  const idx = enabledKeys.value.indexOf(key)
  if (idx >= 0) {
    enabledKeys.value.splice(idx, 1)
  } else {
    if (isAtMax.value) return
    enabledKeys.value.push(key)
  }
  saveEnabledKeys(enabledKeys.value)
}

const previewMetrics = computed(() =>
  ALL_METRICS.filter((m) => enabledKeys.value.includes(m.key))
)

/* 密度预算：一章 ≤5 行，其余折叠 */
const shownSelected = computed(() => previewMetrics.value.slice(0, 5))
const restSelected = computed(() => previewMetrics.value.slice(5))
const shownMetrics = computed(() => ALL_METRICS.slice(0, 5))
const restMetrics = computed(() => ALL_METRICS.slice(5))

const railPercent = computed(() => Math.round((enabledKeys.value.length / MAX_COUNT) * 100))

function formatterText(formatter: string) {
  return formatter === 'money' ? '金额' : formatter === 'percent' ? '百分比' : '数值'
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="page">
    <AurosNavBar title="指标设置" sub="METRICS">
      <template #right>
        <button type="button" class="nav-text" @click="router.push('/')">首页</button>
      </template>
    </AurosNavBar>

    <main class="body">
      <!-- 章 01 已选指标 -->
      <AurosChapter label="METRICS" title="已选指标" meta="按顺序展示" :gap="false">
        <AurosStat
          :value="String(enabledKeys.length)"
          :unit="`/ ${MAX_COUNT}`"
          pink
          size="lg"
          caption="首页指标位"
        />
        <div class="rail-wrap">
          <AurosRail :percent="railPercent" :ticks="MAX_COUNT - 1" />
        </div>

        <AurosGroup v-if="shownSelected.length" title="首页展示顺序">
          <AurosRow
            v-for="(metric, i) in shownSelected"
            :key="metric.key"
            :label="metric.label"
            :sub="formatterText(metric.formatter)"
            :value="String(i + 1).padStart(2, '0')"
          />
        </AurosGroup>

        <AurosDisclosure v-if="restSelected.length" :count="restSelected.length">
          <AurosGroup title="其余已选" :count="`${restSelected.length} 项`">
            <AurosRow
              v-for="(metric, i) in restSelected"
              :key="metric.key"
              :label="metric.label"
              :sub="formatterText(metric.formatter)"
              :value="String(shownSelected.length + i + 1).padStart(2, '0')"
            />
          </AurosGroup>
        </AurosDisclosure>
      </AurosChapter>

      <!-- 章 02 全部指标 -->
      <AurosChapter
        label="ALL"
        title="全部指标"
        :meta="isAtMax ? '已达上限，取消某项后可替换' : `共 ${ALL_METRICS.length} 项`"
      >
        <AurosGroup title="指标开关">
          <AurosRow
            v-for="metric in shownMetrics"
            :key="metric.key"
            :label="metric.label"
            :sub="formatterText(metric.formatter)"
            :mark="isEnabled(metric.key) ? 'lit' : 'dim'"
            :tone="canToggle(metric.key) ? 'default' : 'muted'"
            clickable
            @click="toggle(metric.key)"
          >
            <span
              class="tgl"
              :class="{ 'is-on': isEnabled(metric.key), 'is-off': !canToggle(metric.key) }"
              role="switch"
              :aria-checked="isEnabled(metric.key)"
              :aria-label="`${isEnabled(metric.key) ? '关闭' : '开启'} ${metric.label}`"
            >
              <svg viewBox="0 0 44 24" width="44" height="24" fill="none" aria-hidden="true">
                <rect class="tgl__track" width="44" height="24" rx="12" />
                <circle class="tgl__halo" cx="7" cy="12" r="9" />
                <circle class="tgl__knob" cx="7" cy="12" r="5" />
              </svg>
            </span>
          </AurosRow>
        </AurosGroup>

        <AurosDisclosure v-if="restMetrics.length" :count="restMetrics.length">
          <AurosGroup title="其余指标" :count="`${restMetrics.length} 项`">
            <AurosRow
              v-for="metric in restMetrics"
              :key="metric.key"
              :label="metric.label"
              :sub="formatterText(metric.formatter)"
              :mark="isEnabled(metric.key) ? 'lit' : 'dim'"
              :tone="canToggle(metric.key) ? 'default' : 'muted'"
              clickable
              @click="toggle(metric.key)"
            >
              <span
                class="tgl"
                :class="{ 'is-on': isEnabled(metric.key), 'is-off': !canToggle(metric.key) }"
                role="switch"
                :aria-checked="isEnabled(metric.key)"
                :aria-label="`${isEnabled(metric.key) ? '关闭' : '开启'} ${metric.label}`"
              >
                <svg viewBox="0 0 44 24" width="44" height="24" fill="none" aria-hidden="true">
                  <rect class="tgl__track" width="44" height="24" rx="12" />
                  <circle class="tgl__halo" cx="7" cy="12" r="9" />
                  <circle class="tgl__knob" cx="7" cy="12" r="5" />
                </svg>
              </span>
            </AurosRow>
          </AurosGroup>
        </AurosDisclosure>
      </AurosChapter>
    </main>

    <footer class="bar">
      <AurosButton variant="primary" block @click="goBack">
        完成设置（已选 {{ enabledKeys.length }}/{{ MAX_COUNT }}）
      </AurosButton>
    </footer>
  </div>
</template>

<style scoped>
.page {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
}
.body {
  flex: 1;
  padding: 8px 16px calc(env(safe-area-inset-bottom, 0px) + 112px);
}
.nav-text {
  border: 0;
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: 13px;
  color: var(--au-silver);
}

/* 形状与组之间的呼吸（章内） */
.rail-wrap {
  margin-top: 16px;
}
.rail-wrap + :deep(.group) {
  margin-top: 22px;
}

/* ---------- 右侧自制 toggle：深水色块 + 磷光点 ---------- */
.tgl {
  display: inline-flex;
  align-items: center;
  pointer-events: none;
}
.tgl svg {
  display: block;
}
.tgl__track {
  fill: rgba(0, 55, 52, 0.85);
  transition: fill 240ms var(--au-ease);
}
.tgl.is-on .tgl__track {
  fill: rgba(203, 255, 252, 0.16);
}
.tgl__halo {
  fill: var(--au-lit);
  opacity: 0;
  transition: transform 220ms var(--au-ease), opacity 240ms var(--au-ease);
}
.tgl__knob {
  fill: rgba(237, 255, 254, 0.3);
  transition: transform 220ms var(--au-ease), fill 240ms var(--au-ease);
}
.tgl.is-on .tgl__knob {
  fill: var(--au-lit);
  transform: translateX(30px);
}
.tgl.is-on .tgl__halo {
  opacity: 0.18;
  transform: translateX(30px);
}
.tgl.is-off {
  opacity: 0.35;
}

/* ---------- 底部动作（全页唯一渐变） ---------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
  background: linear-gradient(180deg, rgba(1, 38, 36, 0) 0%, rgba(1, 29, 28, 0.92) 38%);
}

@media (prefers-reduced-motion: reduce) {
  .tgl__track,
  .tgl__halo,
  .tgl__knob {
    transition: none;
  }
}
</style>
