<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in resourceCards" :key="card.label">
        <el-card shadow="hover">
          <template #header>{{ card.label }}</template>
          <div style="font-size: 28px; font-weight: 700">{{ card.value }}</div>
          <div style="color: #909399; font-size: 12px; margin-top: 4px">{{ card.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>项目健康矩阵</template>
      <el-table :data="projects" size="default" @row-click="goDetail" style="cursor: pointer">
        <el-table-column prop="groupName" label="项目组" width="140" />
        <el-table-column prop="name" label="服务" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'frontend' ? 'success' : 'warning'" size="small">
              {{ row.type === 'frontend' ? '前端' : '后端' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="运行状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusOf(row).type" size="small">{{ statusOf(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近24h报错" width="130">
          <template #default="{ row }">
            <el-badge :value="errorCount[row.id] || 0" :type="(errorCount[row.id] || 0) > 0 ? 'danger' : 'info'" class="item">
              <span style="padding: 0 8px">{{ errorCount[row.id] || 0 }}</span>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column label="健康检查" width="180">
          <template #default="{ row }">
            <span>{{ healthOf(row) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLatestHostMetrics, listProjects, getLatestProcessMetrics, listErrors, getHealthChecks } from '../../api'
import { useRealtimeSocket } from '../../composables/useRealtimeSocket'
import { formatBytes, percent } from '../../utils/format'

const router = useRouter()
const host = ref(null)
const projects = ref([])
const processMap = ref({})
const errorCount = ref({})
const healthMap = ref({})

const resourceCards = computed(() => {
  const h = host.value
  return [
    { label: 'CPU 使用率', value: h?.cpu != null ? `${h.cpu}%` : '-', sub: h ? `load1: ${h.load1 ?? '-'}` : '加载中...' },
    { label: '内存', value: h ? percent(h.memUsed, h.memTotal) : '-', sub: h ? `${formatBytes(h.memUsed)} / ${formatBytes(h.memTotal)}` : '' },
    { label: '磁盘 (/)', value: h ? percent(h.diskUsed, h.diskTotal) : '-', sub: h ? `${formatBytes(h.diskUsed)} / ${formatBytes(h.diskTotal)}` : '' },
    { label: '网络累计流量', value: h ? formatBytes(h.netIn + h.netOut) : '-', sub: h ? `↓${formatBytes(h.netIn)} ↑${formatBytes(h.netOut)}` : '' }
  ]
})

function statusOf(row) {
  if (row.runtime === 'static') {
    const c = errorCount.value[row.id] || 0
    return c > 0 ? { type: 'danger', text: '有报错' } : { type: 'success', text: '正常' }
  }
  const p = processMap.value[row.id]
  if (!p) return { type: 'info', text: '未知' }
  if (p.status === 'online') return { type: 'success', text: '运行中' }
  if (p.status === 'errored') return { type: 'danger', text: '异常' }
  return { type: 'info', text: '离线' }
}

function healthOf(row) {
  const h = healthMap.value[row.id]
  if (!h) return '-'
  return h.success === 1 ? `✅ ${h.latencyMs}ms` : `❌ HTTP ${h.statusCode || 'ERR'}`
}

function goDetail(row) {
  router.push({ name: 'project-detail', params: { id: row.id } })
}

async function refresh() {
  try {
    host.value = (await getLatestHostMetrics(1)).data
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
  } catch (e) { /* 拦截器已提示 */ }
}

let sock
onMounted(() => {
  refresh()
  sock = useRealtimeSocket((msg) => {
    if (msg.type === 'metrics_host_refreshed') refresh()
  })
  // 兜底轮询：WS 未推送时每 30s 刷新
  const timer = setInterval(refresh, 30000)
  onUnmounted(() => { clearInterval(timer); sock && sock.close() })
})
</script>
