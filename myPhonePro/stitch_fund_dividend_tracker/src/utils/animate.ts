/**
 * 数字滚动动画（count-up）
 * 仅在 transform 层面驱动值变化，配合 prefers-reduced-motion 降级为直接显示终值。
 *
 * @param from 起始值
 * @param to 目标值
 * @param duration 时长 ms
 * @param onUpdate 每帧回调（接收当前插值）
 * @returns 取消函数（组件卸载时调用避免泄漏）
 */
export function animateNumber(
  from: number,
  to: number,
  duration: number,
  onUpdate: (v: number) => void,
): () => void {
  // 尊重系统"减少动态效果"设置：直接显示终值
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    onUpdate(to)
    return () => {}
  }

  if (from === to || duration <= 0) {
    onUpdate(to)
    return () => {}
  }

  const start = performance.now()
  // ease-out-quart：自然减速，不夸张
  const easeOut = (t: number) => 1 - Math.pow(1 - t, 4)
  let rafId = 0

  const step = (now: number) => {
    const t = Math.min((now - start) / duration, 1)
    onUpdate(from + (to - from) * easeOut(t))
    if (t < 1) {
      rafId = requestAnimationFrame(step)
    }
  }

  rafId = requestAnimationFrame(step)
  return () => cancelAnimationFrame(rafId)
}
