import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

service.interceptors.request.use(
  config => {
    const token = localStorage.getItem('plm_token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('plm_token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  error => {
    const msg = error.response?.data?.message || error.message || '网络异常'
    if (error.response?.status === 401) {
      ElMessageBox.confirm('登录已过期,请重新登录', '提示', {
        confirmButtonText: '重新登录',
        type: 'warning',
        showCancelButton: false
      }).then(() => {
        localStorage.removeItem('plm_token')
        router.push('/login')
      })
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default service
