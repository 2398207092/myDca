<script setup lang="ts">
/**
 * AurosRow —— 行（信息骨架 L3）
 * 名称 13/500 + 副行 10 + 右侧数值 13 tabular；行高 48；行间一条 8% 发丝线。
 * 状态点：lit / pink / dim；可点行显示细箭头。
 */
withDefaults(
  defineProps<{
    label: string
    value?: string
    sub?: string
    mark?: 'none' | 'lit' | 'pink' | 'dim'
    tone?: 'default' | 'muted'
    clickable?: boolean
    chevron?: boolean
  }>(),
  { value: '', sub: '', mark: 'none', tone: 'default', clickable: false, chevron: false },
)
const emit = defineEmits<{ (e: 'click'): void }>()
</script>

<template>
  <component
    :is="clickable ? 'button' : 'div'"
    class="row"
    :class="{ 'row--clickable': clickable }"
    :type="clickable ? 'button' : undefined"
    @click="clickable && emit('click')"
  >
    <i v-if="mark !== 'none'" class="row__mark" :class="`is-${mark}`" aria-hidden="true"></i>
    <span class="row__main">
      <span class="row__label" :class="{ 'is-muted': tone === 'muted' }">{{ label }}</span>
      <span v-if="sub" class="row__sub">{{ sub }}</span>
    </span>
    <span class="row__right">
      <slot>
        <span v-if="value" class="row__value au-num">{{ value }}</span>
      </slot>
      <svg v-if="chevron" class="row__chev" viewBox="0 0 12 12" width="10" height="10" fill="none">
        <path d="M4.6 2.6 8 6l-3.4 3.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </span>
  </component>
</template>

<style scoped>
.row {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 9px 0;
  border: 0;
  background: none;
  font-family: inherit;
  text-align: left;
  border-bottom: 1px solid rgba(237, 255, 254, 0.1);
}
.row:last-child {
  border-bottom: 0;
}
.row--clickable {
  transition: opacity 160ms var(--au-ease);
}
.row--clickable:active {
  opacity: 0.65;
}
.row__mark {
  flex: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(237, 255, 254, 0.18);
}
.row__mark.is-lit {
  background: var(--au-lit);
  box-shadow: 0 0 8px rgba(203, 255, 252, 0.8);
}
.row__mark.is-pink {
  background: var(--au-pink);
  box-shadow: 0 0 8px rgba(253, 233, 255, 0.7);
}
.row__mark.is-dim {
  background: rgba(237, 255, 254, 0.22);
}
.row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.row__label {
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 0.01em;
  color: var(--au-mist);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.row__label.is-muted {
  color: rgba(187, 199, 198, 0.82);
  font-weight: 400;
}
.row__sub {
  font-size: 10px;
  letter-spacing: 0.06em;
  color: rgba(187, 199, 198, 0.6);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.row__right {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 56%;
  justify-content: flex-end;
}
.row__value {
  font-size: 13px;
  font-weight: 500;
  color: var(--au-mist);
  text-align: right;
  white-space: nowrap;
}
.row__chev {
  flex: none;
  color: rgba(187, 199, 198, 0.5);
}
</style>
