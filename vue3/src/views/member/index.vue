<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="120px">
      <el-form-item label="姓名" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入姓名" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button size="small" @click="resetQuery"><el-icon><Refresh /></el-icon>重置</el-button>
      </el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain size="small" @click="handleAdd"><el-icon><Plus /></el-icon>新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>
    <el-table v-loading="loading" :data="dataList">
      <el-table-column label="ID" align="center" prop="id" width="80" />
      <el-table-column label="姓名" align="center" prop="name" />
      <el-table-column label="手机号" align="center" prop="phone" />
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button size="small" type="text" @click="handleUpdate(scope.row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="平台账号" prop="platformAccount">
          <el-input v-model="form.platformAccount" placeholder="请输入平台账号" />
        </el-form-item>
        <el-form-item label="店铺" prop="shopId">
          <el-select v-model="form.shopId" filterable clearable placeholder="请选择店铺" style="width:100%">
            <el-option v-for="item in shopList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商户" prop="merchantId">
          <el-select v-model="form.merchantId" filterable clearable placeholder="请选择商户" style="width:100%">
            <el-option v-for="item in merchantList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="省" prop="province">
          <el-input v-model="form.province" placeholder="省" />
        </el-form-item>
        <el-form-item label="市" prop="city">
          <el-input v-model="form.city" placeholder="市" />
        </el-form-item>
        <el-form-item label="区县" prop="county">
          <el-input v-model="form.county" placeholder="区县" />
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入详细地址" />
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
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { listShopMember, addShopMember, updateShopMember } from '@/api/marketing/member'
import { listShop } from '@/api/shop/shop'
import { listMerchant } from '@/api/shop/merchant'
import Pagination from '@/components/Pagination/index.vue'
import RightToolbar from '@/components/RightToolbar/index.vue'

const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const dataList = ref<any[]>([])
const shopList = ref<any[]>([])
const merchantList = ref<any[]>([])
const open = ref(false)
const title = ref('')
const formRef = ref<FormInstance>()

const queryParams = reactive({ pageNum: 1, pageSize: 10, name: null, phone: null })
const emptyForm = () => ({
  id: null as number | null,
  name: '',
  phone: '',
  platformAccount: '',
  address: '',
  province: '',
  city: '',
  county: '',
  remark: '',
  shopId: null as number | null,
  merchantId: null as number | null
})
const form = reactive(emptyForm())
const rules = {
  name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  phone: [{ required: true, message: '手机号不能为空', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listShopMember(queryParams).then((res: any) => {
    dataList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { queryParams.name = null; queryParams.phone = null; handleQuery() }
function resetForm() {
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}
function handleAdd() {
  resetForm()
  title.value = '新增会员'
  open.value = true
}
function handleUpdate(row: any) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    name: row.name,
    phone: row.phone,
    platformAccount: row.platformAccount,
    address: row.address,
    province: row.province,
    city: row.city,
    county: row.county,
    remark: row.remark,
    shopId: row.shopId,
    merchantId: row.merchantId
  })
  title.value = '编辑会员'
  open.value = true
}
function submitForm() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    const payload = {
      id: form.id,
      name: form.name,
      phone: form.phone,
      platformAccount: form.platformAccount,
      address: form.address,
      province: form.province,
      city: form.city,
      county: form.county,
      remark: form.remark,
      shopId: form.shopId,
      merchantId: form.merchantId
    }
    const req = form.id ? updateShopMember(payload) : addShopMember(payload)
    req.then((res: any) => {
      if (res.code === 200) {
        ElMessage.success(form.id ? '编辑成功' : '新增成功')
        open.value = false
        getList()
      } else {
        ElMessage.error(res.msg || '保存失败')
      }
    })
  })
}
onMounted(() => {
  listShop({ pageSize: 200 }).then((res: any) => { shopList.value = res.rows || [] })
  listMerchant({ pageSize: 200 }).then((res: any) => { merchantList.value = res.rows || [] })
  getList()
})
</script>
