import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Table,
} from 'antd'
import type { TableColumnsType } from 'antd'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  cancelActivity,
  createActivity,
  fetchAdminActivities,
  fetchAdminActivityDetail,
  publishActivity,
  updateActivity,
} from '../../api/admin'
import type { ActivityPayload, ActivityStatus, ActivitySummary } from '../../api/types'
import AppHeader from '../../components/AppHeader'
import StatusChip from '../../components/StatusChip'
import { ACTIVITY_STATUS_UI } from '../../utils/status'
import './index.css'

const PAGE_SIZE = 10
const TIME_FORMAT = 'YYYY-MM-DD HH:mm'

interface ActivityFormValues {
  title: string
  description?: string
  capacity: number
  signupStartAt: Dayjs
  signupEndAt: Dayjs
  startAt: Dayjs
  endAt: Dayjs
}

/**
 * 管理后台 · 活动列表。
 *
 * 这一页是「操作台」：密度优先，所有状态的活动放在同一张表里，
 * 状态决定哪些按钮可用（草稿才能编辑/发布），避免管理员点出一堆 409。
 */
export default function AdminPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [form] = Form.useForm<ActivityFormValues>()

  const [rows, setRows] = useState<ActivitySummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState<ActivityStatus | undefined>(undefined)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(true)

  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    const result = await fetchAdminActivities({
      status: statusFilter,
      keyword: keyword || undefined,
      page,
      size: PAGE_SIZE,
    })
    setRows(result.records)
    setTotal(result.total)
  }, [statusFilter, keyword, page])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    load()
      .catch(() => {
        /* 拦截器已提示 */
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [load])

  // ---------------------------------------------------------------- 弹窗

  const openCreate = () => {
    setEditingId(null)
    const base = dayjs().add(1, 'day').startOf('hour')
    form.setFieldsValue({
      title: '',
      description: '',
      capacity: 50,
      signupStartAt: base,
      signupEndAt: base.add(2, 'day'),
      startAt: base.add(4, 'day'),
      endAt: base.add(4, 'day').add(2, 'hour'),
    })
    setModalOpen(true)
  }

  const openEdit = async (id: number) => {
    try {
      const detail = await fetchAdminActivityDetail(id)
      setEditingId(id)
      form.setFieldsValue({
        title: detail.title,
        description: detail.description ?? '',
        capacity: detail.capacity,
        signupStartAt: dayjs(detail.signupStartAt),
        signupEndAt: dayjs(detail.signupEndAt),
        startAt: dayjs(detail.startAt),
        endAt: dayjs(detail.endAt),
      })
      setModalOpen(true)
    } catch {
      /* 拦截器已提示 */
    }
  }

  const handleSubmit = async () => {
    let values: ActivityFormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }

    const payload: ActivityPayload = {
      title: values.title.trim(),
      description: values.description?.trim() || null,
      capacity: values.capacity,
      signupStartAt: values.signupStartAt.format('YYYY-MM-DDTHH:mm:ss'),
      signupEndAt: values.signupEndAt.format('YYYY-MM-DDTHH:mm:ss'),
      startAt: values.startAt.format('YYYY-MM-DDTHH:mm:ss'),
      endAt: values.endAt.format('YYYY-MM-DDTHH:mm:ss'),
    }

    setSaving(true)
    try {
      if (editingId === null) {
        await createActivity(payload)
        message.success('活动已创建，当前为草稿，确认无误后点「发布」')
      } else {
        await updateActivity(editingId, payload)
        message.success('活动已更新')
      }
      setModalOpen(false)
      await load()
    } catch {
      /* 拦截器已提示 */
    } finally {
      setSaving(false)
    }
  }

  // ---------------------------------------------------------------- 行操作

  const handlePublish = async (id: number) => {
    try {
      await publishActivity(id)
      message.success('活动已发布，学生现在可以申请报名')
      await load()
    } catch {
      /* 拦截器已提示 */
    }
  }

  const handleCancel = async (id: number) => {
    try {
      await cancelActivity(id)
      message.success('活动已取消，相关报名已一并取消')
      await load()
    } catch {
      /* 拦截器已提示 */
    }
  }

  const columns: TableColumnsType<ActivitySummary> = [
    {
      title: '活动',
      dataIndex: 'title',
      render: (_, record) => (
        <Link className="admin__title-link" to={`/admin/activities/${record.id}/signups`}>
          {record.title}
        </Link>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      render: (value: ActivityStatus) => {
        const ui = ACTIVITY_STATUS_UI[value]
        return <StatusChip tone={ui.tone}>{ui.label}</StatusChip>
      },
    },
    {
      title: '名额',
      width: 110,
      render: (_, record) => (
        <span className="table-mono">
          {record.signedUpCount} / {record.capacity}
        </span>
      ),
    },
    {
      title: '报名截止',
      width: 140,
      render: (_, record) => (
        <span className="table-mono">{dayjs(record.signupEndAt).format('MM-DD HH:mm')}</span>
      ),
    },
    {
      title: '操作',
      width: 300,
      render: (_, record) => {
        const isDraft = record.status === 'DRAFT'
        const canCancel = record.status === 'DRAFT' || record.status === 'PUBLISHED'
        return (
          <div className="row-actions">
            <Button type="link" size="small" onClick={() => navigate(`/admin/activities/${record.id}/signups`)}>
              审核
            </Button>
            <Button type="link" size="small" disabled={!isDraft} onClick={() => openEdit(record.id)}>
              编辑
            </Button>
            {isDraft ? (
              <Popconfirm
                title="发布这个活动？"
                description="发布后学生即可申请报名，且不能再编辑。"
                okText="发布"
                cancelText="再想想"
                onConfirm={() => handlePublish(record.id)}
              >
                <Button type="link" size="small">
                  发布
                </Button>
              </Popconfirm>
            ) : null}
            {canCancel ? (
              <Popconfirm
                title="取消这个活动？"
                description="该活动下所有待确认/已确认的报名会被一并取消，名额清零。"
                okText="确认取消"
                cancelText="保留"
                okButtonProps={{ danger: true }}
                onConfirm={() => handleCancel(record.id)}
              >
                <Button type="link" size="small" danger>
                  取消
                </Button>
              </Popconfirm>
            ) : null}
          </div>
        )
      },
    },
  ]

  return (
    <div className="page">
      <AppHeader />

      <main className="page__main">
        <header className="page__head">
          <div>
            <h1 className="page__title">管理后台</h1>
            <p className="page__sub">共 {total} 个活动 · 草稿发布后学生才可见</p>
          </div>

          <div className="admin__toolbar">
            <Input.Search
              allowClear
              placeholder="搜索活动名称"
              className="admin__search"
              onSearch={(value) => {
                setKeyword(value.trim())
                setPage(1)
              }}
            />
            <Select
              allowClear
              placeholder="全部状态"
              className="admin__filter"
              value={statusFilter}
              onChange={(value: ActivityStatus | undefined) => {
                setStatusFilter(value)
                setPage(1)
              }}
              options={(Object.keys(ACTIVITY_STATUS_UI) as ActivityStatus[]).map((status) => ({
                value: status,
                label: ACTIVITY_STATUS_UI[status].label,
              }))}
            />
            <Button type="primary" onClick={openCreate}>
              新建活动
            </Button>
          </div>
        </header>

        <div className="table-card">
          <Table<ActivitySummary>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 880 }}
            pagination={{
              current: page,
              pageSize: PAGE_SIZE,
              total,
              showSizeChanger: false,
              onChange: setPage,
              showTotal: (count) => `共 ${count} 条`,
            }}
          />
        </div>
      </main>

      <Modal
        open={modalOpen}
        title={editingId === null ? '新建活动' : '编辑活动'}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" requiredMark={false} className="admin__form">
          <Form.Item
            label="活动标题"
            name="title"
            rules={[
              { required: true, message: '请填写活动标题' },
              { max: 120, message: '最多 120 个字' },
            ]}
          >
            <Input placeholder="例如：周末天文观测夜" />
          </Form.Item>

          <Form.Item label="活动介绍" name="description">
            <Input.TextArea rows={3} maxLength={2000} showCount placeholder="活动内容、集合地点、注意事项…" />
          </Form.Item>

          <Form.Item
            label="名额"
            name="capacity"
            rules={[{ required: true, message: '请填写名额' }]}
          >
            <InputNumber min={1} max={100000} style={{ width: 160 }} />
          </Form.Item>

          <div className="admin__time-grid">
            <Form.Item
              label="报名开始"
              name="signupStartAt"
              rules={[{ required: true, message: '请选择时间' }]}
            >
              <DatePicker showTime format={TIME_FORMAT} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="报名截止"
              name="signupEndAt"
              rules={[{ required: true, message: '请选择时间' }]}
            >
              <DatePicker showTime format={TIME_FORMAT} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="活动开始"
              name="startAt"
              rules={[{ required: true, message: '请选择时间' }]}
            >
              <DatePicker showTime format={TIME_FORMAT} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="活动结束"
              name="endAt"
              rules={[{ required: true, message: '请选择时间' }]}
            >
              <DatePicker showTime format={TIME_FORMAT} style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <p className="admin__form-hint">
            顺序要求：报名开始 ＜ 报名截止 ≤ 活动开始 ＜ 活动结束。后端会校验，填错会返回具体原因。
          </p>
        </Form>
      </Modal>
    </div>
  )
}
