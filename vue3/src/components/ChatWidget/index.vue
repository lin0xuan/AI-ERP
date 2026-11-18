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
          <el-button text size="small" :disabled="loading || !sessionId" @click="removeConversation">删除对话</el-button>
          <el-button text size="small" aria-label="关闭AI助手" @click="close">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <div class="chat-messages" ref="messagesRef">
          <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
            <div class="message-bubble" v-html="renderMarkdown(filterContent(msg.content))"></div>
          </div>
          <div v-if="streamingText !== null" class="message assistant">
            <div class="message-bubble streaming">
              <template v-if="streamingText"><span v-html="renderMarkdown(filterContent(streamingText))"></span></template>
              <template v-else><span class="thinking-text">思考中</span></template>
              <span class="loading-dots"><span>.</span><span>.</span><span>.</span></span>
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
import { ElMessage, ElMessageBox } from 'element-plus'

interface Msg {
  sessionId: string
  role: 'user' | 'assistant'
  content: string
}

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
let restoreRequest = 0

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
          } else if (type === 'session') {
            if (content) sessionId.value = content
          } else if (type === 'memory') {
            memoryNotice.value = content
          } else if (type === 'error') {
            messages.value.push({ sessionId: sessionId.value, role: 'assistant', content })
            streamingText.value = null
          }
        } catch {
          // ignore non-JSON data
        }
      }
    }

    streamingText.value = null
    if (fullContent) {
      messages.value.push({ sessionId: sessionId.value, role: 'assistant', content: fullContent })
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
