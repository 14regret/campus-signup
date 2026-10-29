import { request } from '../utils/request'
import type {
  ActivityDetail,
  ActivityPayload,
  ActivityStatus,
  ActivitySummary,
  AdminSignupItem,
  BatchReviewPayload,
  BatchReviewResult,
  PageResult,
  RawPageResult,
} from './types'

/**
 * 管理端接口。
 *
 * 分页适配与 activity.ts 一致：后端 list -> 前端 records。
 * 批量审核返回的是「成功几条、失败几条」，不是抛异常，
 * 因为一条报名可能刚好被用户自己取消了，不该让整批失败。
 */
function toPage<T>(raw: RawPageResult<T>): PageResult<T> {
  return {
    records: raw.list,
    total: raw.total,
    page: raw.page,
    size: raw.size,
    totalPages: raw.totalPages,
  }
}

/* ------------------------------- 活动管理 ------------------------------- */

export async function fetchAdminActivities(params: {
  status?: ActivityStatus
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<ActivitySummary>> {
  const raw = await request.get<RawPageResult<ActivitySummary>>('/admin/activities', { params })
  return toPage(raw)
}

export function fetchAdminActivityDetail(id: number | string): Promise<ActivityDetail> {
  return request.get<ActivityDetail>(`/admin/activities/${id}`)
}

export function createActivity(payload: ActivityPayload): Promise<ActivityDetail> {
  return request.post<ActivityDetail>('/admin/activities', payload)
}

export function updateActivity(id: number | string, payload: ActivityPayload): Promise<ActivityDetail> {
  return request.put<ActivityDetail>(`/admin/activities/${id}`, payload)
}

export function publishActivity(id: number | string): Promise<ActivityDetail> {
  return request.post<ActivityDetail>(`/admin/activities/${id}/publish`)
}

export function cancelActivity(id: number | string): Promise<ActivityDetail> {
  return request.post<ActivityDetail>(`/admin/activities/${id}/cancel`)
}

/* ------------------------------- 报名审核 ------------------------------- */

export function fetchActivitySignups(activityId: number | string): Promise<AdminSignupItem[]> {
  return request.get<AdminSignupItem[]>(`/admin/activities/${activityId}/signups`)
}

export function confirmSignup(signupId: number | string): Promise<void> {
  return request.post<void>(`/admin/signups/${signupId}/confirm`)
}

export function rejectSignup(signupId: number | string): Promise<void> {
  return request.post<void>(`/admin/signups/${signupId}/reject`)
}

/** 撤销已确认的报名：CONFIRMED → CANCELLED，名额退回。 */
export function revokeSignup(signupId: number | string): Promise<void> {
  return request.post<void>(`/admin/signups/${signupId}/revoke`)
}

export function batchReview(payload: BatchReviewPayload): Promise<BatchReviewResult> {
  return request.post<BatchReviewResult>('/admin/signups/batch-review', payload)
}
