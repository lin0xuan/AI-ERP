<template>
  <div class="outbound-work">
    <el-card class="work-card" shadow="never">
      <template #header>
        <span>出库作业 - {{ outboundOrder.outNum || outboundOrder.id }}</span>
        <el-button size="small" style="float:right" @click="goBack">
          <el-icon><Back /></el-icon>返回
        </el-button>
      </template>

      <el-form :model="outboundOrder" label-width="100px" size="small" disabled inline>
        <el-form-item label="源单号">{{ outboundOrder.sourceNo }}</el-form-item>
        <el-form-item label="仓库">{{ outboundOrder.warehouseName }}</el-form-item>
        <el-form-item label="出库类型">
          <el-tag v-if="outboundOrder.type === 1" size="small">订单发货出库</el-tag>
          <el-tag v-else-if="outboundOrder.type === 2" size="small">采购退货出库</el-tag>
          <el-tag v-else-if="outboundOrder.type === 3" size="small">盘亏出库</el-tag>
          <el-tag v-else-if="outboundOrder.type === 4" size="small">报损出库</el-tag>
        </el-form-item>
        <el-form-item label="创建时间">{{ parseTime(outboundOrder.createTime) }}</el-form-item>
      </el-form>

      <el-table :data="outboundItems" border stripe style="margin-top:20px">
        <el-table-column prop="skuCode" label="SKU编码" width="120" />
        <el-table-column prop="skuName" label="商品名称" min-width="150" />
        <el-table-column prop="planQuantity" label="应出库数量" width="100" align="center" />
        <el-table-column prop="outboundQuantity" label="已出库数量" width="100" align="center" />
        <el-table-column label="本次出库" width="300" align="center">
          <template #default="scope">
            <div v-if="scope.row.inventoryMode === 1">
              <div v-if="scope.row.lockedBarcodes?.length">
                <el-tag v-for="bc in scope.row.lockedBarcodes" :key="bc.barcode" size="small" style="margin-right:5px">
                  {{ bc.barcode }}
                </el-tag>
                <div class="text-muted">（已锁定，无需扫码）</div>
              </div>
              <div v-else>
                <span class="text-muted">无锁定条码</span>
              </div>
            </div>
            <div v-else>
              <el-select v-model="scope.row.batchStrategy" size="small" placeholder="批次策略" style="width:120px">
                <el-option label="自动分配批次" value="auto" />
                <el-option label="手动选择批次" value="manual" />
              </el-select>
              <el-input-number
                v-model="scope.row.thisQuantity"
                :min="0"
                :max="scope.row.remainingQuantity"
                size="small"
                controls-position="right"
                style="margin-left:8px;width:110px"
                :disabled="scope.row.remainingQuantity === 0"
              />
              <el-button v-if="scope.row.batchStrategy === 'manual'" type="text" size="small"
                @click="openBatchSelection(scope.row, scope.$index)" style="margin-left:5px">
                选批次
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="remainingQuantity" label="剩余可出库" width="100" align="center">
          <template #default="scope">
            <span :class="{ 'text-danger': scope.row.remainingQuantity === 0 }">{{ scope.row.remainingQuantity }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="submit-bar" style="margin-top:20px;text-align:center">
        <el-button type="primary" size="large" @click="submitOutbound" :loading="submitting" :disabled="!canSubmit">
          确认出库
        </el-button>
      </div>

      <el-dialog title="选择批次" v-model="batchOpen" width="760px" append-to-body>
        <el-table v-loading="batchLoading" :data="batchList" border size="small">
          <el-table-column label="批次号" prop="batchNum" min-width="140" />
          <el-table-column label="剩余数量" prop="currentQty" width="100" align="center" />
          <el-table-column label="本次出库" width="160" align="center">
            <template #default="scope">
              <el-input-number v-model="scope.row.outQty" :min="0" :max="Number(scope.row.currentQty) || 0" size="small" controls-position="right" />
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button type="primary" :loading="batchSubmitting" @click="confirmBatch">确认出库</el-button>
          <el-button @click="batchOpen = false">取 消</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back } from '@element-plus/icons-vue'
