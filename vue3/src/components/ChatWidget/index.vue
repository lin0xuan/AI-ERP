<template>
  <div class="chat-widget">
    <el-button v-if="!visible" class="chat-trigger" type="primary" circle aria-label="打开AI助手" @click="open">
      <el-icon :size="24"><ChatDotRound /></el-icon>
    </el-button>

    <Transition name="slide">
      <div v-if="visible" class="chat-panel">
        <div class="chat-header">
          <span class="chat-title">AI 助手</span>
          <el-select v-model="selectedSession" placeholder="历史对话" :disabled="loading" @change="restoreSession" style="width: 150px">
            <el-option v-for="id in sessions" :key="id" :label="id.slice(0, 8)" :value="id" />
          </el-select>
          <el-button text size="small" :disabled="loading" @click="newConversation">新建对话</el-button>
          <el-button text size="small" @click="showMemory">记忆</el-button>
          <el-button text size="small" :disabled="!sessionId" @click="showTraces">执行过程</el-button>
          <el-button text size="small" :disabled="loading || !sessionId" @click="removeConversation">删除对话</el-button>
          <el-button text size="small" aria-label="关闭AI助手" @click="close">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <div class="chat-messages" ref="messagesRef">
          <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
            <div class="message-stack">
              <div class="message-bubble" v-html="renderMarkdown(filterContent(msg.content))"></div>
              <button v-if="msg.traceId" type="button" class="trace-link" @click="openTrace(msg.traceId)">查看执行过程</button>
            </div>
          </div>
          <div v-if="streamingText !== null" class="message assistant">
            <div class="message-stack">
              <div class="message-bubble streaming">
                <template v-if="streamingText"><span v-html="renderMarkdown(filterContent(streamingText))"></span></template>
                <template v-else><span class="thinking-text">思考中</span></template>
                <span class="loading-dots"><span>.</span><span>.</span><span>.</span></span>
              </div>
              <ol v-if="liveSteps.length" class="trace-live">
                <li v-for="step in liveSteps" :key="`${step.step}-${step.status}`">{{ step.step }}. {{ step.name }} · {{ stepLabel(step.status) }}<span v-if="step.preview"> — {{ step.preview }}</span></li>
              </ol>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            placeholder="输入消息... (Ctrl+Enter 发送)"
            :disabled="loading"
            @keydown.enter.ctrl="send"
          />
          <el-button type="primary" :loading="loading" @click="send" style="margin-left: 8px">
            发送
          </el-button>
        </div>
        <div v-if="memoryNotice" class="memory-notice" role="status">{{ memoryNotice }}</div>
      </div>
    </Transition>
    <el-drawer v-model="memoryVisible" title="我的记忆" size="min(560px, 100vw)" append-to-body>
      <p>说“请记住：我的……”可立即保存。事实更新会保留旧版本；删除记忆后将停止使用。对话原文需在聊天窗口单独删除。</p>
      <p class="memory-notice">{{ semanticSearch ? '已配置关键词＋向量检索' : '当前使用关键词检索；配置向量模型后自动启用混合检索' }}</p>
      <el-button :loading="memoryLoading" @click="showMemory">刷新</el-button>
      <el-tabs>
        <el-tab-pane label="长期记忆">
          <el-empty v-if="!memoryItems.length" description="暂无长期记忆" />
          <article v-for="item in memoryItems" :key="item.id" class="memory-card">
            <strong>{{ item.fact_key }}</strong>
            <p>{{ item.content || '内容已删除' }}</p>
            <small>{{ item.status }} · 重要性 {{ item.importance }}/10 · v{{ item.version }} · {{ new Date(item.updated_at).toLocaleString() }}</small>
            <div>
              <el-button text @click="showRevisions(item.id)">旧版本</el-button>
              <el-button v-if="item.status !== 'forgotten'" text type="danger" @click="removeMemory(item.id)">忘记</el-button>
            </div>
          </article>
        </el-tab-pane>
        <el-tab-pane label="当前工作记录">
          <el-empty v-if="!workItems.length" description="暂无工具执行记录" />
          <article v-for="item in workItems" :key="item.id" class="memory-card">
            <strong>步骤 {{ item.step_no }} · {{ item.tool_name }} · {{ item.status }}</strong>
            <details><summary>参数与结果</summary><pre>{{ item.arguments }}
{{ item.result }}</pre></details>
          </article>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
    <el-drawer v-model="traceVisible" title="执行过程" size="min(560px, 100vw)" append-to-body>
      <div v-if="traceDetail" class="trace-detail">
        <el-button text @click="traceDetail = null">返回列表</el-button>
        <p><strong>{{ statusLabel(traceDetail.status) }}</strong> · {{ traceDetail.model || '未记录模型' }} · {{ duration(traceDetail) }}</p>
        <p>指令：{{ traceDetail.input_text }}</p>
        <p>编号：{{ traceDetail.id }} <el-button text @click="copyTrace(traceDetail.id)">复制</el-button></p>
        <p v-if="traceDetail.error_message" class="trace-error">{{ traceDetail.error_message }}</p>
        <h4>过程</h4>
        <el-timeline>
          <el-timeline-item v-for="event in traceDetail.events || []" :key="event.id" :timestamp="eventTime(event)">
            <strong>{{ eventLabel(event) }} · {{ statusLabel(event.status) }}</strong>
            <pre v-if="event.input_text">{{ event.input_text }}</pre>
            <pre v-if="event.output_text">{{ event.output_text }}</pre>
          </el-timeline-item>
        </el-timeline>
        <h4>工具调用</h4>
        <el-empty v-if="!(traceDetail.toolCalls || []).length" description="这次没有调用工具" />
        <article v-for="call in traceDetail.toolCalls || []" :key="call.id" class="memory-card">
          <strong>步骤 {{ call.step_no }} · {{ call.tool_name }} · {{ statusLabel(call.status) }}</strong>
          <pre>{{ call.arguments }}
{{ call.result }}</pre>
        </article>
        <h4>关联业务</h4>
        <el-empty v-if="!(traceDetail.links || []).length" description="没有识别到采购单、库存或业务单据" />
        <p v-for="link in traceDetail.links || []" :key="link.kind + link.business_id">{{ link.label }} <el-button text @click="copyTrace(link.business_id)">复制编号</el-button></p>
        <p class="memory-notice">创建、审批、入库和出库不会从这里自动重做。模型 token 用量尚未记录。</p>
      </div>
      <div v-else>
        <el-empty v-if="!traceList.length" description="当前对话还没有执行记录" />
        <button v-for="item in traceList" :key="item.id" type="button" class="trace-row" @click="openTrace(item.id)">
          <strong>{{ statusLabel(item.status) }}</strong>
          <span>{{ item.input_preview || item.id }}</span>
          <small>{{ duration(item) }}</small>
        </button>
      </div>
    </el-drawer>
    <el-dialog v-model="revisionsVisible" title="记忆更新记录" width="min(520px, 95vw)" append-to-body>
      <el-empty v-if="!revisions.length" description="没有旧版本" />
      <article v-for="item in revisions" :key="item.version" class="memory-card">
        <strong>v{{ item.version }} · {{ item.reason }}</strong><p>{{ item.content }}</p>
      </article>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getChatHistory, getChatSessions, endChatSession, deleteChatSession, getMemories, forgetMemory, getMemoryRevisions, getWorkMemory } from '@/api/ai/chat'
