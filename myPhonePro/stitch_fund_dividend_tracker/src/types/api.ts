// ============================================================
// API 请求/响应 类型定义（仅保留被消费的类型）
// 各 api/*.ts 模块自定接口类型，此处只放共享基础设施类型
// ============================================================

/** 通用 API 响应包装 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 登录响应 */
export interface LoginResp {
  token: string
  email: string
  hasPassword: boolean
}

/** 用户信息（登录后） */
export interface UserInfoResp {
  email: string
  hasPassword: boolean
}
