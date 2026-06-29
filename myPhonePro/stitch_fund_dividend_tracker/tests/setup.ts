/**
 * Vitest setup file
 * 初始化测试环境，模拟浏览器 API
 */

// Mock localStorage
const store: Record<string, string> = {}
Object.defineProperty(window, 'localStorage', {
  value: {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { Object.keys(store).forEach(k => delete store[k]) },
    get length() { return Object.keys(store).length },
    key: (i: number) => Object.keys(store)[i] ?? null,
  },
  writable: true,
})

// Mock fetch (每个测试用例会覆盖)
;(globalThis as any).fetch = vi.fn()
