<template>
  <el-container style="height: 100%">
    <el-aside :width="isCollapsed ? '64px' : '200px'" style="border-right: 1px solid #e4e7ed">
      <div style="padding: 16px; font-weight: 700; font-size: 15px; white-space: nowrap; overflow: hidden">
        {{ isCollapsed ? 'M' : '项目监控面板' }}
      </div>
      <el-menu :default-active="$route.path" :collapse="isCollapsed" router>
        <el-menu-item index="/dashboard"><span>总览</span></el-menu-item>
        <el-menu-item index="/projects"><span>项目列表</span></el-menu-item>
        <el-menu-item index="/trends"><span>历史趋势</span></el-menu-item>
        <el-menu-item index="/errors"><span>错误监控</span></el-menu-item>
        <el-menu-item index="/alerts"><span>告警记录</span></el-menu-item>
        <el-menu-item index="/settings/projects"><span>项目管理</span></el-menu-item>
        <el-menu-item index="/settings/alert-rules"><span>告警规则</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e4e7ed">
        <span style="font-weight: 600">{{ $route.meta.title || '' }}</span>
        <div>
          <el-button text @click="isCollapsed = !isCollapsed">折叠</el-button>
          <el-button text type="danger" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main style="background: #f5f7fa">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const isCollapsed = ref(false)

function logout() {
  localStorage.removeItem('token')
  router.push({ name: 'login' })
}
</script>
