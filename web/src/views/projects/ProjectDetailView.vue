<template>
  <div v-if="project">
    <el-page-header @back="$router.back()" :content="project.name" style="margin-bottom: 16px" />

    <el-descriptions :column="3" border style="margin-bottom: 16px">
      <el-descriptions-item label="项目组">{{ project.groupName }}</el-descriptions-item>
      <el-descriptions-item label="类型">
        <el-tag :type="project.type === 'frontend' ? 'success' : 'warning'" size="small">
          {{ project.type === 'frontend' ? '前端' : '后端' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="运行时">{{ project.runtime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="访问地址">
        <a :href="project.accessUrl" target="_blank">{{ project.accessUrl }}</a>
      </el-descriptions-item>
      <el-descriptions-item label="进程/服务名">{{ project.procName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="当前状态">
        <el-tag :type="latestProcess ? (latestProcess.status === 'online' ? 'success' : 'danger') : 'info'" size="small">
          {{ latestProcess ? latestProcess.status : '-' }}
          <template v-if="latestProcess">（重启 {{ latestProcess.restartTime }} 次，CPU {{ latestProcess.cpu }}%，内存 {{ formatBytes(latestProcess.mem) }}）</template>
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <el-button type="primary" size="small" style="margin-bottom: 12px" :loading="checking" @click="doCheck">
      手动触发健康检查
    </el-button>

    <el-tabs>
      <el-tab-pane label="健康检查历史">
        <el-table :data="healthChecks" size="small" border>
          <el-table-column prop="timestamp" label="时间" width="200">
            <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
          </el-table-column>
          <el-table-column prop="statusCode" label="状态码" width="90" />
          <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
          <el-table-column label="结果">
            <template #default="{ row }">
              <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">{{ row.success === 1 ? '成功' : '失败' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`报错列表（${errors.length}）`">
        <el-table :data="errors" size="small" border>
          <el-table-column prop="lastSeenAt" label="最近发生" width="180">
            <template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="150" />
          <el-table-column prop="message" label="错误信息" show-overflow-tooltip />
          <el-table-column prop="count" label="次数" width="70" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'open' ? 'danger' : 'info'" size="small">
                {{ row.status === 'open' ? '未处理' : '已处理' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button v-if="row.status === 'open'" link type="primary" size="small" @click="doResolve(row.id)">标记处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="资源趋势">
        <div ref="chartRef" style="width: 100%; height: 360px"></div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { getProject, getLatestProcessMetrics, getHealthChecks, listErrors, resolveError, triggerHealthCheck, getProcessMetrics, getHostMetrics } from '../../api'
import { formatBytes, formatTime } from '../../utils/format'
import { ElMessage } from 'element-plus'

const route = useRoute()
const projectId = route.params.id
const project = ref(null)
const latestProcess = ref(null)
const healthChecks = ref([])
const errors = ref([])
const checking = ref(false)
const chartRef = ref(null)
let chart = null

async function load() {
  project.value = (await getProject(projectId)).data
  latestProcess.value = (await getLatestProcessMetrics(projectId)).data
  healthChecks.value = (await getHealthChecks({ projectId, limit: 20 })).data
  errors.value = (await listErrors({ projectId, limit: 100 })).data
}

async function doCheck() {
  checking.value = true
  try {
    await triggerHealthCheck(projectId)
    ElMessage.success('健康检查完成')
    healthChecks.value = (await getHealthChecks({ projectId, limit: 20 })).data
  } finally {
    checking.value = false
  }
}

async function doResolve(id) {
  await resolveError(id)
  ElMessage.success('已标记处理')
  errors.value = (await listErrors({ projectId, limit: 100 })).data
}

async function renderChart() {
  await nextTick()
  if (!chartRef.value) return
  chart = chartRef.value.offsetWidth ? echarts.init(chartRef.value) : chart
  if (!chart) return

  if (project.value.runtime === 'static') {
    // 前端项目：展示主机指标（静态站无进程指标）
    const data = (await getHostMetrics({ serverId: project.value.serverId, range: '1h' })).data
    chart.setOption({
      title: { text: '主机 CPU / 内存使用率（近1小时）' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['CPU %', '内存 %'] },
      xAxis: { type: 'category', data: data.map((d) => formatTime(d.timestamp).slice(11)) },
      yAxis: { type: 'value', max: 100 },
      series: [
        { name: 'CPU %', type: 'line', data: data.map((d) => d.cpu), smooth: true },
        { name: '内存 %', type: 'line', data: data.map((d) => (d.memTotal ? ((d.memUsed / d.memTotal) * 100).toFixed(1) : 0)), smooth: true }
      ]
    })
  } else {
    const data = (await getProcessMetrics({ projectId, range: '1h' })).data
    chart.setOption({
      title: { text: '进程 CPU / 内存（近1小时）' },
      tooltip: { trigger: 'axis' },
      legend: { data: ['CPU %', '内存'] },
      xAxis: { type: 'category', data: data.map((d) => formatTime(d.timestamp).slice(11)) },
      yAxis: [
        { type: 'value', name: 'CPU %' },
        { type: 'value', name: '内存', axisLabel: { formatter: (v) => formatBytes(v) } }
      ],
      series: [
        { name: 'CPU %', type: 'line', data: data.map((d) => d.cpu), smooth: true },
        { name: '内存', type: 'line', yAxisIndex: 1, data: data.map((d) => d.mem), smooth: true }
      ]
    })
  }
}

let timer
onMounted(async () => {
  await load()
  renderChart()
  timer = setInterval(() => { load(); renderChart() }, 30000)
})
onUnmounted(() => {
  clearInterval(timer)
  if (chart) chart.dispose()
})
</script>
