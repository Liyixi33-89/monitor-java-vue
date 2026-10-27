<template>
  <div>
    <el-alert type="info" :closable="false" style="margin-bottom: 12px"
      title="外部 IP 访问监控：按小时窗口统计 nginx 访问，单 IP 单小时超过阈值自动封禁 web 端口，到期自动解封。内网/本机不统计；白名单 IP 统计可见但不封禁。" />

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>当前窗口 Top 访问 IP（{{ currentWindow }}）</span>
              <el-button size="small" @click="loadTop">刷新</el-button>
            </div>
          </template>
          <el-table :data="topList" border size="small">
            <el-table-column prop="ip" label="IP" width="160" />
            <el-table-column prop="totalRequests" label="请求数" width="110" sortable />
            <el-table-column prop="count4xx" label="4xx" width="90" />
            <el-table-column prop="count5xx" label="5xx" width="90" />
            <el-table-column label="占比" width="140">
              <template #default="{ row }">
                <el-progress :percentage="Math.min(100, Math.round(row.totalRequests / banThreshold * 100))" :stroke-width="10"
                  :color="row.totalRequests >= banThreshold ? '#f56c6c' : row.totalRequests >= banThreshold / 2 ? '#e6a23c' : '#67c23a'" />
              </template>
            </el-table-column>
            <el-table-column label="封禁状态" width="120">
              <template #default="{ row }">
                <el-tag v-if="bannedIps.has(row.ip)" type="danger" size="small">已封禁</el-tag>
                <el-tag v-else type="success" size="small">正常</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button v-if="!bannedIps.has(row.ip)" link type="danger" size="small" @click="doBan(row.ip, `当前窗口请求数 ${row.totalRequests}`)">封禁</el-button>
                <el-button v-else link type="primary" size="small" @click="doLift(row.ip)">解封</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="samplePaths" label="样例请求" show-overflow-tooltip>
              <template #default="{ row }">
                <span style="font-size: 12px; color: #999">{{ (row.samplePaths || '').split('\n')[0] }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="封禁列表" name="bans">
        <el-form inline style="margin-bottom: 12px">
          <el-form-item label="状态">
            <el-select v-model="banStatus" clearable placeholder="全部" style="width: 130px" @change="onBanSearch">
              <el-option label="封禁中" value="active" />
              <el-option label="已解封" value="lifted" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onBanSearch">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="bans" border size="small">
          <el-table-column prop="ip" label="IP" width="150" />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">
              <el-tag :type="row.source === 'auto' ? 'danger' : 'warning'" size="small">
                {{ row.source === 'auto' ? '自动' : '手动' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" show-overflow-tooltip />
          <el-table-column prop="requestCount" label="触发请求数" width="110" />
          <el-table-column prop="bannedAt" label="封禁时间" width="170">
            <template #default="{ row }">{{ formatTime(row.bannedAt) }}</template>
          </el-table-column>
          <el-table-column prop="expiresAt" label="到期时间" width="170">
            <template #default="{ row }">{{ row.expiresAt ? formatTime(row.expiresAt) : '永久' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'danger' : 'info'" size="small">
                {{ row.status === 'active' ? '封禁中' : '已解封' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'active'" link type="primary" size="small" @click="doLift(row.ip)">解封</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="banPage" v-model:page-size="banPageSize" :total="banTotal"
          :page-sizes="[10, 20, 50]" layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
          @current-change="loadBans" />
      </el-tab-pane>

      <el-tab-pane label="访问统计" name="visits">
        <el-form inline style="margin-bottom: 12px">
          <el-form-item label="IP">
            <el-input v-model="visitIp" placeholder="精确匹配" clearable style="width: 160px" @keyup.enter="onVisitSearch" />
          </el-form-item>
          <el-form-item label="窗口">
            <el-input v-model="visitWindow" placeholder="yyyy-MM-dd HH:00" clearable style="width: 180px" @keyup.enter="onVisitSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onVisitSearch">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="visits" border size="small">
          <el-table-column prop="ip" label="IP" width="160" />
          <el-table-column prop="hourWindow" label="统计窗口" width="170" />
          <el-table-column prop="totalRequests" label="请求数" width="110" sortable />
          <el-table-column prop="count4xx" label="4xx" width="90" />
          <el-table-column prop="count5xx" label="5xx" width="90" />
          <el-table-column prop="updatedAt" label="最近更新" width="170">
            <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column prop="samplePaths" label="样例请求" show-overflow-tooltip>
            <template #default="{ row }">
              <span style="font-size: 12px; color: #999">{{ (row.samplePaths || '').split('\n')[0] }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="!bannedIps.has(row.ip)" link type="danger" size="small" @click="doBan(row.ip, `手动封禁（窗口 ${row.hourWindow} ${row.totalRequests} 次）`)">封禁</el-button>
              <el-button v-else link type="primary" size="small" @click="doLift(row.ip)">解封</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="visitPage" v-model:page-size="visitPageSize" :total="visitTotal"
          :page-sizes="[10, 20, 50]" layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
          @current-change="loadVisits" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { topIpVisits, listIpVisits, listIpBans, banIp, liftIpBan } from '../../api'
import { formatTime } from '../../utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('bans')
const banThreshold = ref(500)

const currentWindow = ref('')
const topList = ref([])
const bannedIps = ref(new Set())

const bans = ref([])
const banStatus = ref('active')
const banPage = ref(1)
const banPageSize = ref(20)
const banTotal = ref(0)

const visits = ref([])
const visitIp = ref('')
const visitWindow = ref('')
const visitPage = ref(1)
const visitPageSize = ref(20)
const visitTotal = ref(0)

let timer = null

const loadTop = async () => {
  const res = await topIpVisits({ limit: 15 })
  topList.value = res.data
  currentWindow.value = res.hourWindow
}

const loadBans = async () => {
  const params = { page: banPage.value, pageSize: banPageSize.value }
  if (banStatus.value) params.status = banStatus.value
  const res = await listIpBans(params)
  bans.value = res.data
  banTotal.value = res.total
  bannedIps.value = new Set(res.data.filter(b => b.status === 'active').map(b => b.ip))
}

const loadVisits = async () => {
  const params = { page: visitPage.value, pageSize: visitPageSize.value }
  if (visitIp.value) params.ip = visitIp.value
  if (visitWindow.value) params.hourWindow = visitWindow.value
  const res = await listIpVisits(params)
  visits.value = res.data
  visitTotal.value = res.total
}

const onBanSearch = () => { banPage.value = 1; loadBans() }
const onVisitSearch = () => { visitPage.value = 1; loadVisits() }

const doBan = async (ip, reason) => {
  await ElMessageBox.confirm(`确定封禁 IP ${ip}？将阻断其访问所有 web 端口（默认 24 小时自动解封）。`, '封禁确认', { type: 'warning' })
  await banIp({ ip, reason })
  ElMessage.success(`已封禁 ${ip}`)
  loadBans(); loadVisits(); loadTop()
}

const doLift = async (ip) => {
  await liftIpBan(ip)
  ElMessage.success(`已解封 ${ip}`)
  loadBans(); loadVisits(); loadTop()
}

onMounted(() => {
  loadTop()
  loadBans()
  loadVisits()
  timer = setInterval(() => { loadTop(); loadBans() }, 30000)
})

onUnmounted(() => clearInterval(timer))
</script>
