/**
 * useReveal —— 全站统一的"进入视口才揭示"
 * 规则：首次进入视口（往下 12%）时加 `is-in`；只播一次；prefers-reduced-motion 直接终态。
 * 时长为 --au-dur-3，缓动 --au-ease；元素初始态由 `.au-reveal` 负责。
 */
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import { prefersReducedMotion } from '@/auros-skin/utils/auros-motion'

export function useReveal(el: Ref<HTMLElement | null>, threshold = 0.15) {
  const isIn = ref(false)
  let io: IntersectionObserver | null = null

  onMounted(() => {
    if (prefersReducedMotion()) {
      isIn.value = true
      return
    }
    if (!el.value) {
      isIn.value = true
      return
    }
    io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          isIn.value = true
          io?.disconnect()
        }
      },
      { threshold, rootMargin: '0px 0px -8% 0px' },
    )
    io.observe(el.value)
  })

  onBeforeUnmount(() => io?.disconnect())

  return isIn
}
