/**
 * 数据加载小工具：loading / error / data + 全局刷新信号。
 * 不引入状态库；每次 load 用序号防止旧请求覆盖新请求。
 */
import { onMounted, ref, watch, type Ref } from 'vue'
import { ui } from './ui'

export interface AsyncData<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<string | null>
  load: (quiet?: boolean) => Promise<void>
}

export function useAsyncData<T>(
  loader: () => Promise<T>,
  options: { immediate?: boolean } = {},
): AsyncData<T> {
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<string | null>(null)
  let seq = 0

  async function load(quiet = false): Promise<void> {
    const my = ++seq
    if (!quiet) loading.value = true
    error.value = null
    try {
      const result = await loader()
      if (my === seq) data.value = result
    } catch (e) {
      if (my === seq) error.value = e instanceof Error ? e.message : String(e)
    } finally {
      if (my === seq) loading.value = false
    }
  }

  if (options.immediate !== false) {
    onMounted(() => {
      void load()
    })
  }
  watch(
    () => ui.refreshTick,
    () => {
      void load(true)
    },
  )

  return { data, loading, error, load }
}

/** 统一把异常转成可读文案 */
export function errorText(e: unknown): string {
  if (e instanceof Error) return e.message
  return String(e)
}
