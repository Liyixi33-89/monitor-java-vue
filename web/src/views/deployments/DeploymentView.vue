<template>
  <div>
    <el-form inline style="margin-bottom: 12px">
      <el-form-item label="项目">
        <el-select v-model="projectId" clearable filterable placeholder="全部项目" style="width: 220px">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button type="success" @click="openCreate">登记发布</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" border size="small">
      <el-table-column prop="deployedAt" label="发布时间" width="170">
        <template #default="{ row }">{{ formatTime(row.deployedAt) }}</template>
      </el-table-column>
      <el-table-column prop="projectName" label="项目" width="180" />
      <el-table-column prop="version" label="版本" width="140">
        <template #default="{ row }">{{ row.version || '-' }}</template>
      </el-table-column>
      <el-table-column prop="operator" label="发布人" width="110">
        <template #default="{ row }">{{ row.operator || '-' }}</template>
      </el-table-column>
      <el-table-column prop="result" label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="resultTag(row.result)" size="small">{{ resultText(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="发布说明" show-overflow-tooltip />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该发布记录？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 12px; justify-content: flex-end"
      @size-change="onSizeChange"
      @current-change="load"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑发布记录' : '登记发布'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" filterable placeholder="选择项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" placeholder="如 v1.2.0 / commit 简写" />
        </el-form-item>
        <el-form-item label="发布人">
          <el-input v-model="form.operator" placeholder="默认当前登录用户" />
        </el-form-item>
        <el-form-item label="结果">
          <el-radio-group v-model="form.result">
            <el-radio value="success">成功</el-radio>
            <el-radio value="rollback">回滚</el-radio>
            <el-radio value="failed">失败</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker v-model="form.deployedAt" type="datetime" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发布说明">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="本次发布内容、影响范围等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listDeployments, createDeployment, updateDeployment, deleteDeployment, listProjects } from '../../api'
import { formatTime } from '../../utils/format'
import { ElMessage } from 'element-plus'

const projects = ref([])
const rows = ref([])
const projectId = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const form = ref({ projectId: null, version: '', operator: '', result: 'success', deployedAt: null, remark: '' })

async function load() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (projectId.value) params.projectId = projectId.value
  const res = await listDeployments(params)
  rows.value = res.data
  total.value = res.total
}

function onSearch() {
  page.value = 1
  load()
}

function onSizeChange() {
  page.value = 1
  load()
}

function openCreate() {
  editingId.value = null
  form.value = { projectId: projectId.value, version: '', operator: '', result: 'success', deployedAt: new Date(), remark: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    projectId: row.projectId,
    version: row.version || '',
    operator: row.operator || '',
    result: row.result || 'success',
    deployedAt: row.deployedAt ? new Date(row.deployedAt) : new Date(),
    remark: row.remark || ''
  }
  dialogVisible.value = true
}

async function doSubmit() {
  if (!form.value.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form.value }
    if (payload.deployedAt instanceof Date) {
      // 转为 ISO 格式，去掉毫秒（后端 LocalDateTime 可解析）
      payload.deployedAt = payload.deployedAt.toISOString().slice(0, 19)
    }
    if (editingId.value) {
      await updateDeployment(editingId.value, payload)
    } else {
      await createDeployment(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function doDelete(id) {
  await deleteDeployment(id)
  ElMessage.success('已删除')
  load()
}

function resultTag(r) {
  return r === 'success' ? 'success' : r === 'rollback' ? 'warning' : 'danger'
}

function resultText(r) {
  return r === 'success' ? '成功' : r === 'rollback' ? '回滚' : '失败'
}

onMounted(async () => {
  projects.value = (await listProjects()).data
  load()
})
</script>
