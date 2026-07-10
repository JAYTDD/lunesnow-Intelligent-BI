// ============================================================
// chartValidator 零崩溃测试
// 验证 safeParseChartConfig + filterDangerousFields + 
// validateEChartsOption 对各类异常/危险输入均不抛异常。
//
// 用法： npx jiti test/chartValidator.test.mjs
// ============================================================
import { createJiti } from 'jiti'

const jiti = createJiti(import.meta.url)
const { safeParseChartConfig, validateEChartsOption, safeRenderChart } = await jiti.import(
  '../src/utils/chartValidator.ts',
)

let passed = 0
let failed = 0

function assert(condition, msg) {
  if (condition) {
    passed++
  } else {
    failed++
    console.error(`  ❌ ${msg}`)
  }
}

// 递归检查对象中是否仍含危险字段（验证 filterDangerousFields 递归生效）
const DANGEROUS_FIELDS = ['__proto__', 'constructor', 'prototype', 'eval', 'Function', 'setTimeout', 'setInterval', 'fetch', 'XMLHttpRequest']
function hasDangerousDeep(obj) {
  if (obj === null || typeof obj !== 'object') return false
  if (Array.isArray(obj)) return obj.some((item) => hasDangerousDeep(item))
  for (const k of Object.keys(obj)) {
    if (DANGEROUS_FIELDS.includes(k)) return true
    if (hasDangerousDeep(obj[k])) return true
  }
  return false
}

