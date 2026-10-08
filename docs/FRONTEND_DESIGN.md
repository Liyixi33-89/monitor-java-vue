# 前端技术设计文档

> 对应需求文档：`docs/REQUIREMENTS.md`
> 项目名：`monitor-vite`（Vite 构建的监控面板）

## 1. 技术选型

| 模块 | 选型 | 说明 |
|---|---|---|
| 构建工具 | Vite 5 | 项目既定名称 `monitor-vite` |
| 框架 | Vue 3（`<script setup>`） | 团队熟悉度高，生态成熟；MVP 用 JS 实现（规划中的 TS 可在迭代中引入） |
| 组件库 | Element Plus | 表格/卡片/弹窗等组件齐全，适合中后台监控面板 |
| 图表 | ECharts（`vue-echarts`） | 趋势折线图、迷你 sparkline |
| 状态管理 | ~~Pinia~~ → 页面内组合式函数 | MVP 未引入 Pinia；JWT 存 localStorage，数据在页面级请求 |
| 路由 | vue-router | 多页面导航 |
| HTTP | axios | 统一拦截器处理鉴权/错误 |
| 实时通信 | 原生 WebSocket + 封装 hook | 对接后端 `/ws/realtime` |
| 样式 | SCSS + Element Plus 主题变量 | |

## 2. 目录结构

> 实现说明：MVP 阶段使用 **JavaScript（非 TS）**，未引入 Pinia（数据经各页面
> 组合式函数直接请求），API 定义合并为单一 `api/index.js`。路由/页面结构与下述规划一致。

```
web/
├── index.html
├── vite.config.js                            # 代理 /api、/ws → 本地后端 48080
├── sdk/
│   └── monitor-sdk.js                        # 错误采集 SDK（已注入两个线上前端）
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── router/
│   │   └── index.js                # /login /dashboard /projects/:id /trends /alerts /errors /settings
│   ├── api/
│   │   ├── request.js               # axios 实例 + 拦截器（JWT 注入、401 跳登录）
│   │   └── index.js                 # 全部 API 定义（合并了规划中的 auth/projects/metrics 等分散文件）
│   ├── composables/
│   │   └── useRealtimeSocket.js     # WebSocket 封装
│   ├── views/
│   │   ├── login/
│   │   │   └── LoginView.vue
│   │   ├── dashboard/
│   │   │   └── DashboardView.vue    # F10 总览页
│   │   ├── projects/
│   │   │   ├── ProjectListView.vue  # F11 项目列表（按项目分组，前后端卡片）
│   │   │   └── ProjectDetailView.vue# F11a 项目详情（进程信息+报错列表+健康检查历史）
│   │   ├── trends/
│   │   │   └── TrendsView.vue       # F12 历史趋势页，1h/6h/24h/7d 切换
│   │   ├── errors/
│   │   │   └── ErrorMonitorView.vue # F13a 错误监控汇总页
│   │   ├── alerts/
│   │   │   └── AlertLogView.vue     # F13 告警记录页
│   │   └── settings/
│   │       ├── ProjectManageView.vue   # F20 项目管理（新增/编辑/删除）
│   │       └── AlertRuleManageView.vue # F8 告警规则配置
│   ├── layouts/
│   │   └── BasicLayout.vue          # 侧边导航 + 顶部栏
│   └── utils/
│       └── format.js                # 字节/时间格式化
└── package.json
```

## 3. 路由与页面规划

| 路径 | 页面 | 对应需求 |
|---|---|---|
| `/login` | 登录页 | F18 |
| `/dashboard` | 总览：服务器资源卡片 + 迷你趋势 | F10 |
| `/projects` | 项目列表（按组展示 MD Viewer / TaskManager，组内前端/后端卡片） | F11 |
| `/projects/:id` | 项目详情（进程、健康检查历史、报错列表、资源趋势） | F11a |
| `/trends` | 历史趋势（时间范围切换） | F12 |
| `/errors` | 错误监控汇总（前后端报错流，筛选） | F13a |
| `/alerts` | 告警记录 | F13 |
| `/settings/projects` | 项目管理 CRUD | F20 |
| `/settings/alert-rules` | 告警规则配置 | F8 |

