<template>
  <div class="rate-limit-page">
    <div class="page-header">
      <h1 class="page-title">限流管理</h1>
      <p class="page-desc">查看和重置用户/IP 的限流状态</p>
    </div>

    <!-- 查询区域 -->
    <div class="query-card">
      <div class="query-row">
        <el-input
          v-model="queryKey"
          placeholder="输入用户 ID 或 IP 地址"
          clearable
          @keyup.enter="handleQuery"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleQuery" :loading="querying">
          <el-icon><Search /></el-icon>
          查询状态
        </el-button>
        <el-button
          @click="handleReset"
          :loading="resetting"
          type="danger"
          plain
          :disabled="!statusData"
        >
          <el-icon><RefreshRight /></el-icon>
          重置限流
        </el-button>
      </div>

      <div class="quick-keys">
        <span class="quick-label">查询类型：</span>
        <el-radio-group v-model="queryType" size="small">
          <el-radio-button value="user">用户</el-radio-button>
          <el-radio-button value="ip">IP</el-radio-button>
        </el-radio-group>
        <span class="quick-hint">
          {{
            queryType === 'user'
              ? '输入用户 ID，如 2064277865772695554'
              : '输入 IP 地址，如 127.0.0.1'
          }}
        </span>
      </div>
    </div>

    <!-- 单个查询结果 -->
    <div v-if="statusData" class="status-card">
      <div class="status-header">
        <el-icon :size="20" color="#22c55e"><CircleCheckFilled /></el-icon>
        <span>限流状态</span>
      </div>
      <div class="status-grid">
        <div class="status-item">
          <div class="status-label">Key</div>
          <div class="status-value mono">{{ statusData.key }}</div>
        </div>
        <div class="status-item">
          <div class="status-label">是否存在</div>
          <div class="status-value">
            <el-tag :type="statusData.exists ? 'success' : 'info'" size="small">
              {{ statusData.exists ? '存在' : '不存在' }}
            </el-tag>
          </div>
        </div>
        <div v-if="statusData.exists" class="status-item">
          <div class="status-label">剩余令牌</div>
          <div class="status-value highlight">{{ statusData.availableTokens }}</div>
        </div>
      </div>
    </div>

    <!-- 全部限流列表 -->
    <div class="list-card">
      <div class="list-header">
        <h3>全部限流记录</h3>
        <div class="list-actions">
          <el-button @click="loadAllLimits" :loading="loadingList" size="small">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button
            @click="handleResetAll"
            :loading="resettingAll"
            type="danger"
            plain
            size="small"
            :disabled="allLimits.length === 0"
          >
            全部重置
          </el-button>
        </div>
      </div>

      <el-table :data="allLimits" v-loading="loadingList" stripe style="width: 100%">
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 'user' ? 'primary' : 'warning'" size="small">
              {{ row.type === 'user' ? '用户' : 'IP' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="identifier" label="标识" min-width="200">
          <template #default="{ row }">
            <span class="mono">{{ row.identifier }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="availableTokens" label="剩余令牌" width="100">
          <template #default="{ row }">
            <span class="token-count">{{ row.availableTokens }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleView(row)"
            >
              查看
            </el-button>
            <el-button type="danger" link size="small" @click="handleResetSingle(row.key)">
              重置
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="allLimits.length === 0 && !loadingList" class="empty-table">
        <el-empty description="暂无限流记录" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, CircleCheckFilled, Refresh } from '@element-plus/icons-vue'
import {
  getRateLimitStatus,
  resetRateLimit,
  listAllRateLimits,
  resetAllRateLimits,
} from '@/api/rateLimitController'

const queryKey = ref('')
const queryType = ref<'user' | 'ip'>('user')
const querying = ref(false)
const resetting = ref(false)
const statusData = ref<Record<string, any> | null>(null)

const allLimits = ref<any[]>([])
const loadingList = ref(false)
const resettingAll = ref(false)

// 构建完整的限流 key
const fullKey = computed(() => {
  const val = queryKey.value.trim()
  if (!val) return ''
  return `rate_limit:${queryType.value}:${val}`
})

const handleView = (row: any) => {
  queryKey.value = row.identifier
  queryType.value = row.type
  handleQuery()
}

const handleQuery = async () => {
  if (!queryKey.value.trim()) {
    ElMessage.warning('请输入用户 ID 或 IP 地址')
    return
  }
  querying.value = true
  try {
    const res = await getRateLimitStatus({ key: fullKey.value })
    statusData.value = res.data
  } catch (error: any) {
    ElMessage.error(error?.message || '查询失败')
    statusData.value = null
  } finally {
    querying.value = false
  }
}

const handleReset = async () => {
  if (!statusData.value) return
  await ElMessageBox.confirm(`确认重置限流 Key: ${statusData.value.key}？`, '确认重置', {
    type: 'warning',
  })
  resetting.value = true
  try {
    await resetRateLimit({ key: fullKey.value })
    ElMessage.success('限流已重置')
    statusData.value = null
    queryKey.value = ''
    loadAllLimits()
  } catch (error: any) {
    ElMessage.error(error?.message || '重置失败')
  } finally {
    resetting.value = false
  }
}

// 加载全部限流列表
const loadAllLimits = async () => {
  loadingList.value = true
  try {
    const res = await listAllRateLimits()
    allLimits.value = res.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '加载失败')
  } finally {
    loadingList.value = false
  }
}

const handleResetSingle = async (key: string) => {
  await ElMessageBox.confirm(`确认重置限流 Key: ${key}？`, '确认重置', { type: 'warning' })
  try {
    await resetRateLimit({ key })
    ElMessage.success('限流已重置')
    loadAllLimits()
  } catch (error: any) {
    ElMessage.error(error?.message || '重置失败')
  }
}

const handleResetAll = async () => {
  await ElMessageBox.confirm(
    `确认重置全部 ${allLimits.value.length} 条限流记录？`,
    '确认批量重置',
    { type: 'warning' },
  )
  resettingAll.value = true
  try {
    await resetAllRateLimits()
    ElMessage.success('全部限流已重置')
    loadAllLimits()
  } catch (error: any) {
    ElMessage.error(error?.message || '重置失败')
  } finally {
    resettingAll.value = false
  }
}

onMounted(() => {
  loadAllLimits()
})
</script>

<style lang="scss" scoped>
.rate-limit-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px;
  min-height: calc(100vh - 80px);
}

.page-header {
  margin-bottom: 32px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #18181b;
  margin: 0 0 6px 0;
  letter-spacing: -0.5px;
}

.page-desc {
  font-size: 14px;
  color: #71717a;
  margin: 0;
}

.query-card {
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
}

.query-row {
  display: flex;
  gap: 12px;
  align-items: center;

  .el-input {
    flex: 1;
  }
}

.quick-keys {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.quick-label {
  color: #71717a;
  font-size: 13px;
}

.quick-hint {
  color: #a1a1aa;
  font-size: 12px;
}

.status-card {
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
}

.status-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #18181b;
  margin-bottom: 20px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
}

.status-item {
  .status-label {
    font-size: 12px;
    color: #71717a;
    margin-bottom: 4px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    font-weight: 500;
  }

  .status-value {
    font-size: 16px;
    font-weight: 600;
    color: #18181b;

    &.mono {
      font-family: 'SF Mono', 'Consolas', monospace;
      font-size: 13px;
      word-break: break-all;
    }

    &.highlight {
      color: #10b981;
      font-size: 28px;
    }
  }
}

.list-card {
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  padding: 24px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;

  h3 {
    font-size: 16px;
    font-weight: 600;
    color: #18181b;
    margin: 0;
  }
}

.list-actions {
  display: flex;
  gap: 8px;
}

.mono {
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 13px;
}

.token-count {
  font-weight: 600;
  color: #10b981;
  font-family: 'SF Mono', 'Consolas', monospace;
}

.empty-table {
  padding: 40px 0;
}
</style>
