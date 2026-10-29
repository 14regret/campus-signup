import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import type { ActivitySummary, SignupStatus } from '../api/types'

/**
 * 活动的展示态。
 *
 * 前两个不是活动状态，而是「我和这个活动的关系」——
 * 它们优先于报名窗口判断：已经报上名的人不应该再看到「申请报名」。
 */
export type ActivityPhase = 'applied' | 'confirmed' | 'open' | 'soon' | 'full' | 'closed'

export type PhaseTone = 'accent' | 'warning' | 'neutral'

export interface PhaseUi {
  chip: string
  tone: PhaseTone
  action: string
  disabled: boolean
}

export const PHASE_UI: Record<ActivityPhase, PhaseUi> = {
  applied: { chip: '待确认', tone: 'warning', action: '已报名 · 待确认', disabled: true },
  confirmed: { chip: '已报名', tone: 'accent', action: '已报名', disabled: true },
  open: { chip: '报名中', tone: 'accent', action: '申请报名', disabled: false },
  soon: { chip: '即将开始', tone: 'neutral', action: '报名未开始', disabled: true },
  full: { chip: '已满', tone: 'warning', action: '名额已满', disabled: true },
  closed: { chip: '报名截止', tone: 'neutral', action: '报名已截止', disabled: true },
}

/**
 * 推导展示态。
 *
 * 顺序很重要：先看我的报名，再看活动状态，最后才是名额与时间。
 * 反过来会出现「我已经报上了，但卡片还显示名额已满」这种自相矛盾的画面。
 */
export function resolvePhase(
  activity: ActivitySummary,
  myStatus: SignupStatus | undefined,
  now: Dayjs = dayjs(),
): ActivityPhase {
  if (myStatus === 'PENDING') {
    return 'applied'
  }
  if (myStatus === 'CONFIRMED') {
    return 'confirmed'
  }
  if (activity.status !== 'PUBLISHED') {
    return 'closed'
  }
  if (activity.remaining <= 0) {
    return 'full'
  }
  if (activity.signupOpen) {
    return 'open'
  }
  return now.isBefore(dayjs(activity.signupStartAt)) ? 'soon' : 'closed'
}
