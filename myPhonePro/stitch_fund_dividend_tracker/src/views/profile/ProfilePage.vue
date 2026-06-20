<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { PageState } from '@/types'
import { getProfile, getSettings } from '@/api/user'
import { listExchangeRates, refreshExchangeRates } from '@/api/exchangeRate'
import type { UserProfile, UserSettings } from '@/api/user'
import type { ExchangeRateItem } from '@/api/exchangeRate'
import AppHeader from '@/components/shared/AppHeader.vue'
import PageStateView from '@/components/shared/PageState.vue'

const router = useRouter()
const pageState = ref<PageState>('loading')
const profile = ref<UserProfile | null>(null)
const settings = ref<UserSettings | null>(null)
const exchangeRates = ref<ExchangeRateItem[]>([])
const isRefreshing = ref(false)
const avatarError = ref(false)
const showPhoneModal = ref(false)
const showDataInfoModal = ref(false)
const showContactModal = ref(false)

function onAvatarError() {
  avatarError.value = true
}

function showAlert(msg: string) {
  window.alert(msg)
}

async function loadData() {
  try {
    const [p, s, r] = await Promise.all([
      getProfile(),
      getSettings(),
      listExchangeRates(),
    ])
    profile.value = p
    settings.value = s
    exchangeRates.value = r
    pageState.value = 'ready'
  } catch (e) {
    console.error('加载个人中心数据失败:', e)
  }
}

async function handleRefresh() {
  isRefreshing.value = true
  try {
    const resp = await refreshExchangeRates()
    exchangeRates.value = resp.rates
  } catch (e) {
    console.error('刷新汇率失败:', e)
  } finally {
    setTimeout(() => { isRefreshing.value = false }, 1000)
  }
}

function goToSettings() {
  router.push('/metrics/settings')
}

