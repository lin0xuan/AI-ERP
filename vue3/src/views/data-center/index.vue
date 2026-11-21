<template>
  <main class="data-center" v-loading="loading">
    <header>
      <div><span class="eyebrow">AI ERP · 数据中台</span><h1>从业务数据到统一经营指标</h1><p>看板与 AI 复用同一份快照、同一套指标口径。</p></div>
      <el-button @click="reload">刷新</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert title="人民币业务口径；请勿将未转换币种的跨境金额纳入统计。库存为 ERP 自有库存，成功退款归属原订单支付期。" type="info" :closable="false" show-icon />
    <section class="filters">
      <label>历史快照</label>
      <el-select v-model="batch" placeholder="尚无成功快照" @change="loadData" style="width: 330px">
        <el-option v-for="job in successfulJobs" :key="job.id" :value="job.id" :label="`${formatTime(job.finished_at)} · ${job.id.slice(0, 8)}`" />
      </el-select>
      <el-tag v-if="batch">{{ batch.slice(0, 8) }}</el-tag>
      <span v-if="metrics.snapshot">映射版本 {{ metrics.snapshot.mapping_version }} · {{ catalog.metricVersion }}</span>
    </section>
    <el-tabs v-model="tab" @tab-change="loadRows">
      <el-tab-pane label="数据接入" name="sources">
        <div class="section-heading"><div><h2>来源与同步批次</h2><p>先由 ERP 拉取平台数据，再发布中台全量对账快照。任一来源失败时保留上一个成功版本。</p></div>
          <el-button type="primary" :disabled="!canManage" :loading="syncing" @click="synchronize">{{ syncing ? '同步中，请勿重复提交' : '同步并发布快照' }}</el-button></div>
        <el-table :data="catalog.sources || []"><el-table-column prop="kind" label="主题" width="130" /><el-table-column prop="table" label="来源表" width="240" /><el-table-column prop="description" label="数据范围" /></el-table>
        <h2>最近 50 次同步</h2>
        <el-table :data="jobs"><el-table-column label="开始时间" width="190"><template #default="{row}">{{ formatTime(row.started_at) }}</template></el-table-column><el-table-column prop="status" label="状态" width="110" /><el-table-column prop="row_count" label="快照行数" width="110" /><el-table-column prop="issue_count" label="质量问题" width="110" /><el-table-column prop="error_message" label="失败原因 / 重试提示" /><el-table-column prop="id" label="批次" width="300" /></el-table>
      </el-tab-pane>
      <el-tab-pane label="主数据" name="master">
        <h2>统一编码映射</h2><p>保存后重新同步生效，历史快照保持原映射。最多显示 1,000 条，变更留存审计历史。</p>
        <el-alert title="同步时优先使用手工映射。没有手工映射时，用库存规格编码、商品编码，以及明细上的 ERP SKU / 商品 ID 自动对齐。来源键：店铺=商户ID:店铺ID；平台SKU/商品=商户ID:店铺ID:平台类型:SKU或商品ID；ERP SKU/商品=erp:商户ID:SKU或商品ID；仓库/供应商=商户ID:实体ID。" :closable="false" />
        <el-form :model="mapping" label-position="top" class="mapping-form" @submit.prevent="saveMapping">
          <el-form-item label="实体类型"><el-select v-model="mapping.entityType"><el-option v-for="(label, key) in entityTypes" :key="key" :value="key" :label="label" /></el-select></el-form-item>
          <el-form-item label="来源键"><el-input v-model="mapping.sourceKey" maxlength="180" placeholder="例如 1:2:3:SKU001" /></el-form-item>
          <el-form-item label="统一编码"><el-input v-model="mapping.canonicalCode" maxlength="100" placeholder="例如 SKU-001" /></el-form-item>
          <el-form-item label="名称"><el-input v-model="mapping.displayName" maxlength="150" /></el-form-item>
          <el-button type="primary" native-type="submit" :disabled="!canManage" :loading="saving">保存映射</el-button>
        </el-form>
        <el-table :data="mappings"><el-table-column prop="entity_type" label="类型" width="100" /><el-table-column prop="source_key" label="来源键" /><el-table-column prop="canonical_code" label="统一编码" /><el-table-column prop="display_name" label="名称" /><el-table-column prop="version" label="版本" width="90" /><el-table-column label="操作" width="90"><template #default="{row}"><el-button link @click="editMapping(row)">编辑</el-button></template></el-table-column></el-table>
      </el-tab-pane>
      <el-tab-pane label="主题数据" name="themes">
        <h2>可追溯的主题快照</h2><p>查询的是选定批次的历史记录；金额字段以分存储。无效数据仍保留供检查，但不会进入指标。商品明细和退款按平台订单号关联订单。</p>
        <el-radio-group v-model="kind" @change="resetRows"><el-radio-button v-for="(label,key) in themes" :key="key" :value="key">{{ label }}</el-radio-button></el-radio-group>
        <el-table :data="rows" class="table"><el-table-column prop="source_id" label="源ID" width="90" /><el-table-column prop="source_key" label="业务键" /><el-table-column prop="shop_code" label="店铺" /><el-table-column prop="sku_code" label="SKU" /><el-table-column prop="amount_minor" label="金额(分)" /><el-table-column prop="quantity" label="数量" /><el-table-column label="可统计" width="90"><template #default="{row}">{{ row.eligible ? '是' : '否' }}</template></el-table-column><el-table-column label="追溯" width="90"><template #default="{row}"><el-button link @click="trace(row)">查看</el-button></template></el-table-column></el-table>
        <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="loadRows" />
      </el-tab-pane>
      <el-tab-pane label="指标中心" name="definitions">
        <h2>统一指标字典 · {{ catalog.metricVersion }}</h2><p>修改业务口径必须发布新版本；看板和 AI 不自行计算另一套口径。</p>
        <el-table :data="catalog.metrics || []"><el-table-column prop="name" label="指标" width="190" /><el-table-column prop="code" label="标识" width="160" /><el-table-column prop="unit" label="单位" width="100" /><el-table-column prop="definition" label="计算规则与边界" /></el-table>
      </el-tab-pane>
      <el-tab-pane label="质量与追溯" name="quality">
        <h2>质量问题清单</h2><p>ERROR 记录从指标隔离；WARN 保留统计并提示核查。修正 ERP 源数据或映射后，重新同步验证。</p>
        <el-radio-group v-model="kind" @change="resetRows"><el-radio-button v-for="(label,key) in themes" :key="key" :value="key">{{ label }}</el-radio-button></el-radio-group>
        <el-table :data="rows" class="table"><el-table-column prop="severity" label="级别" width="100" /><el-table-column prop="source_id" label="源ID" width="100" /><el-table-column prop="rule_code" label="规则" width="240" /><el-table-column prop="message" label="问题与处理建议" /><el-table-column label="追溯" width="100"><template #default="{row}"><el-button link @click="trace(row)">来源记录</el-button></template></el-table-column></el-table>
        <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="loadRows" />
      </el-tab-pane>
      <el-tab-pane label="数据服务与看板" name="services">
        <div class="section-heading"><div><h2>经营指标</h2><p>支付日期按北京时间，库存为同步时点快照。</p></div><div class="query"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" :clearable="false" /><el-input v-model="shop" placeholder="统一店铺编码，留空全域" style="width:220px" /><el-button type="primary" :disabled="!batch" @click="loadMetrics">查询</el-button></div></div>
        <el-empty v-if="!batch" description="还没有成功快照，请先在数据接入中同步" />
        <template v-else>
          <el-alert v-if="metrics.excludedRows || metrics.snapshot?.issue_count" :title="`本快照全域有 ${metrics.snapshot?.issue_count || 0} 个质量问题，${metrics.excludedRows || 0} 条记录被隔离，请结合质量清单解读指标。`" type="warning" :closable="false" />
          <div class="cards"><el-card v-for="card in cards" :key="card.code" shadow="never"><span>{{ card.name }}</span><strong>{{ metrics[card.code] == null ? '—' : metrics[card.code] }}</strong><small>{{ card.unit }}</small></el-card></div>
          <p>快照：{{ metrics.batchId }} · 更新时间：{{ formatTime(metrics.snapshot?.finished_at) }} · {{ metrics.from }} 至 {{ metrics.to }}</p>
          <el-button @click="copyQuestion">复制同口径 AI 提问</el-button>
          <h2>共享接口</h2><p>GET /api/erp-api/data-center/metrics，参数 from、to、shop、batch。需要登录和 data:center:read 全域权限。</p>
          <p>AI 工具 getDataCenterMetrics 和销售分析均调用该指标服务。固定 batch 可复现历史结果。</p>
          <el-collapse><el-collapse-item title="查看指标服务完整响应"><pre>{{ JSON.stringify(metrics, null, 2) }}</pre></el-collapse-item></el-collapse>
        </template>
      </el-tab-pane>
    </el-tabs>
    <el-drawer v-model="drawer" title="来源与血缘" size="60%"><pre>{{ JSON.stringify(lineage, null, 2) }}</pre></el-drawer>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dcGet, dcMapping, dcSync } from '@/api/data-center'
