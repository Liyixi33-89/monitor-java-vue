<template>
  <div>
    <el-form inline style="margin-bottom: 12px">
      <el-form-item label="项目">
        <el-select v-model="projectId" clearable placeholder="全部项目" style="width: 200px">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="type" clearable placeholder="全部类型" style="width: 160px">
          <el-option label="js_error" value="js_error" />
          <el-option label="unhandledrejection" value="unhandledrejection" />
          <el-option label="resource_error" value="resource_error" />
          <el-option label="api_error" value="api_error" />
          <el-option label="exception" value="exception" />
          <el-option label="process_crash" value="process_crash" />
          <el-option label="http_5xx" value="http_5xx" />
          <el-option label="log_error" value="log_error" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 120px">
          <el-option label="未处理" value="open" />
          <el-option label="已处理" value="resolved" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="errors" border size="small" @row-click="openDetail">
      <el-table-column prop="projectName" label="所属项目" width="160">
        <template #default="{ row }">
          <el-tag :type="row.projectType === 'frontend' ? 'success' : 'warning'" size="small">
            {{ row.projectType === 'frontend' ? '前端' : '后端' }}
          </el-tag>
          {{ row.projectName }}
        </template>
      </el-table-column>
      <el-table-column prop="type" label="错误类型" width="150" />
      <el-table-column prop="message" label="错误信息" show-overflow-tooltip />
      <el-table-column prop="count" label="累计次数" width="90" sortable />
      <el-table-column prop="firstSeenAt" label="首次发生" width="170">
        <template #default="{ row }">{{ formatTime(row.firstSeenAt) }}</template>
      </el-table-column>
      <el-table-column prop="lastSeenAt" label="最近发生" width="170">
        <template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'open' ? 'danger' : 'info'" size="small">
            {{ row.status === 'open' ? '未处理' : '已处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'open'" link type="primary" size="small" @click.stop="doResolve(row.id)">标记处理</el-button>
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

    <el-drawer v-model="drawerVisible" title="错误详情" size="50%">
      <div v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="项目">{{ detail.projectName }}（{{ detail.projectType === 'frontend' ? '前端' : '后端' }}）</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.type }}</el-descriptions-item>
          <el-descriptions-item label="次数">{{ detail.count }}</el-descriptions-item>
          <el-descriptions-item label="URL">{{ detail.url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">
            <pre style="white-space: pre-wrap; margin: 0">{{ detail.message }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="堆栈/上下文">
            <pre style="white-space: pre-wrap; margin: 0; max-height: 400px; overflow: auto">{{ detail.stack || '-' }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listErrors, resolveError, listProjects } from '../../api'
import { formatTime } from '../../utils/format'
import { ElMessage } from 'element-plus'

const projects = ref([])
const errors = ref([])
const projectId = ref(null)
const type = ref('')
const status = ref('')
const drawerVisible = ref(false)
const detail = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function load() {
  const params = { page: page.value, pageSize: pageSize.value }
  if (projectId.value) params.projectId = projectId.value
  if (type.value) params.type = type.value
  if (status.value) params.status = status.value
  const res = await listErrors(params)
  errors.value = res.data
  total.value = res.total
}

function onSizeChange() {
  page.value = 1
  load()
}

function onSearch() {
  page.value = 1
  load()
}

function openDetail(row) {
  detail.value = row
  drawerVisible.value = true
}

async function doResolve(id) {
  await resolveError(id)
  ElMessage.success('已标记处理')
  load()
}

onMounted(async () => {
  projects.value = (await listProjects()).data
  load()
})
</script>