function goToExpenseSettings() {
  router.push('/coverage/settings')
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    alert('已复制到剪贴板')
  }).catch(() => {
    // fallback
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    alert('已复制到剪贴板')
  })
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="min-h-screen bg-background">
    <AppHeader
      title="种树"
      :showLogo="true"
      rightIcon="settings"
      :rightAction="goToSettings"
    />

    <PageStateView v-if="pageState !== 'ready'" :state="pageState" />

    <main
      v-if="pageState === 'ready'"
      class="pt-20 pb-24 px-gutter max-w-[600px] mx-auto"
    >
      <!-- User Info Section -->
      <section class="mb-lg">
        <div
          class="bg-surface-container-lowest rounded-xl p-lg card-shadow flex items-center gap-md cursor-pointer active:scale-[0.98] transition-transform"
          @click="goToSettings"
        >
          <div class="relative shrink-0">
            <img
              v-if="profile?.avatar && !avatarError"
              :src="profile?.avatar"
              alt="User Avatar"
              class="w-20 h-20 rounded-full border-2 border-primary-container object-cover"
              @error="onAvatarError"
            />
            <div
              v-else
              class="w-20 h-20 rounded-full border-2 border-primary-container bg-surface-container-high flex items-center justify-center"
            >
              <span class="material-symbols-outlined text-[36px] text-on-surface-variant">person</span>
            </div>
            <div
              class="absolute -bottom-1 -right-1 bg-primary-container text-white p-1 rounded-full border-2 border-surface-container-lowest flex items-center justify-center"
            >
              <span
                class="material-symbols-outlined text-[14px]"
                style="font-variation-settings: 'FILL' 1;"
              >verified</span>
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-sm mb-xs">
              <h2 class="font-headline-md text-headline-md text-on-surface truncate">
                {{ profile?.name }}
              </h2>
            </div>
            <div class="flex items-center gap-xs flex-wrap">
              <span
                v-if="profile?.membership === 'pro'"
                class="bg-primary-container text-white px-2 py-0.5 rounded-lg text-caption font-label-bold flex items-center gap-1"
              >
                <span
                  class="material-symbols-outlined text-[14px]"
                  style="font-variation-settings: 'FILL' 1;"
                >workspace_premium</span>
                Pro 会员
              </span>
              <span class="text-on-surface-variant text-caption font-caption">
                {{ profile?.membershipExpiry }} 到期
              </span>
            </div>
          </div>
          <span class="material-symbols-outlined text-on-surface-variant shrink-0">
            chevron_right
          </span>
        </div>
      </section>

      <!-- Exchange Rates Card -->
      <section class="mb-lg">
        <div class="bg-surface-container-lowest rounded-xl overflow-hidden card-shadow">
          <div class="p-md bg-surface-container-low flex justify-between items-center">
            <div class="flex items-center gap-sm">
              <span class="material-symbols-outlined text-primary">currency_exchange</span>
              <span class="font-label-bold text-on-surface">货币与汇率</span>
            </div>
            <button
              class="flex items-center gap-xs bg-surface-container-lowest px-3 py-1 rounded-full border border-outline-variant hover:bg-surface-container transition-colors"
            >
              <span class="text-body-sm font-label-bold">人民币 (CNY)</span>
              <span class="material-symbols-outlined text-[16px]">expand_more</span>
            </button>
          </div>
          <div class="p-md grid grid-cols-2 gap-0 divide-x divide-outline-variant">
            <div class="pl-sm" v-for="rate in exchangeRates" :key="rate.pair">
              <p class="text-caption text-on-surface-variant mb-1">{{ rate.label }}</p>
              <p class="font-headline-md text-primary">{{ rate.rate.toFixed(4) }}</p>
            </div>
          </div>
          <div
            class="px-md pb-md flex justify-between items-center border-t border-outline-variant/30 pt-sm"
          >
            <span class="text-caption text-on-surface-variant flex items-center gap-1">
              <span class="material-symbols-outlined text-[14px]">schedule</span>
              更新于: {{ exchangeRates[0]?.updatedAt }}
            </span>
            <button
              class="text-primary text-caption font-label-bold flex items-center gap-xs active:scale-95 transition-transform"
              @click="handleRefresh"
            >
              立即刷新
              <span
                class="material-symbols-outlined text-[14px]"
                :class="{ 'animate-spin': isRefreshing }"
              >refresh</span>
            </button>
          </div>
        </div>
      </section>

      <!-- Function List -->
      <section class="bg-surface-container-lowest rounded-xl overflow-hidden card-shadow mb-xl">
        <div class="divide-y divide-outline-variant">
          <!-- Phone -->
          <div
            class="flex items-center justify-between p-md hover:bg-surface-container transition-colors cursor-pointer group"
            @click="showPhoneModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-secondary">smartphone</span>
              <span class="text-body-md font-label-bold">已绑定手机号</span>
            </div>
            <div class="flex items-center gap-sm">
              <span class="text-on-surface-variant text-body-sm">{{ profile?.phone }}</span>
              <span
                class="material-symbols-outlined text-on-surface-variant group-hover:translate-x-1 transition-transform"
              >chevron_right</span>
            </div>
          </div>
          <!-- Expense Settings -->
          <div
            class="flex items-center justify-between p-md hover:bg-surface-container transition-colors cursor-pointer group"
            @click="goToExpenseSettings"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-secondary">receipt_long</span>
              <span class="text-body-md font-label-bold">生活支出设置</span>
            </div>
            <span
              class="material-symbols-outlined text-on-surface-variant group-hover:translate-x-1 transition-transform"
            >chevron_right</span>
          </div>
          <!-- Data Legend -->
          <div
            class="flex items-center justify-between p-md hover:bg-surface-container transition-colors cursor-pointer group"
            @click="showDataInfoModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-secondary">info</span>
              <span class="text-body-md font-label-bold">数据口径说明</span>
            </div>
            <span
              class="material-symbols-outlined text-on-surface-variant group-hover:translate-x-1 transition-transform"
            >chevron_right</span>
          </div>
          <!-- Contact -->
          <div
            class="flex items-center justify-between p-md hover:bg-surface-container transition-colors cursor-pointer group"
            @click="showContactModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-secondary">headset_mic</span>
              <span class="text-body-md font-label-bold">联系我们</span>
            </div>
            <span
              class="material-symbols-outlined text-on-surface-variant group-hover:translate-x-1 transition-transform"
            >chevron_right</span>
          </div>
        </div>
      </section>

      <!-- Legal Links -->
      <section class="bg-surface-container-lowest rounded-xl overflow-hidden card-shadow">
        <div class="divide-y divide-outline-variant">
          <div
            class="flex items-center justify-between p-md hover:bg-surface-container transition-colors cursor-pointer"
            @click="showAlert('免责声明内容即将上线')"
          >
            <span class="text-body-md text-on-surface-variant">免责声明</span>
            <span class="material-symbols-outlined text-on-surface-variant text-[18px]">
              arrow_outward
            </span>
          </div>
          <div
            class="flex items-center justify-between p-md hover:bg-surface-container transition-colors cursor-pointer"
            @click="showAlert('用户协议内容即将上线')"
          >
            <span class="text-body-md text-on-surface-variant">用户协议</span>
            <span class="material-symbols-outlined text-on-surface-variant text-[18px]">
              arrow_outward
            </span>
          </div>
          <div
            class="flex items-center justify-between p-md hover:bg-surface-container transition-colors cursor-pointer"
            @click="showAlert('隐私政策内容即将上线')"
          >
            <span class="text-body-md text-on-surface-variant">隐私政策</span>
            <span class="material-symbols-outlined text-on-surface-variant text-[18px]">
              arrow_outward
            </span>
          </div>
        </div>
      </section>

      <!-- Version -->
      <div class="mt-xl text-center pb-8">
        <p class="text-caption text-on-surface-variant opacity-50">种树 v2.4.0</p>
      </div>
    </main>

    <!-- Phone Modal -->
    <Teleport to="body">
      <div
        v-if="showPhoneModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showPhoneModal = false"
      >
        <div class="bg-surface-container-lowest rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-label-bold text-label-bold text-on-surface">已绑定手机号</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:bg-surface-container rounded-lg transition-colors"
              @click="showPhoneModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="flex items-center gap-md py-lg">
            <span class="material-symbols-outlined text-[48px] text-primary">smartphone</span>
            <div>
              <p class="font-headline-sm text-headline-sm text-on-surface">{{ profile?.phone }}</p>
              <p class="text-caption text-on-surface-variant mt-1">手机号已实名认证</p>
            </div>
          </div>
          <button
            class="w-full py-2.5 rounded-lg bg-primary text-white font-label-bold text-label-bold active:scale-[0.98] transition-transform"
            @click="copyToClipboard(profile?.phone || '')"
          >
            复制手机号
          </button>
        </div>
      </div>
    </Teleport>

    <!-- Data Info Modal -->
    <Teleport to="body">
      <div
        v-if="showDataInfoModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showDataInfoModal = false"
      >
        <div class="bg-surface-container-lowest rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-label-bold text-label-bold text-on-surface">数据口径说明</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:bg-surface-container rounded-lg transition-colors"
              @click="showDataInfoModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="space-y-md text-body-md text-on-surface-variant leading-relaxed">
            <p><span class="font-label-bold text-on-surface">市值</span> 基于最新基金净值 × 持有份额计算，每交易日更新。</p>
            <p><span class="font-label-bold text-on-surface">成本</span> 为所有买入交易的总投入金额（含手续费）。</p>
            <p><span class="font-label-bold text-on-surface">股息率</span> = 近一年分红总额 / 最新市值 × 100%。</p>
            <p><span class="font-label-bold text-on-surface">预测分红</span> 基于历史分红记录和当前持有份额估算，实际以基金公告为准。</p>
            <p><span class="font-label-bold text-on-surface">汇率数据</span> 由东方财富提供，仅供参考。</p>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Contact Modal -->
    <Teleport to="body">
      <div
        v-if="showContactModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showContactModal = false"
      >
        <div class="bg-surface-container-lowest rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-label-bold text-label-bold text-on-surface">联系我们</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:bg-surface-container rounded-lg transition-colors"
              @click="showContactModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="space-y-md">
            <div
              class="flex items-center gap-md p-md bg-surface-container rounded-xl cursor-pointer active:scale-[0.98] transition-transform"
              @click="copyToClipboard('support@zhongshu.app')"
            >
              <span class="material-symbols-outlined text-primary">mail</span>
              <div>
                <p class="font-label-bold text-label-bold text-on-surface">邮箱</p>
                <p class="text-caption text-on-surface-variant">support@zhongshu.app</p>
              </div>
            </div>
            <div class="flex items-center gap-md p-md bg-surface-container rounded-xl">
              <span class="material-symbols-outlined text-primary">chat</span>
              <div>
                <p class="font-label-bold text-label-bold text-on-surface">在线客服</p>
                <p class="text-caption text-on-surface-variant">工作日内 24 小时回复</p>
              </div>
            </div>
            <div class="flex items-center gap-md p-md bg-surface-container rounded-xl">
              <span class="material-symbols-outlined text-primary">wechat</span>
              <div>
                <p class="font-label-bold text-label-bold text-on-surface">微信公众号</p>
                <p class="text-caption text-on-surface-variant">搜索「种树」关注</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