import { useUserStore } from '@/store/modules/user'

const user = useUserStore()
const canManage = computed(() => user.permissions.includes('*:*:*') || user.permissions.includes('data:center:manage'))
const tab = ref('sources'), batch = ref(''), kind = ref('order'), shop = ref(''), error = ref('')
const loading = ref(false), syncing = ref(false), saving = ref(false), drawer = ref(false)
const jobs = ref<any[]>([]), mappings = ref<any[]>([]), rows = ref<any[]>([])
const catalog = ref<any>({}), metrics = ref<any>({}), lineage = ref<any>({})
const page = ref(1), total = ref(0)
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const dates = ref<[string, string]>([today, today])
const mapping = ref({ entityType: 'sku', sourceKey: '', canonicalCode: '', displayName: '' })
const themes = { order: '订单', refund: '退款', item: '商品明细', inventory: '库存', purchase: '采购' }
const entityTypes = { sku: 'SKU', product: '商品', shop: '店铺', warehouse: '仓库', supplier: '供应商' }
const successfulJobs = computed(() => jobs.value.filter(j => j.status === 'SUCCESS'))
const cards = [{ code: 'paidAmount', name: '支付金额', unit: '元' }, { code: 'refundAmount', name: '该批订单成功退款', unit: '元' }, { code: 'netSales', name: '净销售额（支付批次）', unit: '元' }, { code: 'availableStock', name: '快照可用库存', unit: '件' }, { code: 'purchaseAmount', name: '采购订单金额', unit: '元' }]
const formatTime = (v: unknown) => v ? new Date(Number(v)).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—'
async function safe(action: () => Promise<void>) { try { error.value = ''; await action() } catch (e: any) { error.value = e.message || '请求失败' } }
async function reload() {
  loading.value = true
  await safe(async () => {
    const result = await Promise.all([dcGet('catalog'), dcGet('jobs'), dcGet('mappings')])
    catalog.value = result[0]; jobs.value = result[1]; mappings.value = result[2]
    if (!batch.value) batch.value = successfulJobs.value[0]?.id || ''
    await loadData()
  })
  loading.value = false
}
async function loadData() { metrics.value = {}; page.value = 1; await loadMetrics(); await loadRows() }
async function loadMetrics() {
  if (!batch.value || !dates.value?.[0] || !dates.value?.[1]) return
  await safe(async () => { metrics.value = await dcGet('metrics', { from: dates.value[0], to: dates.value[1], shop: shop.value, batch: batch.value }) })
}
async function loadRows() {
  rows.value = []; total.value = 0
  if (!batch.value || !['themes', 'quality'].includes(tab.value)) return
  await safe(async () => { const data = await dcGet('rows', { kind: kind.value, batch: batch.value, page: page.value, size: 20, issues: tab.value === 'quality' }); rows.value = data.rows; total.value = data.total })
}
async function resetRows() { page.value = 1; await loadRows() }
async function synchronize() {
  syncing.value = true
  await safe(async () => { const job = await dcSync(); if (job.status === 'SUCCESS') { batch.value = job.id; ElMessage.success('新快照已发布') } else { ElMessage.error(job.error_message) } await reload() })
  syncing.value = false
}
async function saveMapping() {
  if (Object.values(mapping.value).some(v => !v.trim())) { ElMessage.warning('请填写完整映射'); return }
  saving.value = true
  await safe(async () => { await dcMapping(mapping.value); mappings.value = await dcGet('mappings'); ElMessage.success('映射已保存，请重新同步生效') })
  saving.value = false
}
function editMapping(row: any) { mapping.value = { entityType: row.entity_type, sourceKey: row.source_key, canonicalCode: row.canonical_code, displayName: row.display_name } }
async function trace(row: any) { await safe(async () => { lineage.value = await dcGet('lineage', { kind: kind.value, id: row.source_id, batch: batch.value }); drawer.value = true }) }
async function copyQuestion() { await safe(async () => { await navigator.clipboard.writeText(`请调用数据中台指标工具，查询 ${metrics.value.from} 至 ${metrics.value.to}、店铺 ${metrics.value.shop || '全域'}、快照 ${metrics.value.batchId} 的净销售额，说明口径、更新时间与质量缺口。`); ElMessage.success('已复制，可粘贴到 AI 对话') }) }
onMounted(reload)
</script>

<style scoped>
.data-center { padding: 28px; color: #203247; background: #f5f7fa; min-height: 100vh; }
header, .section-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; flex-wrap:wrap; }
.eyebrow { color:#286b73; font-weight:600; letter-spacing:2px; }
h1 { font-size:26px; margin:12px 0; } h2 { font-size:18px; margin:24px 0 10px; }
p { color:#64748b; line-height:1.7; } .filters, .query { display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
.filters { margin:20px 0; } .el-alert { margin:12px 0; }
.el-tabs { background:white; padding:20px; border:1px solid #e5eaf0; border-radius:12px; }
.mapping-form { display:grid; grid-template-columns:130px 1fr 1fr 1fr auto; gap:14px; align-items:center; margin:20px 0; }
.cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:16px; margin:24px 0; }
.cards strong { display:block; font-size:28px; margin:14px 0; color:#12666b; } .cards small { color:#64748b; }
.table { margin:18px 0; } pre { white-space:pre-wrap; overflow-wrap:anywhere; font-size:13px; line-height:1.7; }
@media(max-width:900px) { .data-center { padding:12px; } .mapping-form { grid-template-columns:1fr; } }
</style>
