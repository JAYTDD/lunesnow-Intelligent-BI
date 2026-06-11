// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /rate-limit/reset */
export async function resetRateLimit(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
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

/** 此处后端没有提供注释 GET /rate-limit/status */
export async function getRateLimitStatus(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
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
