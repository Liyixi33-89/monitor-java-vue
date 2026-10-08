<template>
  <div>
    <el-radio-group v-model="filterType" style="margin-bottom: 12px">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="frontend">仅前端</el-radio-button>
      <el-radio-button value="backend">仅后端</el-radio-button>
      <el-radio-button value="error">仅异常</el-radio-button>
    </el-radio-group>

    <el-card v-for="group in visibleGroups" :key="group.groupName" shadow="never" style="margin-bottom: 16px">
      <template #header>
        <b>{{ group.displayName }}</b>
        <el-tag size="small" style="margin-left: 8px" type="info">{{ group.items.length }} 个服务</el-tag>
      </template>
      <el-row :gutter="16">
        <el-col :span="12" v-for="p in group.items" :key="p.id">
          <el-card shadow="hover" style="cursor: pointer; margin-bottom: 12px" @click="goDetail(p.id)">
            <div style="display: flex; justify-content: space-between; align-items: center">
              <div>
                <el-tag :type="p.type === 'frontend' ? 'success' : 'warning'" size="small">
                  {{ p.type === 'frontend' ? '前端' : '后端' }}
                </el-tag>
                <b style="margin-left: 8px">{{ p.name }}</b>
                <el-tag v-if="p.runtime" size="small" type="info" style="margin-left: 6px">{{ p.runtime }}</el-tag>
              </div>
              <el-badge :value="errorCount[p.id] || 0" :hidden="!(errorCount[p.id] > 0)" type="danger">
                <el-tag :type="statusOf(p).type" size="small" effect="dark">{{ statusOf(p).text }}</el-tag>
              </el-badge>
            </div>
            <div style="color: #909399; font-size: 12px; margin-top: 8px">
              <div>访问地址：{{ p.accessUrl || '-' }}</div>
              <div>最近24h报错：{{ errorCount[p.id] || 0 }} 条 ｜ 健康检查：{{ healthOf(p) }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listProjects, listErrors, getLatestProcessMetrics, getHealthChecks } from '../../api'
import { useRealtimeSocket } from '../../composables/useRealtimeSocket'

const router = useRouter()
const projects = ref([])
const errorCount = ref({})
const processMap = ref({})
const healthMap = ref({})
const filterType = ref('')

const visibleGroups = computed(() => {
  const groups = {}
  const displayNames = { 'md-viewer': 'MD Viewer（文档阅读平台）', 'taskmanager': 'TaskManager（签到签退系统）' }
  let list = projects.value
  if (filterType.value === 'frontend' || filterType.value === 'backend') {
    list = list.filter((p) => p.type === filterType.value)
  } else if (filterType.value === 'error') {
    list = list.filter((p) => (errorCount.value[p.id] || 0) > 0 || statusOf(p).text !== '正常' && statusOf(p).text !== '运行中')
  }
  list.forEach((p) => {
    if (!groups[p.groupName]) {
      groups[p.groupName] = { groupName: p.groupName, displayName: displayNames[p.groupName] || p.groupName, items: [] }
    }
    groups[p.groupName].items.push(p)
  })
  // 组内排序：前端在前，后端在后
  Object.values(groups).forEach((g) => g.items.sort((a, b) => (a.type === 'frontend' ? -1 : 1) - (b.type === 'frontend' ? -1 : 1)))
  return Object.values(groups)
})

function statusOf(p) {
  if (p.runtime === 'static') {
    return (errorCount.value[p.id] || 0) > 0 ? { type: 'danger', text: '有报错' } : { type: 'success', text: '正常' }
  }
  const proc = processMap.value[p.id]
  if (!proc) return { type: 'info', text: '未知' }
  if (proc.status === 'online') return { type: 'success', text: '运行中' }
  if (proc.status === 'errored') return { type: 'danger', text: '异常' }
  return { type: 'info', text: '离线' }
}

function healthOf(p) {
  const h = healthMap.value[p.id]
  if (!h) return '-'
  return h.success === 1 ? `✅ ${h.latencyMs}ms` : `❌ HTTP ${h.statusCode || 'ERR'}`
}

function goDetail(id) {
  router.push({ name: 'project-detail', params: { id } })
}

async function refresh() {
  projects.value = (await listProjects()).data
  const errs = (await listErrors({ status: 'open', limit: 300 })).data
  const counts = {}
  errs.forEach((e) => {
    const ts = new Date(e.lastSeenAt).getTime()
    if (Date.now() - ts < 24 * 3600 * 1000) counts[e.projectId] = (counts[e.projectId] || 0) + e.count
  })
  errorCount.value = counts
  for (const p of projects.value) {
    if (p.runtime !== 'static') {
      getLatestProcessMetrics(p.id).then((r) => { processMap.value[p.id] = r.data })
    }
    if (p.healthCheckUrl) {
      getHealthChecks({ projectId: p.id, limit: 1 }).then((r) => {
        if (r.data && r.data.length) healthMap.value[p.id] = r.data[0]
      })
    }
  }
}

let sock
onMounted(() => {
  refresh()
  sock = useRealtimeSocket(() => refresh())
})
</script>
