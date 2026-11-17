<template>
  <div class="app-container">
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="ID" prop="id" width="80" />
      <el-table-column label="供应商商品" prop="supplierProductItemId" width="140" />
      <el-table-column label="报价" prop="price" />
      <el-table-column label="状态" prop="status" width="100" />
      <el-table-column label="备注" prop="remark" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button text type="primary" @click="approve(row)">通过</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const rows = ref<any[]>([])

function load() {
  loading.value = true
  request({ url: '/api/erp-api/supplier/price/list', method: 'get', params: { pageNum: 1, pageSize: 20 } })
    .then((res: any) => { rows.value = res.rows || [] })
    .finally(() => { loading.value = false })
}

function approve(row: any) {
  request({ url: '/api/erp-api/supplier/price/edit', method: 'put', data: { ...row, status: 1 } })
    .then(() => { ElMessage.success('已通过'); load() })
}

onMounted(load)
</script>
