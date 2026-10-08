<template>
  <div>
    <el-button type="primary" size="small" style="margin-bottom: 12px" @click="openDialog()">新增规则</el-button>
    <el-table :data="rules" border size="small">
      <el-table-column prop="id" label="ID" width="50" />
      <el-table-column prop="projectId" label="项目ID" width="80">
        <template #default="{ row }">{{ row.projectId || '全局' }}</template>
      </el-table-column>
      <el-table-column prop="metricType" label="指标" width="130" />
      <el-table-column label="条件" width="130">
        <template #default="{ row }">{{ row.condition }} {{ row.threshold }}</template>
      </el-table-column>
      <el-table-column prop="notifyChannel" label="通知渠道" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该规则？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑规则' : '新增规则'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目ID">
          <el-input-number v-model="form.projectId" :min="1" placeholder="留空为全局" />
        </el-form-item>
        <el-form-item label="指标类型">
          <el-select v-model="form.metricType">
            <el-option label="CPU使用率(%)" value="cpu" />
            <el-option label="内存使用率(%)" value="mem" />
            <el-option label="磁盘使用率(%)" value="disk" />
            <el-option label="进程状态异常" value="process_restart" />
            <el-option label="健康检查连续失败" value="health" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <el-select v-model="form.condition" style="width: 90px">
            <el-option label=">" value=">" />
            <el-option label="<" value="<" />
            <el-option label="=" value="=" />
          </el-select>
          <el-input-number v-model="form.threshold" :min="0" :max="100" style="margin-left: 8px" />
        </el-form-item>
        <el-form-item label="持续/次数">
          <el-input-number v-model="form.durationSec" :min="0" />
          <span style="margin-left: 8px; color: #909399">health 规则表示连续失败次数</span>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-select v-model="form.notifyChannel">
            <el-option label="企业微信" value="wecom" />
            <el-option label="钉钉" value="dingtalk" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabledBool" />
        </el-form-item>
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
import { listAlertRules, createAlertRule, updateAlertRule, deleteAlertRule } from '../../api'
import { ElMessage } from 'element-plus'

const rules = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({})

function openDialog(row) {
  editingId.value = row ? row.id : null
  form.value = row
    ? { ...row, enabledBool: row.enabled === 1 }
    : { projectId: null, metricType: 'cpu', condition: '>', threshold: 85, durationSec: 0, notifyChannel: 'wecom', enabledBool: true }
  dialogVisible.value = true
}

async function doSave() {
  const payload = { ...form.value, enabled: form.value.enabledBool ? 1 : 0 }
  delete payload.enabledBool
  if (editingId.value) {
    await updateAlertRule(editingId.value, payload)
  } else {
    await createAlertRule(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function doDelete(id) {
  await deleteAlertRule(id)
  ElMessage.success('删除成功')
  load()
}

async function load() {
  rules.value = (await listAlertRules()).data
}

onMounted(load)
</script>