路由守卫：未登录跳转 `/login`；JWT 过期自动登出。

## 4. 核心数据类型（`types/project.ts`）

```ts
export type ProjectType = 'frontend' | 'backend'
export type ProjectStatus = 'online' | 'error' | 'offline'

export interface Project {
  id: number
  groupName: string        // 'md-viewer' | 'taskmanager'
  name: string              // 'MD Viewer 前端'
  type: ProjectType
  serverId: number
  runtime: string           // 'static' | 'springboot' | 'node-pm2' | 'node-koa'
  accessUrl: string
  healthCheckUrl: string
  status: ProjectStatus
  recentErrorCount24h: number
  lastHealthCheckAt: string
}

export interface ProjectGroup {
  groupName: string
  displayName: string       // 'MD Viewer'
  frontend?: Project
  backend?: Project
}
```

## 5. 关键页面设计

### 5.1 项目列表页（`ProjectListView.vue`）— 核心页面
- 按 `groupName` 聚合为 `ProjectGroup[]`，一行一组：左侧组名（如“MD Viewer”），
  右侧并排两个 `ProjectTypeBadge` 卡片：**前端卡片** + **后端卡片**。
- 每个子卡片展示：状态点（绿/红/灰）、运行时标签（springboot/node-pm2 等）、
  最近 24h 报错数（红色徽标，>0 高亮）、最近健康检查结果、点击进入详情。
- 顶部提供 Tab 快速筛选：全部 / 仅前端 / 仅后端 / 仅异常。

```vue
<ProjectGroupCard
  v-for="g in projectGroups" :key="g.groupName"
  :group="g"
  @click-project="goDetail"
/>
```

### 5.2 项目详情页（`ProjectDetailView.vue`）
Tab 结构：
- 概览：基础信息 + 当前状态 + 最近健康检查
- 资源趋势：CPU/内存折线图（后端项目）或 Nginx 4xx/5xx 趋势（前端项目）
- 报错列表：表格（时间、类型、message 摘要、次数），点击行打开 `ErrorDetailDrawer`
- 健康检查历史：时间轴/表格

### 5.3 错误监控汇总页（`ErrorMonitorView.vue`）
- 顶部筛选：项目（多选）、类型（js_error/api_error/process_crash/http_5xx/log_error...）、级别、时间范围
- 列表字段：发生项目（带前端/后端标签）、错误类型、message、首次/最近发生时间、累计次数、状态（open/resolved）
- 支持一键"标记已处理"（PATCH `/api/errors/:id` status=resolved）

### 5.4 Dashboard 总览页（`DashboardView.vue`）
- 服务器资源卡片（CPU/内存/磁盘/网络 + 迷你 sparkline）
- 项目健康矩阵：4 个服务（2 项目 × 前后端）状态灯一览，点击跳转详情
- 最近告警 Top5

## 6. 实时数据方案

`useRealtimeSocket.ts` 封装：
```ts
export function useRealtimeSocket() {
  const socket = new WebSocket(`${WS_BASE}/ws/realtime`)
  socket.onmessage = (evt) => {
    const msg = JSON.parse(evt.data)
    // msg.type: 'metrics_host' | 'metrics_process' | 'error_event' | 'alert'
    dispatchToStore(msg)
  }
  // 断线重连：指数退避，最大间隔 30s
}
```
Dashboard/项目列表优先消费 WS 推送；若 WS 断开，自动降级为 `setInterval` 轮询 REST 接口（15s）。

## 7. 前端错误采集 SDK（供被监控的前端项目接入）

