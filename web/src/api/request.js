import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({ baseURL: '/api', timeout: 15000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (resp) => resp.data,
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('token')
      router.push({ name: 'login' })
    } else {
      ElMessage.error(err.response?.data?.error || err.message || '请求失败')
    }
    return Promise.reject(err)
  }
)

export default request
