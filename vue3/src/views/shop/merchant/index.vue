<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="86px">
      <el-form-item label="商户名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入商户名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="商户编码" prop="number">
        <el-input v-model="queryParams.number" placeholder="请输入商户编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="联系人" prop="linkman">
        <el-input v-model="queryParams.linkman" placeholder="请输入联系人" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button size="small" @click="resetQuery"><el-icon><Refresh /></el-icon>重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain size="small" @click="handleAdd"><el-icon><Plus /></el-icon>新增商户</el-button>
      </el-col>
      <RightToolbar :showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="merchantList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="id" width="60" />
      <el-table-column label="商户名称" align="left" prop="name" min-width="150" />
      <el-table-column label="商户编码" align="center" prop="number" width="120" />
      <el-table-column label="社会信用代码" align="center" prop="usci" width="150" />
      <el-table-column label="法人" align="center" prop="faren" width="100" />
      <el-table-column label="联系人" align="center" prop="linkMan" width="100" />
      <el-table-column label="手机号" align="center" prop="mobile" width="120" />
      <el-table-column label="联系地址" align="left" prop="address" min-width="200" show-overflow-tooltip />
      <el-table-column label="备注" align="center" prop="remark" width="150" show-overflow-tooltip />
      <el-table-column label="状态" align="center" prop="disable" width="80">
        <template #default="scope"><el-tag v-if="scope.row.disable==0" type="success" size="small">启用</el-tag><el-tag v-else size="small">禁用</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button size="small" type="text" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button size="small" type="text" @click="handleDelete(scope.row)">删除</el-button>
          <el-button size="small" type="text" @click="handleSetLogin(scope.row)">设置登录账号</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="商户名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商户名称" />
        </el-form-item>
        <el-form-item label="商户编码" prop="number">
          <el-input v-model="form.number" placeholder="请输入商户编码" />
        </el-form-item>
        <el-form-item label="社会信用代码" prop="usci">
          <el-input v-model="form.usci" placeholder="请输入社会信用代码" />
        </el-form-item>
        <el-form-item label="法人" prop="faren">
          <el-input v-model="form.faren" placeholder="请输入法人" />
        </el-form-item>
        <el-form-item label="联系人" prop="linkMan">
          <el-input v-model="form.linkMan" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="form.mobile" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="联系地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入联系地址" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="备注" />
        </el-form-item>
        <el-form-item label="状态" prop="disable">
          <el-radio-group v-model="form.disable"><el-radio :value="0">启用</el-radio><el-radio :value="1">禁用</el-radio></el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="设置登录账号" v-model="loginOpen" width="480px" append-to-body>
      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="100px">
        <el-form-item label="商户">
          <el-input :model-value="loginForm.name" disabled />
        </el-form-item>
        <el-form-item label="登录名" prop="loginName">
          <el-input v-model="loginForm.loginName" placeholder="请输入登录名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitLogin">确 定</el-button>
        <el-button @click="loginOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { listMerchant, getMerchant, addMerchant, updateMerchant, delMerchant, setMerchantLoginName } from '@/api/shop/merchant'
import { parseTime } from '@/utils/zhijian'
import Pagination from '@/components/Pagination/index.vue'
import RightToolbar from '@/components/RightToolbar/index.vue'

const loading=ref(true);const showSearch=ref(true);const total=ref(0)
const merchantList=ref<any[]>([]);const open=ref(false);const title=ref('')
const loginOpen=ref(false)
const loginFormRef=ref<FormInstance>()
const loginForm=reactive({id:null as number|null,name:'',loginName:'',password:''})
const loginRules={loginName:[{required:true,message:'登录名不能为空',trigger:'blur'}],password:[{required:true,message:'密码不能为空',trigger:'blur'}]}
const queryParams=reactive({pageNum:1,pageSize:10,name:null as string|null,number:null as string|null,linkman:null as string|null})
const form=reactive<Record<string,any>>({})
const rules={name:[{required:true,message:'不能为空'}],number:[{required:true,message:'不能为空'}]}

function getList(){loading.value=true;listMerchant(queryParams).then((res:any)=>{merchantList.value=res.rows||[];total.value=res.total||0;loading.value=false}).catch(()=>{loading.value=false})}
function handleQuery(){queryParams.pageNum=1;getList()}
function resetQuery(){queryParams.name=null;queryParams.number=null;queryParams.linkman=null;handleQuery()}
function handleSelectionChange(){}
function handleAdd(){title.value='新增商户';Object.assign(form,{name:null,number:null,usci:null,faren:null,linkMan:null,mobile:null,address:null,remark:null,disable:0});open.value=true}
function handleUpdate(row:any){getMerchant(row.id).then((res:any)=>{Object.assign(form,res.data||{});title.value='修改商户';open.value=true})}
function handleDelete(row:any){ElMessageBox.confirm('确认删除？').then(()=>delMerchant(row.id)).then(()=>{ElMessage.success('删除成功');getList()})}
function handleSetLogin(row:any){
  loginForm.id=row.id
  loginForm.name=row.name||''
  loginForm.loginName=row.loginName||''
  loginForm.password=''
  loginOpen.value=true
}
function submitLogin(){
  loginFormRef.value?.validate((valid)=>{
    if(!valid||loginForm.id==null)return
    setMerchantLoginName({id:loginForm.id,loginName:loginForm.loginName,password:loginForm.password}).then((res:any)=>{
      if(res.code===200){ElMessage.success('设置成功');loginOpen.value=false;getList()}
      else ElMessage.error(res.msg||'设置失败')
    })
  })
}
function submitForm(){
  const api=form.id?updateMerchant:addMerchant
  api({...form}).then(()=>{ElMessage.success('保存成功');open.value=false;getList()})
}
function cancel(){open.value=false}
onMounted(()=>{getList()})
</script>