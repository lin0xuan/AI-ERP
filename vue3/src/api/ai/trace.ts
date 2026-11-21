import request from '@/utils/request'
export function getAiTraces(sessionId?: string) { return request({ url: '/api/ai/trace', method: 'get', params: sessionId ? { sessionId } : undefined }) }
export function getAiTrace(id: string) { return request({ url: `/api/ai/trace/${encodeURIComponent(id)}`, method: 'get' }) }