import { getAiTraces, getAiTrace } from '@/api/ai/trace'
import { ElMessage, ElMessageBox } from 'element-plus'

interface Msg {
  sessionId: string
  role: 'user' | 'assistant'
  content: string
  traceId?: string
}
interface TraceStep { step: number; name: string; status: string; preview?: string }

const visible = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref<Msg[]>([])
const sessionId = ref('')
const selectedSession = ref('')
const sessions = ref<string[]>([])
const streamingText = ref<string | null>(null)
const messagesRef = ref<HTMLElement>()
const abortRef = ref<AbortController | null>(null)
const memoryNotice = ref('')
const memoryVisible = ref(false)
const memoryLoading = ref(false)
const semanticSearch = ref(false)
const memoryItems = ref<any[]>([])
const workItems = ref<any[]>([])
const revisions = ref<any[]>([])
const revisionsVisible = ref(false)
const traceVisible = ref(false)
const traceList = ref<any[]>([])
const traceDetail = ref<any>(null)
const liveSteps = ref<TraceStep[]>([])
const currentTraceId = ref('')
let restoreRequest = 0

function statusLabel(status: string) {
  const labels: Record<string, string> = { RUNNING: '进行中', COMPLETED: '完成', FAILED: '失败', running: '进行中', completed: '完成', failed: '失败' }
  return labels[status] || status || '未知'
}
function stepLabel(status: string) { return statusLabel(status) }
function eventLabel(event: any) {
  if (event.type === 'MODEL') return `模型 ${event.name || ''}`.trim()
  if (event.type === 'TOOL') return `工具 ${event.name || ''}`.trim()
  return `${event.type || '步骤'} ${event.name || ''}`.trim()
}
function eventTime(event: any) {
  if (!event.started_at) return ''
  const elapsed = event.finished_at ? ` · ${Math.max(0, event.finished_at - event.started_at)} 毫秒` : ''
  return new Date(event.started_at).toLocaleString('zh-CN', { hour12: false }) + elapsed
}
function duration(row: any) {
  if (!row?.started_at) return ''
  if (!row.finished_at) return '进行中'
  const ms = Math.max(0, row.finished_at - row.started_at)
  return ms < 1000 ? `${ms} 毫秒` : `${(ms / 1000).toFixed(1)} 秒`
}
async function copyTrace(id: string) {
  try { await navigator.clipboard.writeText(id); ElMessage.success('已复制') }
  catch { ElMessage.error('复制失败') }
}
async function showTraces() {
  traceVisible.value = true
  traceDetail.value = null
  if (!sessionId.value) { traceList.value = []; return }
  try {
    const result: any = await getAiTraces(sessionId.value)
    traceList.value = result.data || []
  } catch { ElMessage.error('执行过程加载失败') }
}
async function openTrace(id: string) {
  traceVisible.value = true
  try {
    const result: any = await getAiTrace(id)
    traceDetail.value = result.data
  } catch { ElMessage.error('这条执行记录加载失败') }
}

