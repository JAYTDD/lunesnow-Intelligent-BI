export interface DashboardItem {
  id: string // 唯一标识 (chart_数据库ID 或自生成)
  chartId?: number // 数据库图表 ID
  name: string
  type: string
  genChart?: string // ECharts 配置 JSON
  x: number
  y: number
  width: number
  height: number
}
