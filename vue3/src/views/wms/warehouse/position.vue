<template>
  <div class="app-container">
    <el-form :inline="true" size="small" label-width="68px">
      <el-form-item label="仓库">
        <el-select v-model="queryParams.warehouseId" filterable placeholder="请选择仓库" style="width:220px" @change="handleQuery">
          <el-option v-for="item in warehouseList" :key="item.id" :label="item.warehouseName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery">搜索</el-button>
      </el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain size="small" @click="handleAdd()"><el-icon><Plus /></el-icon>新增货架</el-button>
      </el-col>
    </el-row>
    <el-table v-loading="loading" :data="dataList" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
      <el-table-column label="ID" align="center" prop="id" width="80" />
      <el-table-column label="仓位编号" align="center" prop="number" />
      <el-table-column label="仓位名称" align="center" prop="name" />
      <el-table-column label="地址" align="center" prop="address" show-overflow-tooltip />
      <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button size="small" type="text" @click="handleAdd(scope.row)">新增下级</el-button>
          <el-button size="small" type="text" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button size="small" type="text" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" filterable placeholder="请选择仓库" style="width:100%" :disabled="!!form.id">
            <el-option v-for="item in warehouseList" :key="item.id" :label="item.warehouseName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上级仓位" prop="parentId">
          <el-input-number v-model="form.parentId" :min="0" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="仓位编号" prop="number">
          <el-input v-model="form.number" placeholder="请输入仓位编号" />
        </el-form-item>
        <el-form-item label="仓位名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入仓位名称" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
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
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listPosition, getLocation, addLocation, updateLocation, delLocation } from '@/api/wms/position'
import { listWarehouse } from '@/api/wms/warehouse'
import Pagination from '@/components/Pagination/index.vue'

const route = useRoute()
const loading = ref(false)
const total = ref(0)
const dataList = ref<any[]>([])
const warehouseList = ref<any[]>([])
const open = ref(false)
const title = ref('')
const formRef = ref<FormInstance>()
const queryParams = reactive({ pageNum: 1, pageSize: 10, warehouseId: null as number | null })
const form = reactive<Record<string, any>>({
  id: null, number: '', name: '', warehouseId: null, parentId: 0, remark: '', address: ''
})
const rules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  number: [{ required: true, message: '仓位编号不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '仓位名称不能为空', trigger: 'blur' }]
}

function buildTree(list: any[], parentId: number): any[] {
  return list.filter((item) => item.parentId === parentId).map((item) => ({
    ...item,
    children: buildTree(list, item.id)
  }))
}

function getList() {
  if (!queryParams.warehouseId) {
    ElMessage.warning('请先选择仓库')
    dataList.value = []
    total.value = 0
    return
  }
  loading.value = true
  listPosition({ warehouseId: queryParams.warehouseId, pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }).then((res: any) => {
    const rows = res.rows || []
    dataList.value = buildTree(rows, 0)
    total.value = res.total || rows.length
    loading.value = false
  }).catch(() => { loading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetForm(parentId = 0) {
  Object.assign(form, {
    id: null, number: '', name: '', warehouseId: queryParams.warehouseId, parentId, remark: '', address: ''
  })
  formRef.value?.clearValidate()
}
function handleAdd(row?: any) {
  if (!queryParams.warehouseId) { ElMessage.warning('请先选择仓库'); return }
  resetForm(row?.id || 0)
  title.value = row ? '新增下级仓位' : '新增仓位'
  open.value = true
}
function handleUpdate(row: any) {
  getLocation(row.id).then((res: any) => {
    const data = res.data || row
    Object.assign(form, {
      id: data.id,
      number: data.number,
      name: data.name,
      warehouseId: data.warehouseId || queryParams.warehouseId,
      parentId: data.parentId ?? 0,
      remark: data.remark || '',
      address: data.address || ''
    })
    title.value = '修改仓位'
    open.value = true
  })
}
function submitForm() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    const payload = {
      id: form.id,
      number: form.number,
      name: form.name,
      warehouseId: form.warehouseId,
      parentId: form.parentId,
      remark: form.remark,
      address: form.address
    }
    const req = form.id ? updateLocation(payload) : addLocation(payload)
    req.then((res: any) => {
      if (res.code === 200) {
        ElMessage.success(form.id ? '修改成功' : '新增成功')
        open.value = false
        getList()
      } else {
        ElMessage.error(res.msg || '保存失败')
      }
    })
  })
}
function handleDelete(row: any) {
  ElMessageBox.confirm('确认删除？').then(() => delLocation(row.id)).then(() => { ElMessage.success('删除成功'); getList() }).catch(() => {})
}
onMounted(() => {
  const qid = route.query.warehouseId
  listWarehouse({ pageSize: 200 }).then((res: any) => {
    warehouseList.value = res.rows || []
    if (qid) queryParams.warehouseId = Number(qid)
    else if (warehouseList.value.length > 0) queryParams.warehouseId = warehouseList.value[0].id
    if (queryParams.warehouseId) getList()
  })
})
</script>
