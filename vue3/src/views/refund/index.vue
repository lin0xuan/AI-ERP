<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="108px">
      <el-form-item label="售后单号" prop="refundNum">
        <el-input v-model="queryParams.refundNum" placeholder="请输入售后单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNum">
        <el-input v-model="queryParams.orderNum" placeholder="请输入订单号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable @change="handleQuery">
          <el-option label="待处理" value="0" />
          <el-option label="处理中" value="1" />
          <el-option label="已完成" value="10" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button size="small" @click="resetQuery"><el-icon><Refresh /></el-icon>重置</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="售后单号" align="left" prop="refundNum" width="200" />
      <el-table-column label="订单号" align="left" prop="orderNum" width="200" />
      <el-table-column label="售后类型" align="center" prop="type" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.type === 10">退货</el-tag>
          <el-tag v-else-if="scope.row.type === 20" type="warning">换货</el-tag>
          <el-tag v-else-if="scope.row.type === 80" type="info">补发</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品" align="left" prop="title" min-width="200" />
      <el-table-column label="数量" align="center" prop="quantity" width="60" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 0" type="danger">待处理</el-tag>
          <el-tag v-else-if="scope.row.status === 1" type="warning">处理中</el-tag>
          <el-tag v-else type="success">已完成</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button v-if="scope.row.status === 0" size="small" type="text" @click="handleProcess(scope.row)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="订单号" prop="orderNum">
          <el-input v-model="form.orderNum" disabled />
        </el-form-item>
        <el-form-item label="处理方式" prop="type">
          <el-select v-model="form.type" placeholder="售后处理方式" style="width:100%">
            <el-option label="无需处理" :value="0" />
            <el-option label="退货" :value="10" />
            <el-option label="换货" :value="20" />
            <el-option label="维修" :value="30" />
            <el-option label="补发" :value="80" />
            <el-option label="订单拦截" :value="99" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否已发货" prop="hasGoodsSend">
          <el-select v-model="form.hasGoodsSend" placeholder="是否已发货" style="width:100%">
            <el-option label="未发货" :value="0" />
            <el-option label="已发货" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="发货单号" prop="sendLogisticsCode">
          <el-input v-model="form.sendLogisticsCode" placeholder="发货单号" />
        </el-form-item>
        <el-form-item label="退回物流单号" prop="returnLogisticsCode">
          <el-input v-model="form.returnLogisticsCode" placeholder="退回物流单号" />
        </el-form-item>
        <template v-if="form.type === 80">
          <el-form-item label="收件人" prop="receiverName">
            <el-input v-model="form.receiverName" placeholder="收件人" />
          </el-form-item>
          <el-form-item label="收件手机号" prop="receiverTel">
            <el-input v-model="form.receiverTel" placeholder="收件手机号" />
          </el-form-item>
          <el-form-item label="收件地址" prop="receiverAddress">
            <el-input v-model="form.receiverAddress" placeholder="收件地址" />
          </el-form-item>
        </template>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitProcess">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listReturned, refundProcessing } from '@/api/refund/refund'
import Pagination from '@/components/Pagination/index.vue'

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])
const queryParams = reactive({ pageNum: 1, pageSize: 10, refundNum: null, orderNum: null, status: null })
const open = ref(false)
const title = ref('处理售后')
const formRef = ref<FormInstance>()
const form = reactive<Record<string, any>>({})
const rules = { type: [{ required: true, message: '请选择处理方式', trigger: 'change' }] }

function getList() {
  loading.value = true
  listReturned(queryParams).then((res: any) => {
    dataList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { handleQuery() }
function handleProcess(row: any) {
  Object.assign(form, {
    refundId: row.id,
    orderNum: row.orderNum,
    type: row.type ?? null,
    hasGoodsSend: row.hasGoodReturn === 1 ? 1 : 0,
    sendLogisticsCode: row.sendLogisticsCode || '',
    returnLogisticsCode: row.returnLogisticsCode || '',
    receiverName: '',
    receiverTel: '',
    receiverAddress: '',
    remark: ''
  })
  if (row.type === 10 || row.refundType === 10) { title.value = '退货'; form.type = 10 }
  else if (row.type === 20 || row.refundType === 20) { title.value = '换货'; form.type = 20 }
  else if (row.type === 80 || row.refundType === 80) { title.value = '补发'; form.type = 80 }
  else title.value = '处理售后'
  open.value = true
}
function submitProcess() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    refundProcessing({ ...form }).then((res: any) => {
      if (res.code === 200) {
        ElMessage.success('处理成功')
        open.value = false
        getList()
      } else {
        ElMessage.error(res.msg || '处理失败')
      }
    })
  })
}
onMounted(() => { getList() })
</script>
