/**
 * 图表配置校验工具
 * 对 AI 返回的 ECharts 配置进行运行时校验、容错解析与危险字段过滤
 */

/**
 * 校验结果
 */
export interface ValidationResult {
  valid: boolean
  error?: string
}

/**
 * 危险字段列表（防止原型链污染）
 */
const DANGEROUS_FIELDS = [
  '__proto__',
  'constructor',
  'prototype',
  'eval',
  'Function',
  'setTimeout',
  'setInterval',
  'fetch',
  'XMLHttpRequest',
]

/**
 * 安全解析 AI 返回的图表配置
 * 支持 JSON 格式和 JavaScript 对象格式
 */
export function safeParseChartConfig(raw: string | null | undefined): any | null {
  if (!raw || typeof raw !== 'string') {
    return null
  }

  // 1. 去除空白字符
  const trimmed = raw.trim()
  if (!trimmed) {
    return null
  }

  // 第一次尝试：JSON.parse（标准格式）
  try {
    const result = JSON.parse(trimmed)
    return filterDangerousFields(result)
  } catch {
    // 继续尝试
  }

  // 第二次尝试：移除 option = 前缀后解析
  try {
    const cleaned = trimmed
      .replace(/^(?:let|var|const)?\s*option\s*=\s*/, '')
      .replace(/;$/, '')
      .trim()
    const result = JSON.parse(cleaned)
    return filterDangerousFields(result)
  } catch {
    // 继续尝试
  }

  // 第三次尝试：new Function（JavaScript 对象格式）
  try {
    // 只允许返回对象字面量，不执行其他代码
    const result = new Function('return ' + trimmed)()
    if (typeof result === 'object' && result !== null) {
      return filterDangerousFields(result)
    }
    return null
  } catch {
    return null
  }
}

/**
 * 过滤危险字段
 */
function filterDangerousFields(obj: any): any {
  if (typeof obj !== 'object' || obj === null) {
    return obj
  }

  // 处理数组
  if (Array.isArray(obj)) {
    return obj.map(item => filterDangerousFields(item))
  }

  // 处理对象
  const cleaned: any = {}
  for (const key of Object.keys(obj)) {
    if (!DANGEROUS_FIELDS.includes(key)) {
      cleaned[key] = filterDangerousFields(obj[key])
    }
  }
  return cleaned
}

/**
 * 校验 ECharts 配置是否有效
 */
export function validateEChartsOption(option: any): ValidationResult {
  // 1. 基本类型检查
  if (option === null || option === undefined) {
    return { valid: false, error: '图表配置为空' }
  }

  if (typeof option !== 'object') {
    return { valid: false, error: '图表配置格式错误' }
  }

  // 2. 必须包含 series 或 data
  if (!option.series && !option.data && !option.dataset) {
    return { valid: false, error: '缺少必要的数据配置（series/data/dataset）' }
  }

  // 3. series 必须是数组（如果存在）
  if (option.series !== undefined) {
    if (!Array.isArray(option.series)) {
      return { valid: false, error: 'series 配置格式错误，应为数组' }
    }
    if (option.series.length === 0) {
      return { valid: false, error: 'series 配置为空' }
    }
  }

  // 4. 检查是否有类型定义
  if (option.series && Array.isArray(option.series)) {
    const hasType = option.series.some((s: any) => s && s.type)
    if (!hasType) {
      return { valid: false, error: 'series 中缺少 type 定义' }
    }
  }

  return { valid: true }
}

/**
 * 安全渲染图表（解析 + 校验 + 渲染）
 * 返回渲染结果或错误信息
 */
export function safeRenderChart(
  raw: string | null | undefined,
  renderFn: (option: any) => void
): { success: boolean; error?: string } {
  // 1. 解析配置
  const option = safeParseChartConfig(raw)
  if (option === null) {
    return { success: false, error: '图表配置解析失败' }
  }

  // 2. 校验配置
  const { valid, error } = validateEChartsOption(option)
  if (!valid) {
    return { success: false, error }
  }

  // 3. 执行渲染
  try {
    renderFn(option)
    return { success: true }
  } catch (e: any) {
    return { success: false, error: `渲染失败: ${e.message}` }
  }
}
