<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="88px">
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable @change="handleQuery">
          <el-option label="生效中" value="1" />
          <el-option label="待审核" value="0" />
          <el-option label="审核拒绝" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button size="small" @click="resetQuery"><el-icon><Refresh /></el-icon>重置</el-button>
      </el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain size="small" @click="handleAdd"><el-icon><Plus /></el-icon>添加折扣</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="折扣ID" align="center" prop="id" width="80" />
      <el-table-column label="折扣名称" align="center" prop="ruleName" />
      <el-table-column label="类型" align="center" prop="discountType" width="140">
        <template #default="scope">
          <span v-if="scope.row.discountType === 1">百分比折扣</span>
          <span v-else-if="scope.row.discountType === 2">固定金额折扣</span>
          <span v-else>{{ scope.row.discountType }}</span>
        </template>
      </el-table-column>
      <el-table-column label="折扣值" align="center" prop="discountValue" width="100" />
      <el-table-column label="最小订单金额" align="center" prop="minOrderAmount" width="120" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.status === 1" type="success" size="small">生效中</el-tag>
          <el-tag v-else-if="scope.row.status === 0" type="warning" size="small">待审核</el-tag>
          <el-tag v-else type="danger" size="small">拒绝</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button size="small" type="text" @click="handleAudit(scope.row)" v-if="scope.row.status === 0">审核</el-button>
          <el-button size="small" type="text" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="折扣名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="折扣名称" />
        </el-form-item>
        <el-form-item label="折扣类型" prop="discountType">
          <el-select v-model="form.discountType" placeholder="折扣类型" style="width:100%">
            <el-option label="百分比折扣" :value="1" />
            <el-option label="固定金额折扣" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="折扣值" prop="discountValue">
          <el-input-number v-model="form.discountValue" :min="0" :precision="2" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="最小订单金额" prop="minOrderAmount">
          <el-input-number v-model="form.minOrderAmount" :min="0" :precision="2" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="open = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { listDiscount, addDiscount, delDiscount, auditDiscount } from '@/api/marketing/discount'
import Pagination from '@/components/Pagination/index.vue'
import RightToolbar from '@/components/RightToolbar/index.vue'

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])
const open = ref(false)
const title = ref('')
const formRef = ref<FormInstance>()
const form = reactive<Record<string, any>>({
  ruleName: '',
  discountType: 1,
  discountValue: null,
  minOrderAmount: 0,
  remark: ''
})
const rules = {
  ruleName: [{ required: true, message: '折扣名称不能为空', trigger: 'blur' }],
  discountType: [{ required: true, message: '请选择折扣类型', trigger: 'change' }],
  discountValue: [{ required: true, message: '折扣值不能为空', trigger: 'blur' }]
}

const queryParams = reactive({ pageNum: 1, pageSize: 10, status: null })

function getList() {
  loading.value = true
  listDiscount(queryParams).then((res: any) => {
    dataList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { handleQuery() }
function handleAdd() {
  Object.assign(form, { ruleName: '', discountType: 1, discountValue: null, minOrderAmount: 0, remark: '' })
  formRef.value?.clearValidate()
  title.value = '新增折扣'
  open.value = true
}
function submitForm() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    addDiscount({
      ruleName: form.ruleName,
      discountType: form.discountType,
      discountValue: form.discountValue,
      minOrderAmount: form.minOrderAmount,
      remark: form.remark
    }).then((res: any) => {
      if (res.code === 200) {
        ElMessage.success('添加成功')
        open.value = false
        getList()
      } else {
        ElMessage.error(res.msg || '添加失败')
      }
    })
  })
}
function handleAudit(row: any) {
  ElMessageBox.confirm('确认审核通过？').then(() => {
    auditDiscount(row.id).then(() => { ElMessage.success('审核成功'); getList() })
  }).catch(() => {})
}
function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除？').then(() => {
    delDiscount(row.id).then(() => { ElMessage.success('删除成功'); getList() })
  }).catch(() => {})
}
onMounted(() => { getList() })
</script>
