<template>
  <el-card shadow="never">
    <template #header>
      <el-radio-group v-model="range" @change="render">
        <el-radio-button value="1h">1小时</el-radio-button>
        <el-radio-button value="6h">6小时</el-radio-button>
        <el-radio-button value="24h">24小时</el-radio-button>
        <el-radio-button value="7d">7天</el-radio-button>
      </el-radio-group>
    </template>
    <div ref="chartRef" style="width: 100%; height: 420px"></div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getHostMetrics } from '../../api'
import { formatTime, formatBytes } from '../../utils/format'

const range = ref('1h')
const chartRef = ref(null)
let chart = null

async function render() {
  if (!chart) chart = echarts.init(chartRef.value)
  const data = (await getHostMetrics({ serverId: 1, range: range.value })).data
  chart.setOption({
    title: { text: `服务器资源趋势（${range.value}）` },
    tooltip: { trigger: 'axis' },
    legend: { data: ['CPU %', '内存 %', '磁盘 %'] },
    grid: { left: 60, right: 60 },
    xAxis: { type: 'category', data: data.map((d) => formatTime(d.timestamp).slice(5, 16)) },
    yAxis: [
      { type: 'value', max: 100, name: '%' },
      { type: 'value', name: '网络', axisLabel: { formatter: (v) => formatBytes(v) } }
    ],
    series: [
      { name: 'CPU %', type: 'line', data: data.map((d) => d.cpu), smooth: true, showSymbol: false },
      { name: '内存 %', type: 'line', data: data.map((d) => (d.memTotal ? ((d.memUsed / d.memTotal) * 100).toFixed(1) : 0)), smooth: true, showSymbol: false },
      { name: '磁盘 %', type: 'line', data: data.map((d) => (d.diskTotal ? ((d.diskUsed / d.diskTotal) * 100).toFixed(1) : 0)), smooth: true, showSymbol: false }
    ]
  }, true)
}

let timer
onMounted(() => {
  render()
  timer = setInterval(render, 60000)
})
onUnmounted(() => {
  clearInterval(timer)
  if (chart) chart.dispose()
})
</script>
