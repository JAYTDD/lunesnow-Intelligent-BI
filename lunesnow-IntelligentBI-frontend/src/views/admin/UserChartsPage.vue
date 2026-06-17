<template>
  <div class="page-shell">
    <div class="page-header">
      <div class="header-content">
        <el-button text @click="router.back()" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回用户管理
        </el-button>
        <h1 class="page-title">{{ userName }} 的图表</h1>
        <p class="page-desc">查看和管理该用户的所有图表</p>
      </div>
    </div>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="图表名称" min-width="150" />
        <el-table-column prop="goal" label="分析目标" min-width="200" show-overflow-tooltip />
        <el-table-column prop="chartType" label="图表类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.chartType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagMap[row.status as keyof typeof statusTagMap] || 'info'" size="small">
              {{ statusTextMap[row.status as keyof typeof statusTextMap] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">查看</el-button>
            <el-button link type="warning" size="small" v-if="row.status === 'failed'" @click="handleRetry(row)">重试</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          background
          @current-change="loadChartList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { listChartVoByPage, deleteChart, retryChartGen } from '@/api/chartController'

const router = useRouter()
const route = useRoute()

const userId = ref(route.params.userId as string)
const userName = ref('')
const tableData = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const statusTagMap: Record<string, string> = {
  succeed: 'success',
  failed: 'danger',
  running: 'warning',
  waiting: 'info',
}

const statusTextMap: Record<string, string> = {
  succeed: '成功',
  failed: '失败',
  running: '生成中',
  waiting: '排队中',
}

const loadChartList = async () => {
  loading.value = true
  try {
    const res = await listChartVoByPage({
      current: currentPage.value,
      pageSize: pageSize.value,
      userId: userId.value,
    } as any)
    if (res.data?.records) {
      tableData.value = res.data.records
      total.value = res.data.total || 0
      if (res.data.records.length > 0 && res.data.records[0].user) {
        userName.value = res.data.records[0].user.userName || ''
      }
    }
  } catch (error: unknown) {
    ElMessage.error(`加载图表列表失败`)
  } finally {
    loading.value = false
  }
}

const viewDetail = (row: any) => {
  router.push(`/chart/detail/${row.id}`)
}

const handleRetry = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要重新生成图表 "${row.name}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await retryChartGen({ id: row.id })
    ElMessage.success('已重新提交生成任务')
    loadChartList()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(`重试失败`)
    }
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除图表 "${row.name}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteChart({ id: row.id })
    ElMessage.success('删除成功')
    loadChartList()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败`)
    }
  }
}

onMounted(() => {
  loadChartList()
})
</script>

<style lang="scss" scoped>
.page-shell {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px;
  min-height: calc(100vh - 120px);
}

.page-header {
  margin-bottom: 28px;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.back-btn {
  align-self: flex-start;
  margin-bottom: 8px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #18181b;
  margin: 0;
  letter-spacing: -0.5px;
}

.page-desc {
  font-size: 14px;
  color: #71717a;
  margin: 0;
}

.table-card {
  border: 1px solid #e4e4e7;
  border-radius: 16px;

  :deep(.el-card__body) {
    padding: 0;
  }
}

.pagination {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f4f4f5;
}
</style>
