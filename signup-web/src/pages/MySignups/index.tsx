import { App as AntdApp, Button, Empty, Popconfirm } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { cancelSignup, fetchMySignups } from '../../api/signup'
import type { MySignup } from '../../api/types'
import AppHeader from '../../components/AppHeader'
import StatusChip from '../../components/StatusChip'
import { SIGNUP_STATUS_UI } from '../../utils/status'
import './index.css'

function formatTime(value: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

/**
 * 我的报名。
 *
 * 这一页的情绪是「等待」：待确认的条目要比其他状态更显眼，
 * 并且明确告诉用户「还需要等多久、等不到会怎样」，否则用户会反复刷新页面。
 */
export default function MySignupsPage() {
  const { message } = AntdApp.useApp()

  const [items, setItems] = useState<MySignup[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [cancellingId, setCancellingId] = useState<number | null>(null)

  const load = useCallback(async () => {
    const list = await fetchMySignups()
    setItems(list)
  }, [])

  const reload = useCallback(() => {
    setLoading(true)
    setFailed(false)
    load()
      .catch(() => setFailed(true))
      .finally(() => setLoading(false))
  }, [load])

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

  const handleCancel = async (signupId: number) => {
    setCancellingId(signupId)
    try {
      await cancelSignup(signupId)
      message.success('已取消报名，名额已释放')
      await load()
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setCancellingId(null)
    }
  }

  const pendingCount = items.filter((item) => item.status === 'PENDING').length

  return (
    <div className="page">
      <AppHeader />

      <main className="page__main">
        <header className="page__head">
          <div>
            <h1 className="page__title">我的报名</h1>
            <p className="page__sub">
              {loading
                ? '正在加载…'
                : pendingCount > 0
                  ? `共 ${items.length} 条，其中 ${pendingCount} 条等待确认`
                  : `共 ${items.length} 条报名记录`}
            </p>
          </div>
        </header>

        {failed ? (
          <div className="page__state">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={<span className="page__state-text">报名记录没能加载出来</span>}
            />
            <Button onClick={reload}>重新加载</Button>
          </div>
        ) : null}

        {!failed && !loading && items.length === 0 ? (
          <div className="page__state">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span className="page__state-text">
                  还没有报名任何活动
                  <span className="page__state-hint">去看看近期活动，感兴趣的直接申请</span>
                </span>
              }
            />
            <Link to="/">
              <Button type="primary">浏览活动</Button>
            </Link>
          </div>
        ) : null}

        <div className="ms-list">
          {loading
            ? Array.from({ length: 3 }, (_, index) => (
                <div className="ms-row ms-row--skeleton" key={index} aria-hidden="true">
                  <div className="sk sk--title" />
                  <div className="sk sk--line" />
                </div>
              ))
            : null}

          {!loading && !failed
            ? items.map((item) => {
                const ui = SIGNUP_STATUS_UI[item.status]
                const canCancel = item.status === 'PENDING' || item.status === 'CONFIRMED'
                const linkable = item.activityStatus === 'PUBLISHED'

                return (
                  <article
                    className={item.status === 'PENDING' ? 'ms-row ms-row--pending' : 'ms-row'}
                    key={item.signupId}
                  >
                    <div className="ms-row__main">
                      <h3 className="ms-row__title">
                        {linkable ? (
                          <Link to={`/activity/${item.activityId}`}>{item.activityTitle}</Link>
                        ) : (
                          <span>{item.activityTitle ?? '活动已删除'}</span>
                        )}
                      </h3>

                      <div className="ms-row__meta">
                        <span>
                          提交于 <span className="mono">{formatTime(item.createdAt)}</span>
                        </span>
                        <span>
                          活动时间 <span className="mono">{formatTime(item.activityStartAt)}</span>
                        </span>
                      </div>

                      {item.status === 'PENDING' ? (
                        <p className="ms-row__hint">
                          等待管理员确认 · 24 小时内未确认将自动取消并释放名额
                        </p>
                      ) : null}
                    </div>

                    <div className="ms-row__side">
                      <StatusChip tone={ui.tone}>{ui.label}</StatusChip>
                      {canCancel ? (
                        <Popconfirm
                          title="取消这条报名？"
                          description="名额会立即释放，如需参加需要重新申请。"
                          okText="确认取消"
                          cancelText="保留报名"
                          okButtonProps={{ danger: true }}
                          onConfirm={() => handleCancel(item.signupId)}
                        >
                          <Button size="small" loading={cancellingId === item.signupId}>
                            取消报名
                          </Button>
                        </Popconfirm>
                      ) : null}
                    </div>
                  </article>
                )
              })
            : null}
        </div>
      </main>
    </div>
  )
}
