import request from '@/utils/request'

const AI_TIMEOUT = 120000

export function analyzeSales() {
  return request({
    url: '/api/erp-api/ai/analysis/sales',
    method: 'post',
    timeout: AI_TIMEOUT,
  })
}

export function optimizeInventory(data: { inventoryData?: string; salesData?: string }) {
  return request({
    url: '/api/erp-api/ai/analysis/inventory',
    method: 'post',
    data,
    timeout: AI_TIMEOUT,
  })
}

export function analyzeCustomerInsights(data: { customerData?: string; orderData?: string }) {
  return request({
    url: '/api/erp-api/ai/analysis/customer',
    method: 'post',
    data,
    timeout: AI_TIMEOUT,
  })
}

export function analyzeOperationEfficiency(data: { operationData?: string }) {
  return request({
    url: '/api/erp-api/ai/analysis/operation',
    method: 'post',
    data,
    timeout: AI_TIMEOUT,
  })
}

export function customAnalysis(data: { prompt?: string }) {
  return request({
    url: '/api/erp-api/ai/analysis/custom',
    method: 'post',
    data,
    timeout: AI_TIMEOUT,
  })
}

export function getHistoryList(params: { pageNum: number; pageSize: number }) {
  return request({
    url: '/api/erp-api/ai/analysis/history',
    method: 'get',
    params,
  })
}

export function getHistoryDetail(id: number) {
  return request({
    url: `/api/erp-api/ai/analysis/history/${id}`,
    method: 'get',
  })
}
