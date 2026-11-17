<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="68px">
      <el-form-item label="商品名称" prop="goodsName">
        <el-input v-model="queryParams.goodsName" placeholder="请输入商品名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="Sku编码" prop="skuCode">
        <el-input v-model="queryParams.skuCode" placeholder="请输入Sku编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button size="small" @click="resetQuery"><el-icon><Refresh /></el-icon>重置</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="商品名称" align="left" prop="goodsName" />
      <el-table-column label="Sku编码" align="center" prop="skuCode" />
      <el-table-column label="库存数量" align="center" prop="quantity" />
      <el-table-column label="操作" align="center" width="150">
        <template #default="scope">
          <el-button size="small" type="text" @click="handleLink(scope.row)">关联ERP</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="关联ERP商品" v-model="linkOpen" width="720px" append-to-body>
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="SKU编码/名称">
          <el-input v-model="skuKeyword" placeholder="输入编码或名称" clearable style="width:240px" @keyup.enter="searchErpSku" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="skuLoading" @click="searchErpSku">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="skuLoading" :data="skuOptions" border size="small" highlight-current-row @current-change="(row:any)=>pickedSku=row">
        <el-table-column label="SkuId" prop="skuId" width="90" />
        <el-table-column label="SKU编码" prop="skuCode" width="140" />
        <el-table-column label="商品名称" prop="goodsName" min-width="160" />
        <el-table-column label="规格" prop="skuName" min-width="140" />
      </el-table>
      <template #footer>
        <el-button type="primary" :loading="linkSubmitting" @click="submitLink">确 定</el-button>
        <el-button @click="linkOpen=false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listWarehouseGoodsItems, linkErpGoodsSku } from '@/api/wms/goods'
import { searchSku } from '@/api/goods/goods'
import Pagination from '@/components/Pagination/index.vue'

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])
const queryParams = reactive({ pageNum: 1, pageSize: 10, goodsName: null, skuCode: null })
const linkOpen = ref(false)
const skuLoading = ref(false)
const linkSubmitting = ref(false)
const skuKeyword = ref('')
const skuOptions = ref<any[]>([])
const pickedSku = ref<any>(null)
const linkRow = ref<any>(null)

function getList() {
  loading.value = true
  listWarehouseGoodsItems(queryParams).then((res: any) => {
    dataList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { handleQuery() }
function handleLink(row: any) {
  linkRow.value = row
  skuKeyword.value = row.skuCode || row.goodsName || ''
  skuOptions.value = []
  pickedSku.value = null
  linkOpen.value = true
  if (skuKeyword.value) searchErpSku()
}
function searchErpSku() {
  const keyword = skuKeyword.value.trim()
  if (!keyword) { ElMessage.warning('请输入SKU编码或名称'); return }
  skuLoading.value = true
  searchSku({ keyword }).then((res: any) => {
    skuOptions.value = res.rows || []
    pickedSku.value = skuOptions.value[0] || null
    skuLoading.value = false
    if (skuOptions.value.length === 0) ElMessage.warning('未查询到ERP商品SKU')
  }).catch(() => { skuLoading.value = false })
}
function submitLink() {
  if (!linkRow.value || !pickedSku.value) { ElMessage.warning('请选择要关联的ERP商品SKU'); return }
  const erpGoodsSkuId = pickedSku.value.id || pickedSku.value.skuId
  linkSubmitting.value = true
  linkErpGoodsSku({ id: linkRow.value.id, erpGoodsSkuId }).then((res: any) => {
    linkSubmitting.value = false
    if (res.code === 200) {
      ElMessage.success('关联成功')
      linkOpen.value = false
      getList()
    } else {
      ElMessage.error(res.msg || '关联失败')
    }
  }).catch(() => { linkSubmitting.value = false })
}
onMounted(() => { getList() })
</script>
