/**
 * 手写的线性图标。
 *
 * 项目没有装 @ant-design/icons，与其用 emoji / Unicode 符号凑（规范里明确禁止），
 * 不如自己画：统一 16 视窗、1.3 描边、round 端点，保证同一套笔触。
 */

interface IconProps {
  size?: number
  className?: string
}

const BASE = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.3,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

export function ClockIcon({ size = 14, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" className={className} {...BASE}>
      <circle cx="8" cy="8" r="6.2" />
      <path d="M8 4.8V8l2.2 1.45" />
    </svg>
  )
}

export function CalendarIcon({ size = 14, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" className={className} {...BASE}>
      <rect x="2.2" y="3.6" width="11.6" height="10.2" rx="2" />
      <path d="M2.2 6.8h11.6M5.6 2.2v2.6M10.4 2.2v2.6" />
    </svg>
  )
}

export function SearchIcon({ size = 15, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" className={className} {...BASE}>
      <circle cx="7.2" cy="7.2" r="4.6" />
      <path d="M10.6 10.6 13.6 13.6" />
    </svg>
  )
}