async function showMemory() {
  memoryVisible.value = true
  memoryLoading.value = true
  try {
    const result: any = await getMemories()
    memoryItems.value = result.data.items
    semanticSearch.value = result.data.semanticSearch
    const work: any = sessionId.value ? await getWorkMemory(sessionId.value) : { data: [] }
    workItems.value = work.data
  } catch { ElMessage.error('记忆加载失败') }
  finally { memoryLoading.value = false }
}
async function removeMemory(id: string) {
  try { await ElMessageBox.confirm('停止使用这条记忆并删除其旧版本？对话原文仍会保留。', '忘记这条记忆') }
  catch { return }
  try { await forgetMemory(id); await showMemory() } catch { ElMessage.error('删除失败，请稍后重试') }
}
async function showRevisions(id: string) {
  try { const result: any = await getMemoryRevisions(id); revisions.value = result.data; revisionsVisible.value = true }
  catch { ElMessage.error('版本记录加载失败') }
}
async function removeConversation() {
  if (loading.value || !sessionId.value) return
  try { await ElMessageBox.confirm('删除当前对话和工作记录？已提取的长期记忆可在记忆面板单独删除。', '删除对话') }
  catch { return }
  try {
    const id = sessionId.value
    await deleteChatSession(id)
    sessions.value = sessions.value.filter(value => value !== id)
    sessionId.value = ''; newConversation()
  } catch { ElMessage.error('删除对话失败') }
}

async function restoreSession(id: string) {
  if (loading.value) return
  const request = ++restoreRequest
  try {
    const result: any = await getChatHistory(id)
    if (request !== restoreRequest) return
    messages.value = result.data || []
    sessionId.value = id
    selectedSession.value = id
    scrollToBottom()
  } catch { ElMessage.error('历史对话加载失败') }
}

async function open() {
  try {
    const result: any = await getChatSessions()
    sessions.value = result.data || []
    if (!sessionId.value && sessions.value.length) await restoreSession(sessions.value[0]!)
  } catch { ElMessage.error('会话列表加载失败') }
  visible.value = true
  if (!messages.value.length) {
    messages.value.push({
      sessionId: '',
      role: 'assistant',
      content: `你好！我是 AI ERP 智能助手，可以帮你：

**📦 商品查询** — 搜索商品名称、SKU编码、价格、规格等信息

你可以这样问我：
- "搜索商品名中含黄金的商品"
- "帮我查一下SKU编码SS001-YN"`
    })
  }
}

function close() {
  if (abortRef.value) {
    abortRef.value.abort()
    abortRef.value = null
  }
  if (!loading.value && sessionId.value) endChatSession(sessionId.value).catch(() => {})
  visible.value = false
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  input.value = ''
  loading.value = true
  ++restoreRequest
  memoryNotice.value = ''
  streamingText.value = ''
  liveSteps.value = []
  currentTraceId.value = ''

  if (!sessionId.value) {
    sessionId.value = crypto.randomUUID()
    selectedSession.value = sessionId.value
    sessions.value.unshift(sessionId.value)
  }

  messages.value.push({ sessionId: sessionId.value, role: 'user', content: text })
  scrollToBottom()

  const token = getToken()
  abortRef.value = new AbortController()

  try {
    const response = await fetch('/api/ai/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ sessionId: sessionId.value, message: text }),
      signal: abortRef.value.signal
    })

    if (!response.ok) {
      const errText = await response.text().catch(() => '')
      messages.value.push({
        sessionId: sessionId.value,
        role: 'assistant',
        content: `请求失败 (${response.status}): ${errText}`
      })
      streamingText.value = null
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let fullContent = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const t = line.trim()
        if (!t || !t.startsWith('data:')) continue

        const jsonStr = t.slice(5).trim()
        if (!jsonStr) continue

        try {
          const event = JSON.parse(jsonStr)
          const type = event.type
          const content = event.content || ''

          if (type === 'message') {
            fullContent += content
            streamingText.value = fullContent
            scrollToBottom()
          } else if (type === 'trace') {
            currentTraceId.value = content
          } else if (type === 'step') {
            const step = JSON.parse(content) as TraceStep
            const index = liveSteps.value.findIndex(item => item.step === step.step && item.name === step.name)
            if (index >= 0) liveSteps.value[index] = step
            else liveSteps.value.push(step)
            scrollToBottom()
          } else if (type === 'session') {
            if (content) sessionId.value = content
          } else if (type === 'memory') {
            memoryNotice.value = content
          } else if (type === 'error') {
            messages.value.push({ sessionId: sessionId.value, role: 'assistant', content, traceId: currentTraceId.value || undefined })
            streamingText.value = null
          }
        } catch {
          // ignore non-JSON data
        }
      }
    }

    streamingText.value = null
    if (fullContent) {
      messages.value.push({ sessionId: sessionId.value, role: 'assistant', content: fullContent, traceId: currentTraceId.value || undefined })
    }
  } catch (e: any) {
    if (e.name === 'AbortError') return
    streamingText.value = null
    messages.value.push({
      sessionId: sessionId.value,
      role: 'assistant',
      content: '网络错误: ' + (e.message || '请检查网络连接')
    })
  } finally {
    loading.value = false
    streamingText.value = null
    abortRef.value = null
    scrollToBottom()
  }
}

