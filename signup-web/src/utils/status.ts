import type { ActivityStatus, SignupStatus } from '../api/types'
import type { PhaseTone } from './activityPhase'

export interface StatusUi {
  label: string
  tone: PhaseTone
}

/** 报名状态：待确认要显眼，已取消/已拒绝是终态、低调处理 */
export const SIGNUP_STATUS_UI: Record<SignupStatus, StatusUi> = {
  PENDING: { label: '待确认', tone: 'warning' },
  CONFIRMED: { label: '已确认', tone: 'accent' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  REJECTED: { label: '已拒绝', tone: 'neutral' },
}

/** 活动状态（管理端用） */
export const ACTIVITY_STATUS_UI: Record<ActivityStatus, StatusUi> = {
  DRAFT: { label: '草稿', tone: 'neutral' },
  PUBLISHED: { label: '已发布', tone: 'accent' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  FINISHED: { label: '已结束', tone: 'neutral' },
}