// ===== 测试用例 =====
// [名称, 输入, 期望]
// 期望格式： { shouldParse: bool?, parseResult: null|'object'?, valid: bool?, noCrash: bool }
const CASES = [
  // ── 边界输入：三重容错必须兜住，不抛异常 ──
  { name: 'null', input: null, expect: { parseResult: null } },
  { name: 'undefined', input: undefined, expect: { parseResult: null } },
  { name: '空字符串', input: '', expect: { parseResult: null } },
  { name: '纯文本非 JSON', input: 'hello world', expect: { parseResult: null } },
  { name: '纯数字字符串', input: '12345', expect: {} }, // JSON.parse 返回数字，不崩溃（validate 会判非法）
  { name: '畸形 JSON 1', input: '{bad json}', expect: { parseResult: null } },
  { name: '畸形 JSON 2', input: '{{}}', expect: { parseResult: null } },
  { name: '畸形 JSON 3', input: '[}', expect: { parseResult: null } },
  { name: '残缺 JSON', input: '{"key": "value"', expect: { parseResult: null } },

  // ── 标准 JSON 格式（第一次解析成功）──
  { name: '标准 JSON', input: '{"series":[{"type":"bar","data":[1,2]}]}', expect: { parseResult: 'object', valid: true } },

  // ── AI 带 prefix 格式（第二次解析）──
  { name: 'option = 前缀', input: 'option = {"series":[{"type":"bar","data":[1,2]}]}', expect: { parseResult: 'object', valid: true } },
  { name: 'let option = 前缀', input: 'let option = {"series":[{"type":"bar","data":[1,2]}]}', expect: { parseResult: 'object', valid: true } },
  { name: 'option = 带分号', input: 'option = {"series":[{"type":"bar","data":[1,2]}]};', expect: { parseResult: 'object', valid: true } },

  // ── JS 对象字面量（第三次宽松解析）──
  { name: 'JS字面量 无引号键', input: '{xAxis:{type:"category"},series:[{data:[1,2,3],type:"bar"}]}', expect: { parseResult: 'object', valid: true } },
  { name: 'JS字面量 单引号', input: "{xAxis:{type:'category'},series:[{data:[1,2,3],type:'bar'}]}", expect: { parseResult: 'object', valid: true } },
  { name: 'JS字面量 尾逗号', input: '{series:[{data:[1,2,],type:"bar",},]}', expect: { parseResult: 'object', valid: true } },
  { name: 'JS字面量 option= + 尾逗号', input: 'option = {series:[{data:[1,2,],type:"bar",},]}', expect: { parseResult: 'object', valid: true } },

  // ── 危险字段过滤 ──
  { name: '__proto__ 污染', input: '{"__proto__":{"polluted":true},"series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },
  { name: 'constructor 污染', input: '{"constructor":{"prototype":{"polluted":true}},"series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },
  { name: 'eval 字段', input: '{"eval":"alert(1)","series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },
  { name: 'Function 字段', input: '{"Function":"return 1","series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },
  { name: 'setTimeout 字段', input: '{"setTimeout":"evil","series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },
  { name: 'fetch 字段', input: '{"fetch":"evil","series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },

  // ── 嵌套危险字段（验证 filterDangerousFields 递归生效）──
  { name: '嵌套 constructor (series 内)', input: '{"series":[{"constructor":{"prototype":{"polluted":true}},"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },
  { name: '嵌套 eval (tooltip 内)', input: '{"tooltip":{"eval":"x"},"series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },
  { name: '深层嵌套危险字段 (a.b.c)', input: '{"a":{"b":{"c":{"eval":"x"}}},"series":[{"type":"bar","data":[]}]}', expect: { parseResult: 'object', checkDangerous: true } },

  // ── 边界补充：validate 分支与解析限制（暴露真实逻辑盲点）──
  { name: 'dataset 合法', input: '{"dataset":{"source":[[1,2],[3,4]]}}', expect: { parseResult: 'object', valid: true } },
  { name: 'series 项混合(部分有type)', input: '{"series":[{"type":"bar"},{"data":[1]}]}', expect: { parseResult: 'object', valid: true } },
  { name: '顶层数组配置', input: '[{"type":"bar","data":[1,2]}]', expect: { parseResult: 'object', valid: false } },
  { name: '非 option 变量名前缀', input: 'myoption={"series":[{"type":"bar","data":[]}]}', expect: { parseResult: null } },

  // ── 缺少必填字段（校验应失败但解析应成功）──
  { name: '空对象', input: '{}', expect: { parseResult: 'object', valid: false } },
  { name: '空 series 数组', input: '{"series":[]}', expect: { parseResult: 'object', valid: false } },
  { name: 'data 不是数组', input: '{"data":"string"}', expect: { parseResult: 'object', valid: false } },
  { name: 'series 不是数组', input: '{"series":"string"}', expect: { parseResult: 'object', valid: false } },
  { name: 'series 缺 type', input: '{"series":[{"data":[1,2]}]}', expect: { parseResult: 'object', valid: false } },
]

console.log('========== chartValidator 零崩溃测试 ==========')
console.log(`测试用例数: ${CASES.length}\n`)

for (const { name, input, expect } of CASES) {
  try {
    const result = safeParseChartConfig(input)
    const { valid } = result ? validateEChartsOption(result) : { valid: false }

    // 1. 核心断言：不抛异常 = 零崩溃
    assert(true, `${name}: 不抛异常 ✅`)

    // 2. 解析结果类型匹配
    if (expect.parseResult === 'object') {
      assert(typeof result === 'object' && result !== null, `${name}: 应返回对象`)
    } else if (expect.parseResult === null) {
      assert(result === null, `${name}: 应返回 null`)
    }

    // 3. 校验结果
    if (expect.valid !== undefined) {
      assert(valid === expect.valid, `${name}: 校验结果应为 ${expect.valid}（实际 ${valid}）`)
    }

    // 4. 危险字段过滤检查（递归）
    if (expect.checkDangerous && result) {
      const hasDanger = hasDangerousDeep(result)
      assert(!hasDanger, `${name}: 危险字段应被递归过滤掉`)
    }
  } catch (e) {
    failed++
    console.error(`  ❌ ${name}: 抛出异常: ${e.message}`)
  }
}

// ===== safeRenderChart 整体链路 =====
console.log('\n--- safeRenderChart 链路测试 ---')
const RENDER_CASES = [
  { name: 'null→不崩溃', input: null },
  { name: '异常JSON→不崩溃', input: '{bad}' },
  { name: '正常JSON→不崩溃', input: '{"series":[{"type":"bar","data":[1]}]}' },
  { name: '危险字段→不崩溃', input: '{"__proto__":{"polluted":true},"series":[{"type":"bar","data":[]}]}' },
]
for (const { name, input } of RENDER_CASES) {
  try {
    const renderFn = () => { /* 桩渲染，仅看是否抛异常 */ }
    const r = safeRenderChart(input, renderFn)
    assert(true, `${name}: 链路不抛异常（结果: ${r.success ? '成功' : '失败(' + r.error + ')'}）`)
  } catch (e) {
    failed++
    console.error(`  ❌ ${name}: 链路抛出异常: ${e.message}`)
  }
}

console.log(`\n结果: ${passed} 通过 / ${failed} 失败`)
if (failed > 0) process.exit(1)