function newConversation() {
  ++restoreRequest
  if (sessionId.value) endChatSession(sessionId.value).catch(() => {})
  if (abortRef.value) {
    abortRef.value.abort()
    abortRef.value = null
  }
  messages.value = []
  sessionId.value = ''
  selectedSession.value = ''
  streamingText.value = null
  input.value = ''
  memoryNotice.value = ''
}

function filterContent(text: string): string {
  return text.replace(/\n{3,}/g, '\n\n')
}

function renderMarkdown(text: string): string {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text) as string)
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.memory-notice { padding: 8px 12px; color: #667085; font-size: 12px; }
.message-stack { max-width: 80%; display: flex; flex-direction: column; gap: 4px; }
.message-stack .message-bubble { max-width: 100%; }
.trace-link, .trace-row { border: 0; background: transparent; color: #409eff; text-align: left; cursor: pointer; font-size: 12px; padding: 0; }
.trace-row { display: flex; flex-direction: column; gap: 2px; width: 100%; padding: 10px 0; border-bottom: 1px solid #e4e7ed; color: #263446; }
.trace-live { margin: 0; padding-left: 18px; color: #667085; font-size: 12px; }
.trace-detail pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #f6f8fa; padding: 8px; border-radius: 6px; }
.trace-error { color: #c45656; }
.memory-card { padding: 12px 0; border-bottom: 1px solid #e4e7ed; overflow-wrap: anywhere; }
.memory-card p, .memory-card pre { white-space: pre-wrap; overflow-wrap: anywhere; }
.chat-header { flex-wrap: wrap; gap: 4px; }
.chat-widget {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000;
}

.chat-trigger {
  width: 56px;
  height: 56px;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.chat-panel {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 520px;
  height: 720px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  background: #f8f9fa;
}

.chat-title {
  font-weight: 600;
  margin-right: auto;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.user .message-bubble {
  background: #409eff;
  color: #fff;
  border-radius: 12px 12px 4px 12px;
}

.message.assistant .message-bubble {
  background: #f0f2f5;
  color: #333;
  border-radius: 12px 12px 12px 4px;
}

.message-bubble {
  max-width: 80%;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message-bubble p {
  margin: 4px 0;
}

.message-bubble p:first-child {
  margin-top: 0;
}

.message-bubble p:last-child {
  margin-bottom: 0;
}

.message-bubble ul, .message-bubble ol {
  margin: 4px 0;
  padding-left: 20px;
}

.message-bubble li {
  margin: 2px 0;
}

.message-bubble code {
  background: rgba(0,0,0,0.06);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 13px;
  font-family: monospace;
}

.message-bubble pre {
  background: rgba(0,0,0,0.06);
  padding: 8px 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 6px 0;
}

.message-bubble pre code {
  background: none;
  padding: 0;
}

.message-bubble strong {
  font-weight: 600;
}

.thinking-text {
  color: #999;
}

.streaming .loading-dots span {
  animation: dot-blink 1.4s infinite;
  font-weight: bold;
  font-size: 18px;
  line-height: 1;
  color: #409eff;
}

.streaming .loading-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.streaming .loading-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dot-blink {
  0%, 20% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}

.chat-input {
  display: flex;
  align-items: flex-end;
  padding: 12px;
  border-top: 1px solid #eee;
}

.chat-input :deep(.el-textarea) {
  flex: 1;
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}
</style>
