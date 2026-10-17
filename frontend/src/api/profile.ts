import request, { unwrap } from './request'

// ── 个人档案与每日消耗（ADR 0029／0030）──
// 个人档案＝身高／性别／出生日期／生活活动档，基本不变；体重仍取身体档案最新一条。

/** 某一档生活活动量（只含日常生活，**不含**专门训练）。 */
export type ActivityLevel = 'sedentary' | 'light' | 'active'

export type ProfileSex = 'male' | 'female'

/** 每日总消耗的拆解——同时也是「怎么算的」弹层的数据源（算式在服务端算，前端只展示）。 */
export interface EnergyInfo {
  date: string
  complete: boolean
  /** 缺什么：`profile`＝档案没填全，`weight`＝没有体重记录。 */
  missing?: 'profile' | 'weight' | null
  sex?: ProfileSex | null
  age?: number | null
  heightCm?: number | null
  weightKg?: number | null
  weightAt?: string | null
  activityLevel?: ActivityLevel | null
  activityFactor?: number | null
  bmr?: number | null
  /** 基础消耗 ＝ 基础代谢 × 系数。 */
  baseKcal?: number | null
  /** 当日手填的训练消耗合计。 */
  trainingKcal?: number | null
  /** 每日总消耗 ＝ baseKcal + trainingKcal。 */
  totalKcal?: number | null
}

export interface ProfileInfo {
  complete: boolean
  sex?: ProfileSex | null
  birthDate?: string | null
  age?: number | null
  heightCm?: number | null
  activityLevel?: ActivityLevel | null
  latestWeight?: number | null
  energy?: EnergyInfo | null
}

export interface ProfileSavePayload {
  sex: ProfileSex
  birthDate: string
  heightCm: number
  activityLevel: ActivityLevel
}

export function getProfile() {
  return unwrap(request.get<ProfileInfo>('/profile'))
}

export function saveProfile(payload: ProfileSavePayload) {
  return unwrap(request.put<ProfileInfo>('/profile', payload))
}
