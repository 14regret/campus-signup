import { App as AntdApp, ConfigProvider, theme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { RouterProvider } from 'react-router-dom'
import router from './router'

/**
 * 应用根组件。
 *
 * - darkAlgorithm：整个组件库切到暗色基线，避免只在自定义 CSS 里做暗色、控件却是白的；
 * - token 与 src/styles/tokens.css 同源，改色时两边一起改；
 * - AntdApp 提供 message/modal 的上下文实例，页面里用 App.useApp() 取，
 *   不用静态 message（静态方法拿不到主题上下文）。
 */
export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#38E1C8',
          colorInfo: '#38E1C8',
          colorLink: '#38E1C8',
          colorSuccess: '#46D98B',
          colorWarning: '#FFB454',
          colorError: '#FF5F6D',
          colorBgBase: '#07080D',
          colorBgContainer: 'rgba(255,255,255,0.045)',
          colorBgElevated: '#0C0E16',
          colorBorder: 'rgba(255,255,255,0.10)',
          colorBorderSecondary: 'rgba(255,255,255,0.08)',
          colorText: '#F2F4F9',
          colorTextSecondary: '#A6AEC4',
          colorTextTertiary: '#6E768C',
          colorTextLightSolid: '#07080D',
          borderRadius: 6,
          fontFamily:
            "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, -apple-system, sans-serif",
          fontSize: 14,
        },
        components: {
          Button: {
            controlHeight: 40,
            paddingInline: 20,
            fontWeight: 500,
          },
          Input: {
            colorBgContainer: '#131624',
            activeBorderColor: 'rgba(56,225,200,0.45)',
            hoverBorderColor: 'rgba(56,225,200,0.32)',
            activeShadow: '0 0 0 3px rgba(56,225,200,0.12)',
          },
        },
      }}
    >
      <AntdApp>
        <RouterProvider router={router} />
      </AntdApp>
    </ConfigProvider>
  )
}
