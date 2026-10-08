<template>
  <div>
    <el-button type="primary" size="small" style="margin-bottom: 12px" @click="openDialog()">新增项目</el-button>
    <el-table :data="projects" border size="small">
      <el-table-column prop="id" label="ID" width="50" />
      <el-table-column prop="groupName" label="项目组" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.type === 'frontend' ? 'success' : 'warning'" size="small">
            {{ row.type === 'frontend' ? '前端' : '后端' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="runtime" label="运行时" width="110" />
      <el-table-column prop="procName" label="进程/服务名" width="140" />
      <el-table-column prop="healthCheckUrl" label="健康检查URL" show-overflow-tooltip />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该项目？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑项目' : '新增项目'" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="项目组标识"><el-input v-model="form.groupName" placeholder="如 md-viewer" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="前端" value="frontend" />
            <el-option label="后端" value="backend" />
          </el-select>
        </el-form-item>
        <el-form-item label="运行时">
          <el-select v-model="form.runtime">
            <el-option label="static（Nginx静态站）" value="static" />
            <el-option label="springboot（systemd）" value="springboot" />
            <el-option label="node-pm2（PM2托管）" value="node-pm2" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问地址"><el-input v-model="form.accessUrl" /></el-form-item>
        <el-form-item label="进程/服务名"><el-input v-model="form.procName" placeholder="pm2进程名或systemd服务名" /></el-form-item>
        <el-form-item label="日志路径"><el-input v-model="form.logPath" placeholder="服务器上的日志绝对路径" /></el-form-item>
        <el-form-item label="健康检查URL"><el-input v-model="form.healthCheckUrl" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listProjects, createProject, updateProject, deleteProject } from '../../api'
import { ElMessage } from 'element-plus'

const projects = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({})

function openDialog(row) {
  editingId.value = row ? row.id : null
  form.value = row ? { ...row } : { groupName: '', name: '', type: 'frontend', runtime: 'static', accessUrl: '', procName: '', logPath: '', healthCheckUrl: '' }
  dialogVisible.value = true
}

async function doSave() {
  if (editingId.value) {
    await updateProject(editingId.value, form.value)
  } else {
    await createProject(form.value)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function doDelete(id) {
  await deleteProject(id)
  ElMessage.success('删除成功')
  load()
}

async function load() {
  projects.value = (await listProjects()).data
}

onMounted(load)
</script>
