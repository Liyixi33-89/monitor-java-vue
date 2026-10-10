import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// Cookie 认证模式：HttpOnly Cookie 浏览器自动携带，只需开启 withCredentials
const request = axios.create({ baseURL: '/api', timeout: 15000, withCredentials: true })

// 401 防抖：短时间内并发多个请求失效时，只跳转一次登录页
let redirectingToLogin = false

request.interceptors.response.use(
  (resp) => resp.data,
  (err) => {
    if (err.response && err.response.status === 401) {
      // 登录态失效（Cookie 过期/被清）：跳登录页并提示
      if (!redirectingToLogin && router.currentRoute.value.name !== 'login') {
        redirectingToLogin = true
        ElMessage.warning('登录已失效，请重新登录')
        router.push({ name: 'login' }).finally(() => {
          setTimeout(() => (redirectingToLogin = false), 1000)
        })
      }
    } else {
      ElMessage.error(err.response?.data?.error || err.message || '请求失败')
    }
    return Promise.reject(err)
  }
)

export default request
