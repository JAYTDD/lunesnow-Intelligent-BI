<template>
  <el-dialog
    :model-value="visible"
    :title="user ? '编辑用户' : '新增用户'"
    width="500px"
    @update:model-value="emit('close')"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="用户名" prop="userName">
        <el-input v-model="form.userName" />
      </el-form-item>
      <el-form-item label="账号" prop="userAccount">
        <el-input v-model="form.userAccount" />
      </el-form-item>
      <el-form-item label="角色" prop="userRole">
        <el-select v-model="form.userRole">
          <el-option label="普通用户" value="user" />
          <el-option label="管理员" value="admin" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { addUser, updateUser } from '@/api/userController'

const props = defineProps<{
  visible: boolean
  user: API.UserVO | null
}>()

const emit = defineEmits<{
  close: []
  success: []
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  userName: '',
  userAccount: '',
  userRole: 'user',
})

const rules: FormRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  userAccount: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  userRole: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

watch(
  () => props.visible,
  (val) => {
    if (!val) return
    if (props.user) {
      form.userName = props.user.userName ?? ''
      form.userAccount = ''
      form.userRole = props.user.userRole ?? 'user'
    } else {
      form.userName = ''
      form.userAccount = ''
      form.userRole = 'user'
    }
  },
)

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (props.user?.id) {
        await updateUser({ id: props.user.id, ...form } as API.UserUpdateRequest)
        ElMessage.success('更新成功')
      } else {
        await addUser(form)
        ElMessage.success('新增成功')
      }
      emit('success')
      emit('close')
    } catch (error: unknown) {
      ElMessage.error(error instanceof Error ? error.message : '操作失败')
    } finally {
      submitting.value = false
    }
  })
}
</script>
