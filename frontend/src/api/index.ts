import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const client = axios.create({ baseURL: '/api', timeout: 15000 })
client.interceptors.request.use(config => { const token = localStorage.getItem('lab-token'); if (token) config.headers.Authorization = `Bearer ${token}`; return config })
client.interceptors.response.use(response => { const body = response.data; if (body && body.success === false) { ElMessage.error(body.message || '请求失败'); return Promise.reject(new Error(body.message)) } return body?.data ?? body }, error => { if (error.response?.status === 401) { localStorage.removeItem('lab-token'); location.hash = '#/login' } ElMessage.error(error.response?.data?.message || '服务暂不可用'); return Promise.reject(error) })

export const api = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> { return client.get(url, config) as unknown as Promise<T> },
  post<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> { return client.post(url, data, config) as unknown as Promise<T> },
  put<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> { return client.put(url, data, config) as unknown as Promise<T> }
}
