// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /chart/add */
export async function addChart(body: API.ChartAddRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/chart/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chart/delete */
export async function deleteChart(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/chart/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chart/edit */
export async function editChart(body: API.ChartEditRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/chart/edit', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chart/gen */
export async function getChartByAi(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getChartByAIParams,
  body: {},
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBiResponse>('/chart/gen', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    params: {
      ...params,
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chart/get/data/${param0} */
export async function getChartData(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getChartDataParams,
  options?: { [key: string]: any }
) {
  const { chartId: param0, ...queryParams } = params
  return request<API.BaseResponseListMapStringString>(`/chart/get/data/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chart/get/vo */
export async function getChartVoById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getChartVOByIdParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseChartVO>('/chart/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chart/list/page */
export async function listChartByPage(
  body: API.ChartQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageChart>('/chart/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chart/list/page/vo */
export async function listChartVoByPage(
  body: API.ChartQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageChartVO>('/chart/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chart/my/list/page/vo */
export async function listMyChartVoByPage(
  body: API.ChartQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageChartVO>('/chart/my/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /chart/update */
export async function updateChart(body: API.ChartUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/chart/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
