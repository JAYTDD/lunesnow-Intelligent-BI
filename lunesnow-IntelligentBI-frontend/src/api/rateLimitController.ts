// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 获取单个限流状态 */
export async function getRateLimitStatus(
  params: API.getRateLimitStatusParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject>('/rate-limit/status', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 获取所有限流状态 */
export async function listAllRateLimits(
  options?: { [key: string]: any }
) {
  return request<any>('/rate-limit/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 重置单个限流 */
export async function resetRateLimit(
  params: API.resetRateLimitParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/rate-limit/reset', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 批量重置所有限流 */
export async function resetAllRateLimits(
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/rate-limit/resetAll', {
    method: 'POST',
    ...(options || {}),
  })
}
