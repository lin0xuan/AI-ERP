<template>
  <div class="app-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>AI 智能分析</span>
          <el-button @click="router.push('/ai/config')">模型配置</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="智能分析" name="analysis">
          <el-radio-group v-model="analysisType" style="margin-bottom: 16px" @change="result = ''">
            <el-radio-button value="sales">销售分析</el-radio-button>
            <el-radio-button value="inventory">库存优化</el-radio-button>
            <el-radio-button value="customer">客户洞察</el-radio-button>
            <el-radio-button value="operation">运营效率</el-radio-button>
            <el-radio-button value="custom">自定义分析</el-radio-button>
          </el-radio-group>

          <el-alert
            v-if="analysisType === 'sales'"
            title="按订单表统计最近 7 天的金额和订单数。未配置模型时只显示汇总，不编造商品或渠道结论。"
            type="info"
            show-icon
            :closable="false"
          />

          <el-form v-else label-width="100px" style="max-width: 760px">
            <template v-if="analysisType === 'inventory'">
              <el-form-item label="库存数据">
                <el-input v-model="form.inventoryData" type="textarea" :rows="6" placeholder="库存 JSON 或表格文本" />
              </el-form-item>
              <el-form-item label="销售数据">
                <el-input v-model="form.salesData" type="textarea" :rows="6" placeholder="对应的销售数据" />
              </el-form-item>
            </template>
            <template v-else-if="analysisType === 'customer'">
              <el-form-item label="客户数据">
                <el-input v-model="form.customerData" type="textarea" :rows="6" />
              </el-form-item>
              <el-form-item label="订单数据">
                <el-input v-model="form.orderData" type="textarea" :rows="6" />
              </el-form-item>
            </template>
            <el-form-item v-else-if="analysisType === 'operation'" label="运营数据">
              <el-input v-model="form.operationData" type="textarea" :rows="8" />
            </el-form-item>
            <el-form-item v-else label="提示词">
              <el-input v-model="form.prompt" type="textarea" :rows="8" placeholder="说明要分析的问题和已有事实" />
            </el-form-item>
          </el-form>

          <el-button type="primary" :loading="loading" style="margin-top: 16px" @click="analyze">开始分析</el-button>

          <el-card v-if="result" shadow="never" style="margin-top: 16px">
            <template #header>分析结果</template>
            <div class="result-content" v-html="result" />
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="历史记录" name="history">
          <el-table v-loading="historyLoading" :data="historyList">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="analysisType" label="类型" width="120" />
            <el-table-column prop="analysisContent" label="输入" show-overflow-tooltip />
            <el-table-column prop="createdTime" label="时间" width="180" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button text type="primary" @click="openDetail(row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="total > 0"
            style="margin-top: 16px; justify-content: flex-end"
            background
            layout="prev, pager, next"
            :total="total"
            :page-size="pageSize"
            :current-page="currentPage"
            @current-change="onPage"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="detailOpen" title="分析报告" width="720px">
      <div class="result-content" v-html="detailHtml" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import {
  analyzeSales,
  optimizeInventory,
  analyzeCustomerInsights,
  analyzeOperationEfficiency,
  customAnalysis,
  getHistoryList,
  getHistoryDetail,
} from '@/api/ai/analysis'

const router = useRouter()
const analysisType = ref('sales')
const activeTab = ref('analysis')
const loading = ref(false)
const result = ref('')
const form = reactive({
  inventoryData: '',
  salesData: '',
  customerData: '',
  orderData: '',
  operationData: '',
  prompt: '',
})

const historyList = ref<any[]>([])
const historyLoading = ref(false)
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)
const detailOpen = ref(false)
const detailHtml = ref('')

function render(text: string) {
  return marked.parse(text || '') as string
}

async function analyze() {
  loading.value = true
  result.value = ''
  try {
    let response: any
    if (analysisType.value === 'sales') response = await analyzeSales()
    else if (analysisType.value === 'inventory') response = await optimizeInventory(form)
    else if (analysisType.value === 'customer') response = await analyzeCustomerInsights(form)
    else if (analysisType.value === 'operation') response = await analyzeOperationEfficiency(form)
    else response = await customAnalysis(form)
    result.value = render(response.data || '')
    loadHistory()
  } catch {
    // 拦截器已经提示错误
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const response: any = await getHistoryList({ pageNum: currentPage.value, pageSize })
    historyList.value = response.rows || []
    total.value = response.total || 0
  } finally {
    historyLoading.value = false
  }
}

async function openDetail(id: number) {
  const response: any = await getHistoryDetail(id)
  detailHtml.value = render(response.data?.analysisResult || '')
  detailOpen.value = true
}

function onPage(page: number) {
  currentPage.value = page
  loadHistory()
}

onMounted(loadHistory)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.result-content {
  line-height: 1.6;
  white-space: normal;
}
</style>
