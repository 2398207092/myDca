import { get, put } from './request'

export interface RiskTargetResult {
  /** {mode:"A"|"B", targets:{...}}；未设置时为 null */
  riskTargetJson: string | null
  updatedAt: string | null
}

/** 当前登录用户的目标配置；后端按 token 里的 userId 隔离 */
export async function getRiskTarget(): Promise<RiskTargetResult> {
  return get<RiskTargetResult>('/user/risk-targets')
}

export async function saveRiskTarget(riskTargetJson: string): Promise<RiskTargetResult> {
  return put<RiskTargetResult>('/user/risk-targets', { riskTargetJson })
}