独立输出一个轻量包 `web/sdk/monitor-sdk.js`（<5KB，无依赖），供 MD Viewer 前端 / TaskManager 前端引入。
**已于 2026-10-08 实际注入两个线上项目**的 `index.html`（`projectId=1` MD Viewer 前端、`projectId=3` TaskManager 前端，
与 `V1__init.sql` 中 projects 表 ID 对应），report-url 指向生产端口 4000：

```html
<!-- 线上实际注入的标签（两处仅 projectId / data-app-name 不同） -->
<script src="/monitor-sdk.js"
        data-project-id="1"
        data-app-name="md-viewer-frontend"
        data-report-url="http://111.231.57.177:4000/api/error/report"
        data-sample-rate="1"></script>
```

SDK 实际能力（`web/sdk/monitor-sdk.js`，与下述示例逻辑一致并另有增强）：
- `window.onerror` / `unhandledrejection` / 资源加载失败（捕获阶段）三类异常采集
- `fetch` 与 `XMLHttpRequest` 双拦截（接口 4xx/5xx/网络错误/超时 → `api_error`）
- `navigator.sendBeacon` 优先，降级 `fetch(keepalive)`
- **本地节流**：同一错误（message+stack 前 200 字符）10 秒内只上报一次
- **采样率** `data-sample-rate`（0~1，默认 1 全量）
(function () {
  var cfg = document.currentScript.dataset
  function report(type, payload) {
    var body = JSON.stringify(Object.assign({
      projectId: cfg.projectId, type: type, url: location.href,
      userAgent: navigator.userAgent, timestamp: Date.now()
    }, payload))
    if (navigator.sendBeacon) {
      navigator.sendBeacon(cfg.reportUrl, body)
    } else {
      fetch(cfg.reportUrl, { method: 'POST', body: body, keepalive: true })
    }
  }
  window.addEventListener('error', function (e) {
    if (e.target && e.target.tagName) {
      report('resource_error', { message: e.target.src || e.target.href })
    } else {
      report('js_error', { message: e.message, stack: e.error && e.error.stack })
    }
  }, true)
  window.addEventListener('unhandledrejection', function (e) {
    report('unhandledrejection', { message: String(e.reason), stack: e.reason && e.reason.stack })
  })
  var _fetch = window.fetch
  window.fetch = function () {
    var url = arguments[0]
    return _fetch.apply(this, arguments).then(function (res) {
      if (!res.ok) report('api_error', { message: 'HTTP ' + res.status, url: String(url) })
      return res
    }).catch(function (err) {
      report('api_error', { message: err.message, url: String(url) })
      throw err
    })
  }
})()
```

该 SDK 不依赖本项目构建产物，可独立复制到被监控前端项目的 `public/` 下并在 `index.html` 引入一行 `<script>`，
不改变被监控项目原有业务代码（符合"不干扰业务代码"的接入要求，待用户确认是否允许接入）。

## 8. 响应式与权限

- 布局采用 Element Plus `el-container` + 栅格，侧边栏在 <768px 自动收起为顶部汉堡菜单（F14）。
- 路由守卫 + axios 拦截器统一处理 401（跳转登录）。
- 项目管理/告警规则管理页面仅登录用户可见（后续可扩展角色）。

## 9. 构建与部署

```bash
cd web
npm install
npm run dev        # 本地开发，默认代理 /api 与 /ws 到 http://localhost:48080（本机 4000 被占，开发端口用 48080）
npm run build       # 产出 dist/，部署到任意静态服务器（可与监控后端同机 Nginx 托管）
```

`vite.config.js` 关键配置（实际文件）：
```js
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:48080',
      '/ws': { target: 'ws://localhost:48080', ws: true }
    }
  }
})
```

> 端口约定：开发环境后端 48080（`--server.port=48080` 临时覆盖），生产环境后端 4000；
> 前端通过 Nginx 反代 `/api` 到后端，无需区分端口。
