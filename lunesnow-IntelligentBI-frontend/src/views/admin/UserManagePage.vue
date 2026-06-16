<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <div class="eyebrow">Admin</div>
        <h2>用户管理</h2>
      </div>
      <el-button type="primary" @click="handleAdd">新建</el-button>
    </div>

    <el-card class="table-card" shadow="never">
      <UserFormDialog
        :visible="dialogVisible"
        :user="editingUser"
        @close="dialogVisible = false"
        @success="loadUserList"
      />

      <el-table :data="tableData" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userName" label="用户名" width="180" />
        <el-table-column prop="userAccount" label="账号" width="180" />
        <el-table-column prop="userRole" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="row.userRole === 'admin' ? 'danger' : 'info'">
              {{ row.userRole === 'admin' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
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
          @current-change="loadUserList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUserVoByPage, deleteUser } from '@/api/userController'
import UserFormDialog from '@/components/UserFormDialog.vue'

const tableData = ref<API.UserVO[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false) // 新增/编辑用户弹窗是否可见
const editingUser = ref<API.UserVO | null>(null) // 当前编辑的用户

// 加载用户列表
const loadUserList = async () => {
  try {
    const query: API.UserQueryRequest = {
      current: currentPage.value,
      pageSize: pageSize.value,
    }
    const res = await listUserVoByPage(query)
    if (res.data?.records) {
      tableData.value = res.data.records
      total.value = res.data.total || 0
    }
  } catch (error: unknown) {
    ElMessage.error(`加载用户列表失败：${error instanceof Error ? error.message : '未知错误'}`)
  }
}

onMounted(() => {
  loadUserList()
})

const handleAdd = () => {
  editingUser.value = null
  dialogVisible.value = true
}

const handleEdit = (row: API.UserVO) => {
  editingUser.value = row
  dialogVisible.value = true
}

const handleDelete = async (row: API.UserVO) => {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确定要删除用户 ${row.userName} 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteUser({ id: row.id })
    ElMessage.success('删除成功！')
    loadUserList()
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败：${error instanceof Error ? error.message : '未知错误'}`)
    }
  }
}
</script>

<style lang="scss" scoped>
.page-shell {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px;
  min-height: calc(100vh - 120px);
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
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

.table-card {
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;

  :deep(.el-card__body) {
    padding: 0;
  }
}

:deep(.el-table) {
  --el-table-header-bg-color: #fafafa;
  --el-table-border-color: #f4f4f5;

  th {
    font-weight: 600;
    color: #3f3f46;
    font-size: 13px;
  }

  td {
    color: #52525b;
  }
}

:deep(.el-button--primary) {
  background: #18181b;
  border-color: #18181b;
  border-radius: 10px;

  &:hover {
    background: #27272a;
    border-color: #27272a;
  }
}

.pagination {
  padding: 16px 20px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f4f4f5;
}
</style>
