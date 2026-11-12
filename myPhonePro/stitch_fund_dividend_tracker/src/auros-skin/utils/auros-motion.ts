/**
 * Auros 首页动效工具（2026-11-08）
 * 只有三种运动：进页编排 / 进视口揭示 / 跟随滚动。
 * 所有函数都遵守 prefers-reduced-motion：直接落到终态。
 */

export const prefersReducedMotion = (): boolean =>
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

export const easeOutExpo = (t: number): number =>
  t >= 1 ? 1 : 1 - Math.pow(2, -10 * t)

export const easeOutQuart = (t: number): number => 1 - Math.pow(1 - t, 4)

/** 数值滚动；返回取消函数 */
export function animateValue(
  from: number,
  to: number,
  duration: number,
  onUpdate: (v: number) => void,
  ease: (t: number) => number = easeOutExpo,
): () => void {
  if (prefersReducedMotion() || duration <= 0) {
    onUpdate(to)
    return () => {}
  }
  let raf = 0
  const start = performance.now()
  const tick = (now: number) => {
    const t = Math.min(1, (now - start) / duration)
    onUpdate(from + (to - from) * ease(t))
    if (t < 1) raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
  return () => cancelAnimationFrame(raf)
}

/**
 * 元素首次进入视口时执行一次（往下滚 12% 触发）。
 * 返回解绑函数。
 */
export function whenVisible(
  el: Element | null | undefined,
  cb: () => void,
  threshold = 0.25,
): () => void {
  if (!el) {
    cb()
    return () => {}
  }
  if (prefersReducedMotion()) {
    cb()
    return () => {}
  }
  const io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        io.disconnect()
        cb()
      }
    },
    { threshold, rootMargin: '0px 0px -12% 0px' },
  )
  io.observe(el)
  return () => io.disconnect()
}

/** 滚动进度 0..1（滚动到元素顶部进入视口 → 元素离开） */
export function scrollListener(cb: (y: number) => void): () => void {
  let raf = 0
  let last = -1
  const onScroll = () => {
    if (raf) return
    raf = requestAnimationFrame(() => {
      raf = 0
      const y = window.scrollY
      if (Math.abs(y - last) < 3) return
      last = y
      cb(y)
    })
  }
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
  return () => {
    window.removeEventListener('scroll', onScroll)
    if (raf) cancelAnimationFrame(raf)
  }
}
