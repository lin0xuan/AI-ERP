<template>
  <div class="app-container">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 720px">
      <el-form-item label="店铺" prop="shopId">
        <el-select v-model="form.shopId" filterable placeholder="请选择店铺" style="width: 100%">
          <el-option v-for="shop in shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="收件人" prop="receiverName">
        <el-input v-model="form.receiverName" />
      </el-form-item>
      <el-form-item label="手机" prop="receiverPhone">
        <el-input v-model="form.receiverPhone" />
      </el-form-item>
      <el-form-item label="地址" prop="address">
        <el-input v-model="form.address" />
      </el-form-item>
      <el-form-item label="ERP SKU" prop="erpGoodsSkuId">
        <el-input v-model="form.erpGoodsSkuId" placeholder="商品库 SKU 主键" />
      </el-form-item>
      <el-form-item label="数量" prop="quantity">
        <el-input-number v-model="form.quantity" :min="1" />
      </el-form-item>
      <el-form-item label="金额" prop="itemAmount">
        <el-input-number v-model="form.itemAmount" :min="0" :precision="2" />
      </el-form-item>
      <el-form-item label="买家留言">
        <el-input v-model="form.buyerMemo" type="textarea" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">保存订单</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { listShop } from '@/api/shop/shop'
import { createShopOrder } from '@/api/shop/order'

const formRef = ref<FormInstance>()
const saving = ref(false)
const shops = ref<any[]>([])
const form = reactive({
  shopId: null as number | null,
  receiverName: '',
  receiverPhone: '',
  address: '',
  buyerMemo: '',
  erpGoodsSkuId: '',
  quantity: 1,
  itemAmount: 0,
})
const rules = {
  shopId: [{ required: true, message: '请选择店铺' }],
  receiverName: [{ required: true, message: '请填写收件人' }],
  receiverPhone: [{ required: true, message: '请填写手机' }],
  erpGoodsSkuId: [{ required: true, message: '请填写 SKU' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res: any = await createShopOrder({
      shopId: form.shopId,
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      address: form.address,
      buyerMemo: form.buyerMemo,
      goodsAmount: form.itemAmount,
      itemList: [{
        erpGoodsSkuId: Number(form.erpGoodsSkuId),
        quantity: form.quantity,
        itemAmount: form.itemAmount,
      }],
    })
    if (res.code === 200) ElMessage.success('订单已创建')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  listShop({}).then((res: any) => { shops.value = res.rows || [] })
})
</script>
