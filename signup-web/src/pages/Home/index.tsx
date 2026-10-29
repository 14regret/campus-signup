import { App as AntdApp, Button, Empty, Input } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { fetchActivities } from '../../api/activity'
import { fetchMySignups, signUp } from '../../api/signup'
import type { ActivitySummary, SignupStatus } from '../../api/types'
import ActivityCard from '../../components/ActivityCard'
import AppHeader from '../../components/AppHeader'
import { SearchIcon } from '../../components/icons'
import './index.css'

/** 9 条正好铺满 3 列 × 3 行 */
const PAGE_SIZE = 9
const SEARCH_DEBOUNCE = 300

export default function HomePage() {
  const { message } = AntdApp.useApp()

  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')
  const [reloadToken, setReloadToken] = useState(0)

  const [items, setItems] = useState<ActivitySummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [failed, setFailed] = useState(false)

  /** activityId -> 我的报名状态，决定卡片按钮显示什么 */
  const [myStatuses, setMyStatuses] = useState<Record<number, SignupStatus>>({})
  const [applyingId, setApplyingId] = useState<number | null>(null)

  // 输入防抖：每敲一个字就请求一次会把后端打穿
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(keyword.trim()), SEARCH_DEBOUNCE)
    return () => window.clearTimeout(timer)
  }, [keyword])

  // 我的报名状态只跟用户有关，和搜索/分页无关，所以单独拉一次
  useEffect(() => {
    let cancelled = false
    fetchMySignups()
      .then((list) => {
        if (cancelled) return
        setMyStatuses(
          Object.fromEntries(list.map((signup) => [signup.activityId, signup.status])) as Record<
            number,
            SignupStatus
          >,
        )
      })
      .catch(() => {
        // 拉不到就当作「都没报名」，不影响浏览；真正报名时后端还会再校验一次
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 首屏与搜索词变化：都从第 1 页重新拉
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setFailed(false)

    fetchActivities({ page: 1, size: PAGE_SIZE, keyword: query || undefined })
      .then((result) => {
        if (cancelled) return
        setItems(result.records)
        setTotal(result.total)
        setPage(1)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [query, reloadToken])

  const handleLoadMore = useCallback(async () => {
    setLoadingMore(true)
    try {
      const next = page + 1
      const result = await fetchActivities({ page: next, size: PAGE_SIZE, keyword: query || undefined })
      // 偏移分页期间如果有人新建了活动，下一页可能和上一页重叠，按 id 去重
      setItems((prev) => {
        const seen = new Set(prev.map((item) => item.id))
        return [...prev, ...result.records.filter((item) => !seen.has(item.id))]
      })
      setTotal(result.total)
      setPage(next)
    } catch {
      // 错误提示已由 Axios 拦截器统一处理
    } finally {
      setLoadingMore(false)
    }
  }, [page, query])

  /**
   * 直接在列表里报名。
   * 不跳详情页——用户可能只是想顺手占个座，没必要打断。
   * 成功后用接口返回的 remaining 就地更新这一张卡，不整页刷新。
   */
  const handleApply = useCallback(
    async (activity: ActivitySummary) => {
      setApplyingId(activity.id)
      try {
        const result = await signUp(activity.id)
        setMyStatuses((prev) => ({ ...prev, [activity.id]: result.status }))
        setItems((prev) =>
          prev.map((item) =>
            item.id === activity.id
              ? {
                  ...item,
                  remaining: result.remaining,
                  signedUpCount: item.capacity - result.remaining,
                }
              : item,
          ),
        )
        message.success('报名成功，请等待管理员确认')
      } catch {
        // 名额满 / 重复报名等错误已由拦截器提示
      } finally {
        setApplyingId(null)
      }
    },
    [message],
  )

  const firstLoading = loading && items.length === 0
  const hasMore = items.length < total
  const showEmpty = !loading && !failed && items.length === 0
  const showList = !failed && items.length > 0

  return (
    <div className="home">
      <AppHeader />

      <main className="home__main">
        <div className="home__head">
          <div className="home__intro">
            <h1 className="home__title">近期活动</h1>
            <p className="home__sub" aria-live="polite">
              {firstLoading ? '正在加载活动…' : `共 ${total} 个活动开放报名`}
            </p>
          </div>

          <Input
            className="home__search"
            size="large"
            allowClear
            placeholder="搜索活动名称"
            prefix={<SearchIcon />}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>

        {failed ? (
          <div className="home__state">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span className="home__state-text">
                  活动列表没能加载出来
                  <span className="home__state-hint">检查网络连接后重试</span>
                </span>
              }
            />
            <Button onClick={() => setReloadToken((token) => token + 1)}>重新加载</Button>
          </div>
        ) : null}

        {showEmpty ? (
          <div className="home__state">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span className="home__state-text">
                  {query ? `没有找到与「${query}」匹配的活动` : '暂时没有开放报名的活动'}
                  <span className="home__state-hint">
                    {query ? '换个关键词试试，或清空搜索' : '管理员发布后，活动会出现在这里'}
                  </span>
                </span>
              }
            />
          </div>
        ) : null}

        <div className="home__grid">
          {firstLoading
            ? Array.from({ length: 6 }, (_, index) => <CardSkeleton key={index} />)
            : null}

          {showList
            ? items.map((activity) => (
                <ActivityCard
                  key={activity.id}
                  activity={activity}
                  myStatus={myStatuses[activity.id]}
                  applying={applyingId === activity.id}
                  onApply={handleApply}
                />
              ))
            : null}
        </div>

        {showList ? (
          <div className="home__more">
            {hasMore ? (
              <Button size="large" loading={loadingMore} onClick={handleLoadMore}>
                加载更多
              </Button>
            ) : (
              <span className="home__end">已经到底了</span>
            )}
          </div>
        ) : null}
      </main>
    </div>
  )
}

/** 与真实卡片同构的骨架，避免加载完成后高度跳变 */
function CardSkeleton() {
  return (
    <div className="ac ac--skeleton" aria-hidden="true">
      <div className="sk sk--title" />
      <div className="sk sk--line" />
      <div className="sk sk--line sk--short" />
      <div className="sk sk--bar" />
      <div className="sk__foot">
        <div className="sk sk--chip" />
        <div className="sk sk--btn" />
      </div>
    </div>
  )
}
