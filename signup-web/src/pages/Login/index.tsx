import { useState } from 'react'
import { Button, Card, Form, Input, Typography } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import { login } from '../../api/auth'
import type { LoginParams } from '../../api/types'
import { setStoredUser, setToken } from '../../utils/auth'

const { Title, Text } = Typography

/**
 * 登录页。
 *
 * 只做三件事：拿表单值、调登录、落 token 后跳回来源页。
 * 错误提示统一由 Axios 拦截器负责，这里不再重复弹一次。
 */
export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()

  // 被 RequireAuth 拦下来时带过来的原始路径，登录后原路返回
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const handleFinish = async (values: LoginParams) => {
    setLoading(true)
    try {
      const result = await login(values)
      setToken(result.token)
      setStoredUser(result.user)
      navigate(from, { replace: true })
    } catch {
      // 失败原因已由拦截器提示，这里只负责恢复按钮状态
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100%',
        display: 'grid',
        placeItems: 'center',
        padding: 24,
      }}
    >
      <Card style={{ width: '100%', maxWidth: 380 }} variant="outlined">
        <div style={{ marginBottom: 24 }}>
          <Title level={3} style={{ marginBottom: 4 }}>
            校园活动报名
          </Title>
          <Text type="secondary">登录后查看活动并报名</Text>
        </div>

        <Form<LoginParams> layout="vertical" onFinish={handleFinish} requiredMark={false}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="用户名" autoComplete="username" allowClear />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder="密码" autoComplete="current-password" />
          </Form.Item>

          <Button type="primary" htmlType="submit" block loading={loading} size="large">
            登录
          </Button>
        </Form>
      </Card>
    </div>
  )
}
