export interface DashboardItem {
  id: string // 唯一标识 (chart_数据库ID 或自生成)
  chartId?: number // 数据库图表 ID
  name: string // 图表名称
  type: string // 图表类型
  genChart?: string // ECharts 配置 JSON
  x: number // 图表位置 X 坐标
  y: number // 图表位置 Y 坐标
  width: number // 图表宽度
  height: number // 图表高度
}
