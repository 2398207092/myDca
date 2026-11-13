<script setup lang="ts">
/**
 * UiSkinPicker —— UI 皮肤包切换（原版 / Auros 卡片版 / 三面版）
 * 皮肤是整套前端，所以点选后整页跳到对应入口；三套共用同一份数据。
 * 当前皮肤从 localStorage 读取（与 public/skin-boot.js 同一份偏好）。
 */
import { UI_SKIN_PACKS, getUiSkin, switchUiSkin } from '@/skins/packs'

const current = getUiSkin()
</script>

<template>
  <section class="bg-card-bg rounded-xl overflow-hidden card-shadow border border-border-light/40">
    <div class="p-lg flex items-center justify-between">
      <div class="flex items-center gap-sm">
        <span class="material-symbols-outlined text-brand-ink">palette</span>
        <span class="font-body text-sm font-medium text-text-primary">界面皮肤</span>
      </div>
      <span class="font-body text-xs text-text-tertiary">切换会重新载入界面</span>
    </div>
    <div class="px-lg pb-lg space-y-2">
      <button
        v-for="pack in UI_SKIN_PACKS"
        :key="pack.id"
        type="button"
        class="w-full flex items-center justify-between rounded-lg border p-md text-left transition-all active:scale-[0.99]"
        :class="
          pack.id === current
            ? 'border-brand-ink ring-1 ring-brand-ink/30 bg-card-alt'
            : 'border-border-light hover:bg-card-alt'
        "
        @click="pack.id !== current && switchUiSkin(pack.id)"
      >
        <span>
          <span class="block font-body text-sm font-medium text-text-primary">
            {{ pack.name }}
            <span v-if="pack.id === current" class="ml-1 font-body text-[10px] text-brand-ink">使用中</span>
          </span>
          <span class="block font-body text-xs text-text-tertiary mt-0.5">{{ pack.desc }}</span>
        </span>
        <span
          class="material-symbols-outlined text-[20px]"
          :class="pack.id === current ? 'text-brand-ink' : 'text-text-tertiary'"
        >{{ pack.id === current ? 'check_circle' : 'swap_horiz' }}</span>
      </button>
    </div>
  </section>
</template>
