import request from '@/utils/request'
export async function operations(path: string, method: 'get' | 'post' | 'put' = 'get', data?: unknown, params?: Record<string, unknown>) {
  const response: any = await request({ url: `/api/erp-api/operations/${path}`, method, data, params })
  return response.data
}
