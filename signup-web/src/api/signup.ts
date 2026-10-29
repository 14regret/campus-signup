import { request } from '../utils/request'
import type { MySignup, SignupResult } from './types'

/**
 * 报名。名额满返回 409 + code 4001，重复报名返回 409 + code 4002，拦截器会弹提示。
 */
export function signUp(activityId: number | string): Promise<SignupResult> {
  return request.post<SignupResult>(`/signups/${activityId}`)
}

/** 取消报名。注意路径参数是报名记录 id（SignupResult.signupId），不是活动 id。 */
export function cancelSignup(signupId: number | string): Promise<SignupResult> {
  return request.put<SignupResult>(`/signups/${signupId}/cancel`)
}

/**
 * 我的全部报名记录。
 *
 * 前端用它来决定按钮是「申请报名」还是「已报名·待确认」。
 * 数据量很小（一个学生几十条），所以后端没做分页。
 */
export function fetchMySignups(): Promise<MySignup[]> {
  return request.get<MySignup[]>('/signups/my')
}
