<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { PageState } from '@/types'
import { getProfile, getSettings } from '@/api/user'
import { listExchangeRates, refreshExchangeRates } from '@/api/exchangeRate'
import type { UserProfile, UserSettings } from '@/api/user'
import type { ExchangeRateItem } from '@/api/exchangeRate'
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
  <div class="min-h-screen bg-page-bg">
    <!-- Header — 统一内联 -->
    <header class="flex items-center justify-between px-gutter h-14 sticky top-0 z-50 bg-card-bg border-b border-border-light/40">
      <div class="flex items-center gap-2">
        <span class="material-symbols-outlined text-brand text-2xl">eco</span>
        <h1 class="font-body text-md font-medium text-text-primary">种树</h1>
      </div>
      <button @click="goToSettings" class="w-10 h-10 flex items-center justify-center active:opacity-80 transition-opacity">
        <span class="material-symbols-outlined text-text-secondary">settings</span>
      </button>
    </header>

    <PageStateView v-if="pageState !== 'ready'" :state="pageState" />

    <main
      v-if="pageState === 'ready'"
      class="pt-4 pb-24 px-gutter"
    >
      <!-- User Info Section -->
      <section class="mb-lg">
        <div
          class="bg-card-bg rounded-xl p-lg card-shadow border border-border-light/40 flex items-center gap-md cursor-pointer active:scale-[0.98] transition-transform"
          @click="goToSettings"
        >
          <div class="relative shrink-0">
            <img
              v-if="profile?.avatar && !avatarError"
              :src="profile?.avatar"
              alt="User Avatar"
              class="w-20 h-20 rounded-full border-2 border-brand-light object-cover"
              @error="onAvatarError"
            />
            <div
              v-else
              class="w-20 h-20 rounded-full border-2 border-brand-light bg-card-alt flex items-center justify-center"
            >
              <span class="material-symbols-outlined text-[36px] text-text-secondary">person</span>
            </div>
            <div
              class="absolute -bottom-1 -right-1 bg-brand text-white p-1 rounded-full border-2 border-card-bg flex items-center justify-center"
            >
              <span
                class="material-symbols-outlined text-[14px]"
                style="font-variation-settings: 'FILL' 1;"
              >verified</span>
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-sm mb-xs">
              <h2 class="font-display text-lg text-text-primary truncate">
                {{ profile?.name }}
              </h2>
            </div>
            <div class="flex items-center gap-xs flex-wrap">
              <span
                v-if="profile?.membership === 'pro'"
                class="bg-brand text-white px-2 py-0.5 rounded-lg font-body text-xs font-medium flex items-center gap-1"
              >
                <span
                  class="material-symbols-outlined text-[14px]"
                  style="font-variation-settings: 'FILL' 1;"
                >workspace_premium</span>
                Pro 会员
              </span>
              <span class="text-text-tertiary font-body text-xs">
                {{ profile?.membershipExpiry }} 到期
              </span>
            </div>
          </div>
          <span class="material-symbols-outlined text-text-tertiary shrink-0">
            chevron_right
          </span>
        </div>
      </section>

      <!-- Exchange Rates Card -->
      <section class="mb-lg">
        <div class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
          <div class="p-lg bg-card-alt flex justify-between items-center">
            <div class="flex items-center gap-sm">
              <span class="material-symbols-outlined text-brand">currency_exchange</span>
              <span class="font-body text-sm font-medium text-text-primary">货币与汇率</span>
            </div>
            <button
              class="flex items-center gap-xs bg-card-bg px-3 py-1 rounded-full border border-border-light hover:bg-card-alt transition-colors"
            >
              <span class="font-body text-xs text-text-secondary">人民币 (CNY)</span>
              <span class="material-symbols-outlined text-[16px] text-text-tertiary">expand_more</span>
            </button>
          </div>
          <div class="p-lg grid grid-cols-2 gap-0 divide-x divide-border-light">
            <div class="pl-sm" v-for="rate in exchangeRates" :key="rate.pair">
              <p class="font-body text-xs text-text-tertiary mb-1">{{ rate.label }}</p>
              <p class="font-display text-lg font-semibold text-brand">{{ rate.rate.toFixed(4) }}</p>
            </div>
          </div>
          <div
            class="px-lg pb-lg flex justify-between items-center border-t border-border-light/30 pt-sm"
          >
            <span class="font-body text-xs text-text-tertiary flex items-center gap-1">
              <span class="material-symbols-outlined text-[14px]">schedule</span>
              更新于: {{ exchangeRates[0]?.updatedAt }}
            </span>
            <button
              class="text-brand font-body text-xs font-medium flex items-center gap-xs active:scale-95 transition-transform"
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
      <section class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40 mb-xl">
        <div class="divide-y divide-border-light">
          <!-- Phone -->
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group"
            @click="showPhoneModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">smartphone</span>
              <span class="font-body text-sm font-medium text-text-primary">已绑定手机号</span>
            </div>
            <div class="flex items-center gap-sm">
              <span class="text-text-tertiary font-body text-sm">{{ profile?.phone }}</span>
              <span
                class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform"
              >chevron_right</span>
            </div>
          </div>
          <!-- Expense Settings -->
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group"
            @click="goToExpenseSettings"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">receipt_long</span>
              <span class="font-body text-sm font-medium text-text-primary">生活支出设置</span>
            </div>
            <span
              class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform"
            >chevron_right</span>
          </div>
          <!-- Data Legend -->
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group"
            @click="showDataInfoModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">info</span>
              <span class="font-body text-sm font-medium text-text-primary">数据口径说明</span>
            </div>
            <span
              class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform"
            >chevron_right</span>
          </div>
          <!-- Contact -->
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer group"
            @click="showContactModal = true"
          >
            <div class="flex items-center gap-md">
              <span class="material-symbols-outlined text-text-secondary">headset_mic</span>
              <span class="font-body text-sm font-medium text-text-primary">联系我们</span>
            </div>
            <span
              class="material-symbols-outlined text-text-tertiary group-hover:translate-x-1 transition-transform"
            >chevron_right</span>
          </div>
        </div>
      </section>

      <!-- Legal Links -->
      <section class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
        <div class="divide-y divide-border-light">
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer"
            @click="showAlert('免责声明内容即将上线')"
          >
            <span class="font-body text-sm text-text-tertiary">免责声明</span>
            <span class="material-symbols-outlined text-text-tertiary text-[18px]">
              arrow_outward
            </span>
          </div>
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer"
            @click="showAlert('用户协议内容即将上线')"
          >
            <span class="font-body text-sm text-text-tertiary">用户协议</span>
            <span class="material-symbols-outlined text-text-tertiary text-[18px]">
              arrow_outward
            </span>
          </div>
          <div
            class="flex items-center justify-between p-lg hover:bg-card-alt transition-colors cursor-pointer"
            @click="showAlert('隐私政策内容即将上线')"
          >
            <span class="font-body text-sm text-text-tertiary">隐私政策</span>
            <span class="material-symbols-outlined text-text-tertiary text-[18px]">
              arrow_outward
            </span>
          </div>
        </div>
      </section>

      <!-- Version -->
      <div class="mt-xl text-center pb-8">
        <p class="font-body text-xs text-text-tertiary opacity-50">种树 v2.4.0</p>
      </div>
    </main>

    <!-- Phone Modal -->
    <Teleport to="body">
      <div
        v-if="showPhoneModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-gutter"
        @click.self="showPhoneModal = false"
      >
        <div class="bg-card-bg rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-body text-sm font-medium text-text-primary">已绑定手机号</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showPhoneModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="flex items-center gap-md py-lg">
            <span class="material-symbols-outlined text-[48px] text-brand">smartphone</span>
            <div>
              <p class="font-display text-lg text-text-primary">{{ profile?.phone }}</p>
              <p class="font-body text-xs text-text-tertiary mt-1">手机号已实名认证</p>
            </div>
          </div>
          <button
            class="w-full py-2.5 rounded-lg bg-brand text-white font-body font-medium text-sm active:scale-[0.98] transition-transform"
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
        <div class="bg-card-bg rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-body text-sm font-medium text-text-primary">数据口径说明</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showDataInfoModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="space-y-md font-body text-sm text-text-secondary leading-relaxed">
            <p><span class="font-medium text-text-primary">市值</span> 基于最新基金净值 × 持有份额计算，每交易日更新。</p>
            <p><span class="font-medium text-text-primary">成本</span> 为所有买入交易的总投入金额（含手续费）。</p>
            <p><span class="font-medium text-text-primary">股息率</span> = 近一年分红总额 / 最新市值 × 100%。</p>
            <p><span class="font-medium text-text-primary">预测分红</span> 基于历史分红记录和当前持有份额估算，实际以基金公告为准。</p>
            <p><span class="font-medium text-text-primary">汇率数据</span> 由东方财富提供，仅供参考。</p>
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
        <div class="bg-card-bg rounded-xl p-lg w-full max-w-sm">
          <div class="flex items-center justify-between mb-md">
            <h3 class="font-body text-sm font-medium text-text-primary">联系我们</h3>
            <button
              class="w-8 h-8 flex items-center justify-center text-text-tertiary hover:bg-card-alt rounded-lg transition-colors"
              @click="showContactModal = false"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <div class="space-y-md">
            <div
              class="flex items-center gap-md p-md bg-card-alt rounded-xl cursor-pointer active:scale-[0.98] transition-transform"
              @click="copyToClipboard('support@zhongshu.app')"
            >
              <span class="material-symbols-outlined text-brand">mail</span>
              <div>
                <p class="font-body text-sm font-medium text-text-primary">邮箱</p>
                <p class="font-body text-xs text-text-tertiary">support@zhongshu.app</p>
              </div>
            </div>
            <div class="flex items-center gap-md p-md bg-card-alt rounded-xl">
              <span class="material-symbols-outlined text-brand">chat</span>
              <div>
                <p class="font-body text-sm font-medium text-text-primary">在线客服</p>
                <p class="font-body text-xs text-text-tertiary">工作日内 24 小时回复</p>
              </div>
            </div>
            <div class="flex items-center gap-md p-md bg-card-alt rounded-xl">
              <span class="material-symbols-outlined text-brand">wechat</span>
              <div>
                <p class="font-body text-sm font-medium text-text-primary">微信公众号</p>
                <p class="font-body text-xs text-text-tertiary">搜索「种树」关注</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
