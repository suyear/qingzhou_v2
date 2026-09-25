import axios from 'axios'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'

export const backendUnreachable = ref(false)

const http = axios.create({
  timeout: 15000,
})

let lastNetworkToastAt = 0
const NETWORK_TOAST_GAP_MS = 2500

function isNetworkFailure(err) {
  return !err.response && (err.code === 'ERR_NETWORK' || err.code === 'ECONNABORTED' || err.message === 'Network Error')
}

export function networkErrorMessage(err) {
  if (!err) return '网络异常'
  if (err.code === 'ECONNABORTED' || String(err.message || '').toLowerCase().includes('timeout')) {
    return '请求超时，请稍后重试'
  }
  if (isNetworkFailure(err)) {
    return '无法连接后端服务。请确认已启动 server（默认端口 18080），然后刷新页面。'
  }
  const status = err.response?.status
  const serverMsg = err.response?.data?.message
  if (status === 404) return serverMsg || '接口不存在（404）'
  if (status === 401 || status === 403) return serverMsg || '没有权限执行该操作'
  if (status >= 500) return serverMsg || '服务暂时不可用，请稍后重试'
  return serverMsg || err.message || '网络异常'
}

function toastError(message, isNetwork) {
  const now = Date.now()
  if (isNetwork && now - lastNetworkToastAt < NETWORK_TOAST_GAP_MS) {
    return
  }
  if (isNetwork) {
    lastNetworkToastAt = now
  }
  ElMessage.error(message)
}

http.interceptors.response.use(
  (res) => {
    backendUnreachable.value = false
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(body)
    }
    return body
  },
  (err) => {
    if (err.config?.silent) {
      if (isNetworkFailure(err)) {
        backendUnreachable.value = true
      }
      return Promise.reject(err)
    }
    const network = isNetworkFailure(err)
    if (network) {
      backendUnreachable.value = true
    }
    toastError(networkErrorMessage(err), network)
    return Promise.reject(err)
  },
)

export default http
