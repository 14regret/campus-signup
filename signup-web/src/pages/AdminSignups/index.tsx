import { App as AntdApp, Button, Empty, Popconfirm, Table } from 'antd'
import type { TableColumnsType } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  batchReview,
  confirmSignup,
  fetchActivitySignups,
  fetchAdminActivityDetail,
  rejectSignup,
  revokeSignup,
} from '../../api/admin'
import type { ActivityDetail, AdminSignupItem, ReviewAction } from '../../api/types'
import AppHeader from '../../components/AppHeader'
import StatusChip from '../../components/StatusChip'
import { SIGNUP_STATUS_UI } from '../../utils/status'
import './index.css'

/**
 * 管理后台 · 报名审核。
 *
 * 三种动作对应三个状态入口：
 * - 待确认：可以「确认」（占位转正式）或「拒绝」（退名额）；
 * - 已确认：可以「撤销」（收回名额，用于现场调整）；
 * - 已取消/已拒绝：终态，不再提供操作。
 * 所有会改名额的动作后端都加了活动锁，这里只负责把入口摆对。
 */
export default function AdminSignupsPage() {
  const { id } = useParams<{ id: string }>()
  const { message } = AntdApp.useApp()

  const [activity, setActivity] = useState<ActivityDetail | null>(null)
  const [rows, setRows] = useState<AdminSignupItem[]>([])
  const [selected, setSelected] = useState<number[]>([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [batching, setBatching] = useState(false)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    if (!id) return
    const [detail, signups] = await Promise.all([
      fetchAdminActivityDetail(id),
      fetchActivitySignups(id),
    ])
    setActivity(detail)
    setRows(signups)
    setSelected((prev) => prev.filter((signupId) => signups.some((item) => item.signupId === signupId)))
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

  const handleSingle = async (signupId: number, action: ReviewAction) => {
    setBusyId(signupId)
    try {
      if (action === 'CONFIRM') {
        await confirmSignup(signupId)
        message.success('已确认')
      } else if (action === 'REJECT') {
        await rejectSignup(signupId)
        message.success('已拒绝，名额已释放')
      } else {
        await revokeSignup(signupId)
        message.success('已撤销，名额已释放')
      }
      await load()
    } catch {
      /* 拦截器已提示 */
    } finally {
      setBusyId(null)
    }
  }

  const handleBatch = async (action: ReviewAction) => {
    if (selected.length === 0) return
    setBatching(true)
    try {
      const result = await batchReview({ signupIds: selected, action })
      const failedPart = result.failed > 0 ? `，${result.failed} 条失败（可能已被用户取消）` : ''
      message.success(`已处理 ${result.success} 条${failedPart}`)
      setSelected([])
      await load()
    } catch {
      /* 拦截器已提示 */
    } finally {
      setBatching(false)
    }
  }

  const pendingCount = rows.filter((row) => row.status === 'PENDING').length
  const confirmedCount = rows.filter((row) => row.status === 'CONFIRMED').length

  const columns: TableColumnsType<AdminSignupItem> = [
    {
      title: '报名人',
      dataIndex: 'username',
      render: (_, record) => (
        <div className="review__person">
          <span className="review__username">{record.username ?? `#${record.userId}`}</span>
          {record.nickname ? <span className="review__nickname">{record.nickname}</span> : null}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: AdminSignupItem['status']) => {
        const ui = SIGNUP_STATUS_UI[status]
        return <StatusChip tone={ui.tone}>{ui.label}</StatusChip>
      },
    },
    {
      title: '提交时间',
      width: 150,
      render: (_, record) => (
        <span className="table-mono">{dayjs(record.createdAt).format('MM-DD HH:mm')}</span>
      ),
    },
    {
      title: '操作',
      width: 190,
      render: (_, record) => {
        if (record.status === 'PENDING') {
          return (
            <div className="row-actions">
              <Button
                type="link"
                size="small"
                loading={busyId === record.signupId}
                onClick={() => handleSingle(record.signupId, 'CONFIRM')}
              >
                确认
              </Button>
              <Popconfirm
                title="拒绝这条报名？"
                description="名额会立即释放给其他人。"
                okText="拒绝"
                cancelText="再想想"
                okButtonProps={{ danger: true }}
                onConfirm={() => handleSingle(record.signupId, 'REJECT')}
              >
                <Button type="link" size="small" danger>
                  拒绝
                </Button>
              </Popconfirm>
            </div>
          )
        }

        if (record.status === 'CONFIRMED') {
          return (
            <Popconfirm
              title="撤销这条已确认的报名？"
              description="名额会立即释放给其他人，用户需要重新申请。"
              okText="确认撤销"
              cancelText="再想想"
              okButtonProps={{ danger: true }}
              onConfirm={() => handleSingle(record.signupId, 'REVOKE')}
            >
              <Button type="link" size="small" danger loading={busyId === record.signupId}>
                撤销
              </Button>
            </Popconfirm>
          )
        }

        return <span className="review__done">已处理</span>
      },
    },
  ]

  if (failed) {
    return (
      <div className="page">
        <AppHeader />
        <main className="page__main">
          <div className="page__state">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={<span className="page__state-text">活动不存在或加载失败</span>}
            />
            <Link to="/admin">
              <Button>返回管理后台</Button>
            </Link>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="page">
      <AppHeader />

      <main className="page__main">
        <Link className="review__back" to="/admin">
          ← 返回管理后台
        </Link>

        <header className="page__head">
          <div>
            <h1 className="page__title">{activity?.title ?? '报名审核'}</h1>
            <p className="page__sub">
              待确认 <span className="mono">{pendingCount}</span> · 已确认{' '}
              <span className="mono">{confirmedCount}</span> · 名额{' '}
              <span className="mono">
                {activity?.signedUpCount ?? 0} / {activity?.capacity ?? 0}
              </span>
            </p>
          </div>

          <div className="review__batch">
            <span className="review__selected">
              已选 <span className="mono">{selected.length}</span> 条
            </span>
            <Button
              type="primary"
              disabled={selected.length === 0}
              loading={batching}
              onClick={() => handleBatch('CONFIRM')}
            >
              批量确认
            </Button>
            <Popconfirm
              title={`拒绝选中的 ${selected.length} 条报名？`}
              description="名额会立即释放给其他人。"
              okText="确认拒绝"
              cancelText="再想想"
              okButtonProps={{ danger: true }}
              disabled={selected.length === 0}
              onConfirm={() => handleBatch('REJECT')}
            >
              <Button danger disabled={selected.length === 0} loading={batching}>
                批量拒绝
              </Button>
            </Popconfirm>
          </div>
        </header>

        <div className="table-card">
          <Table<AdminSignupItem>
            rowKey="signupId"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 720 }}
            rowSelection={{
              selectedRowKeys: selected,
              onChange: (keys) => setSelected(keys.map(Number)),
              getCheckboxProps: (record) => ({ disabled: record.status !== 'PENDING' }),
            }}
            pagination={false}
            locale={{
              emptyText: (
                <span className="review__empty">还没有人报名。活动发布后，学生的申请会出现在这里。</span>
              ),
            }}
          />
        </div>
      </main>
    </div>
  )
}
