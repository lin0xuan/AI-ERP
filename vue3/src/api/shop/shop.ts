import request from '@/utils/request'

export function listPlatform(query?: Record<string, any>) {
  return request({
    url: '/api/oms-api/shop/platformList',
    method: 'get',
    params: query
  })
}

export function updatePlatform(data: Record<string, any>) {
  return request({ url: '/api/oms-api/shop/platform', method: 'put', data })
}

export function listShop(query?: Record<string, any>) {
  return request({ url: '/api/oms-api/shop/list', method: 'get', params: query })
}

export function listShopPage(query?: Record<string, any>) {
  return request({ url: '/api/oms-api/shop/pageList', method: 'get', params: query })
}

export function getShop(id: number | string) {
  return request({ url: '/api/oms-api/shop/shop/' + id, method: 'get' })
}

export function addShop(data: Record<string, any>) {
  return request({ url: '/api/oms-api/shop/shop', method: 'post', data })
}

export function updateShop(data: Record<string, any>) {
  return request({ url: '/api/oms-api/shop/shop', method: 'put', data })
}

export function delShop(id: number | string) {
  return request({ url: '/api/oms-api/shop/shop/' + id, method: 'delete' })
}

export function listShopPullLogs(query?: Record<string, any>) {
  return request({ url: '/api/oms-api/shop/pull_logs_list', method: 'get', params: query })
}

export function getShopPullLasttime(shopId: number | string) {
  return request({ url: '/api/oms-api/shop/pull_lasttime/' + shopId, method: 'get' })
}

export function saveShopPullLasttime(data: { shopId: number | string; pullType: string; lasttime: string }) {
  return request({ url: '/api/oms-api/shop/pull_lasttime', method: 'post', data })
}
