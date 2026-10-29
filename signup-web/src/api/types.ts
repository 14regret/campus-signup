/**
 * 与后端约定的数据结构。
 *
 * 字段名与后端 DTO 保持一致（后端用 camelCase），唯一做了转换的是分页：
 * 后端 PageResult 返回的是 `list`，前端统一用 `records`，转换在 activity.ts 里做一次。
 */

/** 后端统一返回体。注意成功码是 0，不是 200（见 utils/request.ts 的兼容说明）。 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export type Role = 'USER' | 'ADMIN'

export type ActivityStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'FINISHED'

export type SignupStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'REJECTED'

/* ------------------------------- 认证 ------------------------------- */

export interface LoginParams {
  username: string
  password: string
}

export interface UserProfile {
  id: number
  username: string
  nickname: string | null
  role: Role
}

/** 后端把 token 和用户信息一起返回，role 在 user 里，不在顶层。 */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserProfile
}

/* ------------------------------- 分页 ------------------------------- */

export interface PageQuery {
  page?: number
  size?: number
  keyword?: string
}

/** 后端原始分页结构。前端业务代码不要直接用，请用 PageResult。 */
export interface RawPageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

/* ------------------------------- 活动 ------------------------------- */

export interface ActivitySummary {
  id: number
  title: string
  status: ActivityStatus
  capacity: number
  signedUpCount: number
  /** 后端算好的剩余名额，前端不要再自己减 */
  remaining: number
  /** 当前是否可报名（已发布且在报名窗口内），按钮状态直接用它 */
  signupOpen: boolean
  signupStartAt: string
  signupEndAt: string
  startAt: string
  endAt: string
}

export interface ActivityDetail extends ActivitySummary {
  description: string | null
  createdBy: number
  createdAt: string
  updatedAt: string
}

/** 创建 / 编辑活动的入参，时间统一用 ISO 字符串（不带时区，按服务器本地时间解释） */
export interface ActivityPayload {
  title: string
  description?: string | null
  capacity: number
  signupStartAt: string
  signupEndAt: string
  startAt: string
  endAt: string
}

/* ------------------------------- 报名 ------------------------------- */

export interface SignupResult {
  signupId: number
  activityId: number
  status: SignupStatus
  /** 本次操作后的剩余名额 */
  remaining: number
}

/** 「我的报名」列表项。活动信息由后端一并带回，避免前端逐条再查详情。 */
export interface MySignup {
  signupId: number
  activityId: number
  status: SignupStatus
  remark: string | null
  createdAt: string
  updatedAt: string
  activityTitle: string | null
  activityStatus: ActivityStatus | null
  activityStartAt: string | null
  activityEndAt: string | null
  signupEndAt: string | null
}

/** 管理端审核列表项。 */
export interface AdminSignupItem {
  signupId: number
  userId: number
  username: string | null
  nickname: string | null
  status: SignupStatus
  remark: string | null
  createdAt: string
}

/** CONFIRM: 待确认→已确认；REJECT: 待确认→已拒绝；REVOKE: 已确认→已取消 */
export type ReviewAction = 'CONFIRM' | 'REJECT' | 'REVOKE'

export interface BatchReviewPayload {
  signupIds: number[]
  action: ReviewAction
}

export interface BatchReviewResult {
  success: number
  failed: number
}