import { getStockOutEntry, stockOut } from '@/api/wms/stockOut'
import { searchSkuInventoryBatch } from '@/api/vendor/stockup'
import { parseTime } from '@/utils/zhijian'

const route = useRoute()
const router = useRouter()

const outboundOrder = reactive<Record<string, any>>({})
const outboundItems = ref<any[]>([])
const submitting = ref(false)
const batchOpen = ref(false)
const batchLoading = ref(false)
const batchSubmitting = ref(false)
const batchList = ref<any[]>([])
const batchRow = ref<any>(null)

const canSubmit = computed(() => {
  return outboundItems.value.some((item: any) => (item.thisQuantity || 0) > 0 || item.scannedCount > 0)
})

function loadData(id: number) {
  getStockOutEntry(id).then((res: any) => {
    const data = res.data || {}
    Object.assign(outboundOrder, data)
    outboundItems.value = (data.itemList || data.items || []).map((item: any) => ({
      ...item,
      thisQuantity: 0,
      scannedCount: 0,
      remainingQuantity: (item.planQuantity || 0) - (item.outboundQuantity || 0),
      batchStrategy: 'auto',
      lockedBarcodes: item.lockedBarcodes || [],
    }))
  })
}

function openBatchSelection(row: any, index: number) {
  const goodsId = row.goodsId || row.skuId
  if (!goodsId) { ElMessage.warning('当前明细缺少商品，无法查询批次'); return }
  batchRow.value = row
  batchList.value = []
  batchOpen.value = true
  batchLoading.value = true
  searchSkuInventoryBatch({ goodsId }).then((res: any) => {
    const rows = res.rows || res.data || []
    batchList.value = rows.map((item: any) => ({
      ...item,
      batchNum: item.batchNum || item.batchNo || item.id,
      currentQty: item.currentQty ?? item.remainingQuantity ?? 0,
      outQty: 0
    }))
    batchLoading.value = false
    if (batchList.value.length === 0) ElMessage.warning('没有可用批次')
  }).catch(() => { batchLoading.value = false })
}

function confirmBatch() {
  const row = batchRow.value
  const selected = batchList.value.filter((item: any) => Number(item.outQty) > 0)
  if (!row || selected.length === 0) { ElMessage.warning('请填写要出库的批次数量'); return }
  const totalQty = selected.reduce((sum: number, item: any) => sum + Number(item.outQty), 0)
  if (totalQty > row.remainingQuantity) { ElMessage.warning('出库数量不能超过剩余可出库数量'); return }
  batchSubmitting.value = true
  Promise.all(selected.map((item: any) => stockOut({
    entryItemId: row.id,
    entryId: outboundOrder.id,
    skuId: row.skuId,
    batchId: item.id || item.batchId,
    outQty: item.outQty
  }))).then((results: any[]) => {
    const failed = results.find((res: any) => res && res.code && res.code !== 200)
    batchSubmitting.value = false
    if (failed) { ElMessage.error(failed.msg || '出库失败'); return }
    ElMessage.success('批次出库成功')
    batchOpen.value = false
    if (outboundOrder.id) loadData(outboundOrder.id)
  }).catch(() => { batchSubmitting.value = false })
}

function submitOutbound() {
  const items = outboundItems.value
    .filter((item: any) => (item.thisQuantity || 0) > 0)
    .map((item: any) => ({ itemId: item.id, quantity: item.thisQuantity }))

  if (items.length === 0) {
    ElMessage.warning('请填写出库数量')
    return
  }

  submitting.value = true
  stockOut({ id: outboundOrder.id, items }).then(() => {
    ElMessage.success('出库成功')
    submitting.value = false
    router.push('/wms/stock_out_list')
  }).catch(() => {
    submitting.value = false
  })
}

function goBack() {
  router.back()
}

onMounted(() => {
  const id = route.query.id || route.params.id
  if (id) loadData(Number(id))
})
</script>

<style scoped>
.text-danger { color: #f56c6c; font-weight: bold; }
.text-muted { color: #909399; font-size: 12px; }
</style>