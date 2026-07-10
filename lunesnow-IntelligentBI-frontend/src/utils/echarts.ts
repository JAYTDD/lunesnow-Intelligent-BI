// 统一按需引入 ECharts，所有页面从这个模块导入，避免重复打包全量 echarts。
// 业务涉及的图表类型固定为：折线 / 柱状 / 饼 / 散点 / 雷达。
// 如需支持更多类型，在下方 use([...]) 中追加对应 Chart / Component 即可。
import * as echarts from 'echarts/core'
import {
  BarChart,
  LineChart,
  PieChart,
  ScatterChart,
  RadarChart,
} from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  LegendScrollComponent,
  AxisPointerComponent,
  TitleComponent,
  DataZoomComponent,
  VisualMapComponent,
  ToolboxComponent,
  RadarComponent,
  DatasetComponent,
  MarkPointComponent,
  MarkLineComponent,
  MarkAreaComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  ScatterChart,
  RadarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  LegendScrollComponent,
  AxisPointerComponent,
  TitleComponent,
  DataZoomComponent,
  VisualMapComponent,
  ToolboxComponent,
  RadarComponent,
  DatasetComponent,
  MarkPointComponent,
  MarkLineComponent,
  MarkAreaComponent,
  CanvasRenderer,
])

// 透传 echarts/core 的所有导出（含 init / getInstanceByDom / ECharts 类型等），
// 这样调用方可以用 `import * as echarts from '@/utils/echarts'` 原样使用。
export * from 'echarts/core'
