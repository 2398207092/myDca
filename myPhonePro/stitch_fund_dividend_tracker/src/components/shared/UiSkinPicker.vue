<script setup lang="ts">
/**
 * UiSkinPicker —— UI 皮肤包切换（原版界面 / Auros 卡片版）
 * 皮肤是整套前端，所以点选后整页跳到对应入口；两边共用同一份数据。
 */
import { UI_SKIN_PACKS, switchUiSkin } from '@/skins/packs'
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
          pack.id === 'classic'
            ? 'border-brand-ink ring-1 ring-brand-ink/30 bg-card-alt'
            : 'border-border-light hover:bg-card-alt'
        "
        @click="pack.id === 'auros' && switchUiSkin('auros')"
      >
        <span>
          <span class="block font-body text-sm font-medium text-text-primary">
            {{ pack.name }}
            <span v-if="pack.id === 'classic'" class="ml-1 font-body text-[10px] text-brand-ink">使用中</span>
          </span>
          <span class="block font-body text-xs text-text-tertiary mt-0.5">{{ pack.desc }}</span>
        </span>
        <span
          class="material-symbols-outlined text-[20px]"
          :class="pack.id === 'classic' ? 'text-brand-ink' : 'text-text-tertiary'"
        >{{ pack.id === 'classic' ? 'check_circle' : 'swap_horiz' }}</span>
      </button>
    </div>
  </section>
</template>
