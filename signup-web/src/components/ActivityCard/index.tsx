import { Button } from 'antd'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import type { ActivitySummary, SignupStatus } from '../../api/types'
import { PHASE_UI, resolvePhase } from '../../utils/activityPhase'
import QuotaMeter from '../QuotaMeter'
import StatusChip from '../StatusChip'
import { CalendarIcon, ClockIcon } from '../icons'
import './index.css'

function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm')
}

interface ActivityCardProps {
  activity: ActivitySummary
  /** 我对这个活动的报名状态，决定按钮是「申请报名」还是「已报名」 */
  myStatus?: SignupStatus
  /** 该卡片的报名请求进行中 */
  applying?: boolean
  onApply: (activity: ActivitySummary) => void
}

/**
 * 活动卡片。
 * 结构固定为「标题 → 时间 → 名额 → 状态 + 操作」，四张卡片并排时视线能横着扫过去。
 *
 * 整卡可点：用一层绝对定位的透明 Link 覆盖，而不是给 article 挂 onClick。
 * 这样键盘 Tab 能聚焦到链接、读屏能念出活动名，里面的按钮因为层级更高也不会被覆盖。
 */
export default function ActivityCard({
  activity,
  myStatus,
  applying = false,
  onApply,
}: ActivityCardProps) {
  const phase = resolvePhase(activity, myStatus)
  const ui = PHASE_UI[phase]

  return (
    <article className="ac">
      <Link
        className="ac__overlay"
        to={`/activity/${activity.id}`}
        aria-label={`查看「${activity.title}」详情`}
      />

      <h3 className="ac__title">{activity.title}</h3>

      <div className="ac__meta">
        <div className="ac__time">
          <ClockIcon />
          <span className="mono">报名截止 {formatTime(activity.signupEndAt)}</span>
        </div>
        <div className="ac__time">
          <CalendarIcon />
          <span className="mono">活动开始 {formatTime(activity.startAt)}</span>
        </div>
      </div>

      <QuotaMeter
        capacity={activity.capacity}
        signedUp={activity.signedUpCount}
        remaining={activity.remaining}
      />

      <div className="ac__foot">
        <StatusChip tone={ui.tone}>{ui.chip}</StatusChip>
        <Button
          type={ui.disabled ? 'default' : 'primary'}
          disabled={ui.disabled}
          loading={applying}
          onClick={() => onApply(activity)}
        >
          {ui.action}
        </Button>
      </div>
    </article>
  )
}
