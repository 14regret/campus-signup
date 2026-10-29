import { App as AntdApp, Button, Empty } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchActivityDetail } from '../../api/activity'
import { fetchMySignups, signUp } from '../../api/signup'
import type { ActivityDetail, SignupStatus } from '../../api/types'
import AppHeader from '../../components/AppHeader'
import QuotaMeter from '../../components/QuotaMeter'
import StatusChip from '../../components/StatusChip'
import { ClockIcon } from '../../components/icons'
import { PHASE_UI, resolvePhase } from '../../utils/activityPhase'
import './index.css'

function formatTime(value: string): string {
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

export default function ActivityDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { message } = AntdApp.useApp()

  const [activity, setActivity] = useState<ActivityDetail | null>(null)
  const [myStatus, setMyStatus] = useState<SignupStatus | undefined>(undefined)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [applying, setApplying] = useState(false)

  const load = useCallback(async () => {
    if (!id) return
    const [detail, mySignups] = await Promise.all([
      fetchActivityDetail(id),
      // 「我报没报名」和活动详情互不依赖，并行拿，少一次串行等待
      fetchMySignups().catch(() => []),
    ])
    setActivity(detail)
    setMyStatus(mySignups.find((signup) => signup.activityId === detail.id)?.status)
  }, [id])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setFailed(false)

    load()
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [load])

  const handleApply = useCallback(async () => {
    if (!activity) return
    setApplying(true)
    try {
      const result = await signUp(activity.id)
      setMyStatus(result.status)
      // 用接口返回的剩余名额就地更新，避免整页重新加载造成的闪动
      setActivity((prev) =>
        prev
          ? { ...prev, remaining: result.remaining, signedUpCount: prev.capacity - result.remaining }
          : prev,
      )
      message.success('报名成功，请等待管理员确认')
    } catch {
      // 名额满 / 重复报名等错误已由拦截器提示
    } finally {
      setApplying(false)
    }
  }, [activity, message])

  if (loading) {
    return (
      <div className="detail">
        <AppHeader />
        <main className="detail__main">
          <div className="detail__skeleton">
            <div className="sk sk--title" />
            <div className="sk sk--line" />
            <div className="sk sk--line sk--short" />
            <div className="sk sk--block" />
          </div>
        </main>
      </div>
    )
  }

  if (failed || !activity) {
    return (
      <div className="detail">
        <AppHeader />
        <main className="detail__main">
          <div className="detail__state">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span className="detail__state-text">
                  这个活动看不到
                  <span className="detail__state-hint">可能已下架、被取消，或者链接不对</span>
                </span>
              }
            />
            <Link to="/">
              <Button>返回活动列表</Button>
            </Link>
          </div>
        </main>
      </div>
    )
  }

  const ui = PHASE_UI[resolvePhase(activity, myStatus)]
  const paragraphs = (activity.description ?? '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  return (
    <div className="detail">
      <AppHeader />

      <main className="detail__main">
        <Link className="detail__back" to="/">
          ← 返回活动列表
        </Link>

        <div className="detail__grid">
          <section className="detail__content">
            <div className="detail__head">
              <StatusChip tone={ui.tone}>{ui.chip}</StatusChip>
              <h1 className="detail__title">{activity.title}</h1>
            </div>

            <dl className="detail__facts">
              <div className="detail__fact">
                <dt>活动时间</dt>
                <dd className="mono">
                  {formatTime(activity.startAt)} — {dayjs(activity.endAt).format('HH:mm')}
                </dd>
              </div>
              <div className="detail__fact">
                <dt>报名截止</dt>
                <dd className="mono">{formatTime(activity.signupEndAt)}</dd>
              </div>
            </dl>

            <section className="detail__desc">
              <h2>活动介绍</h2>
              {paragraphs.length > 0 ? (
                paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)
              ) : (
                <p className="detail__desc-empty">管理员还没有填写活动介绍。</p>
              )}
            </section>
          </section>

          <aside className="detail__aside">
            <div className="panel">
              <div className="panel__head">
                <span className="panel__label">剩余名额</span>
                <div className="panel__num">
                  {Math.max(activity.remaining, 0)}
                  <span className="panel__cap">/ {activity.capacity}</span>
                </div>
              </div>

              <QuotaMeter
                capacity={activity.capacity}
                signedUp={activity.signedUpCount}
                remaining={activity.remaining}
                showMeta={false}
              />

              <div className="panel__deadline">
                <ClockIcon />
                <span className="mono">报名截止 {formatTime(activity.signupEndAt)}</span>
              </div>

              <Button
                block
                size="large"
                type={ui.disabled ? 'default' : 'primary'}
                disabled={ui.disabled}
                loading={applying}
                onClick={handleApply}
              >
                {ui.action}
              </Button>

              <p className="panel__hint">报名后需管理员确认，24 小时内未确认将自动取消并释放名额。</p>
            </div>
          </aside>
        </div>
      </main>

      {/* 窄屏：操作栏沉到底部固定条，避免长描述把按钮顶出屏幕 */}
      <div className="detail__bar">
        <span className="detail__bar-num">
          剩余 <b className="mono">{Math.max(activity.remaining, 0)}</b>
        </span>
        <Button
          type={ui.disabled ? 'default' : 'primary'}
          disabled={ui.disabled}
          loading={applying}
          onClick={handleApply}
        >
          {ui.action}
        </Button>
      </div>
    </div>
  )
}
