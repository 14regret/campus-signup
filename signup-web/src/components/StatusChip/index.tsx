import type { ReactNode } from 'react'
import './index.css'

export type ChipTone = 'accent' | 'warning' | 'neutral'

interface StatusChipProps {
  tone: ChipTone
  children: ReactNode
}

/**
 * 状态标签。
 *
 * 颜色永远伴随文字：色盲用户、灰度截图、打印出来都必须还能读懂，
 * 所以这里没有「只有颜色」的变体。
 */
export default function StatusChip({ tone, children }: StatusChipProps) {
  return (
    <span className={`chip chip--${tone}`}>
      <span className="chip__dot" aria-hidden="true" />
      {children}
    </span>
  )
}
