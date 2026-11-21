<template>
  <el-form-item v-for="field in fields" :key="field.field_key" :label="field.label" :required="!!field.required_flag">
    <el-input-number v-if="field.field_type==='number'" v-model="values[field.field_key]"/>
    <el-date-picker v-else-if="field.field_type==='date'" v-model="values[field.field_key]" value-format="YYYY-MM-DD"/>
    <el-switch v-else-if="field.field_type==='boolean'" v-model="values[field.field_key]"/>
    <el-select v-else-if="field.field_type==='select'" v-model="values[field.field_key]"><el-option v-for="option in JSON.parse(field.options_json)" :key="option" :value="option"/></el-select>
    <el-input v-else v-model="values[field.field_key]" maxlength="1000"/>
  </el-form-item>
</template>
<script setup lang="ts">
defineProps<{fields:any[],values:Record<string,any>}>()
</script>
