<template>
  <el-table :data="logs" border size="small">
    <el-table-column prop="triggeredAt" label="触发时间" width="180">
      <template #default="{ row }">{{ formatTime(row.triggeredAt) }}</template>
    </el-table-column>
    <el-table-column prop="projectType" label="项目类型" width="90">
      <template #default="{ row }">
        <el-tag v-if="row.projectType" :type="row.projectType === 'frontend' ? 'success' : 'warning'" size="small">
          {{ row.projectType === 'frontend' ? '前端' : '后端' }}
        </el-tag>
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column prop="level" label="级别" width="90">
      <template #default="{ row }">
        <el-tag :type="row.level === 'critical' ? 'danger' : 'warning'" size="small">{{ row.level }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="message" label="告警内容" show-overflow-tooltip />
    <el-table-column prop="resolvedAt" label="恢复时间" width="180">
      <template #default="{ row }">
        <el-tag v-if="!row.resolvedAt" type="danger" size="small">未恢复</el-tag>
        <span v-else>{{ formatTime(row.resolvedAt) }}</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listAlertLogs } from '../../api'
import { formatTime } from '../../utils/format'

const logs = ref([])

onMounted(async () => {
  logs.value = (await listAlertLogs({ limit: 200 })).data
})
</script>
