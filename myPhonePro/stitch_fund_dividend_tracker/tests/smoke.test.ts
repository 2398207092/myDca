import { describe, it, expect } from 'vitest'

describe('测试基础设施验证', () => {
  it('vitest 正常工作', () => {
    expect(1 + 1).toBe(2)
  })

  it('jsdom 环境可用', () => {
    expect(typeof window).toBe('object')
    expect(typeof document).toBe('object')
  })

  it('localStorage mock 可用', () => {
    localStorage.setItem('test_key', 'hello')
    expect(localStorage.getItem('test_key')).toBe('hello')
    localStorage.clear()
    expect(localStorage.getItem('test_key')).toBeNull()
  })
})
