import './index.css'

interface QuotaMeterProps {
  capacity: number
  signedUp: number
  remaining: number
  /** 详情页的操作栏自己展示大号数字，这里只需要一条轨道 */
  showMeta?: boolean
}

/** 剩余名额低于 10% 时转为琥珀，并换一句更紧迫的文案 */
const LOW_RATIO = 0.1

/**
 * 名额进度条（DESIGN.md 里的 Signature Component）。
 *
 * 它不显示"报名进度"这种抽象百分比，而是回答用户唯一在意的问题：还剩几个。
 * 数字用等宽 + tabular-nums，名额从 9 变成 10 时宽度不变，卡片不会抖。
 */
export default function QuotaMeter({
  capacity,
  signedUp,
  remaining,
  showMeta = true,
}: QuotaMeterProps) {
  const safeCapacity = Math.max(capacity, 1)
  const percent = Math.min(100, Math.max(0, (signedUp / safeCapacity) * 100))
  const low = remaining > 0 && remaining <= Math.max(1, Math.ceil(safeCapacity * LOW_RATIO))

  return (
    <div className={low ? 'qm qm--low' : 'qm'}>
      <div
        className="qm__track"
        role="progressbar"
        aria-label="名额使用情况"
        aria-valuemin={0}
        aria-valuemax={capacity}
        aria-valuenow={signedUp}
      >
        <div className="qm__fill" style={{ width: `${percent}%` }} />
      </div>

      {showMeta ? (
        <div className="qm__meta">
          <span>
            已报 <span className="qm__num">{signedUp}</span>
            <span className="qm__sep"> / </span>
            <span className="qm__num">{capacity}</span>
          </span>
          <span className="qm__left">
            {remaining <= 0 ? '已满' : low ? `仅剩 ${remaining} 个` : `剩余 ${remaining}`}
          </span>
        </div>
      ) : null}
    </div>
  )
}
