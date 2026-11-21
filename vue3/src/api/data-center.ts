import request from '@/utils/request'

const base = '/api/erp-api/data-center'
export async function dcGet(path: string, params?: Record<string, unknown>) {
  const response: any = await request({ url: `${base}/${path}`, params })
  return response.data
}
export async function dcSync() {
  const response: any = await request({ url: `${base}/sync`, method: 'post', timeout: 330000 })
  return response.data
}
export async function dcMapping(data: Record<string, string>) {
  return request({ url: `${base}/mappings`, method: 'put', data })
}
