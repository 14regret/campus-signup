import { request } from '../utils/request'
import type {
  ActivityDetail,
  ActivitySummary,
  PageQuery,
  PageResult,
  RawPageResult,
} from './types'

/**
 * 后端分页字段叫 list，前端统一用 records。
 * 只在这一层做一次转换，页面里永远不用关心后端的命名。
 * 将来后端改成 records 时，删掉这个函数即可。
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

/** 活动列表。只返回已发布的活动，未登录调不通（后端要求认证）。 */
export async function fetchActivities(query: PageQuery = {}): Promise<PageResult<ActivitySummary>> {
  const raw = await request.get<RawPageResult<ActivitySummary>>('/activities', { params: query })
  return toPage(raw)
}

/** 活动详情。未发布的活动后端返回 404。 */
export function fetchActivityDetail(id: number | string): Promise<ActivityDetail> {
  return request.get<ActivityDetail>(`/activities/${id}`)
}
