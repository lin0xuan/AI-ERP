<template>
  <div class="sidebar-logo-container" :class="{ collapse }">
    <router-link class="sidebar-logo-link" to="/">
      <img :src="logo" class="sidebar-logo" alt="AI ERP Logo" />
      <h1 v-if="!collapse" class="sidebar-title">{{ title }}</h1>
    </router-link>
  </div>
</template>

<script setup lang="ts">
interface Props {
  collapse: boolean
}

withDefaults(defineProps<Props>(), { collapse: false })

const title = import.meta.env.VITE_APP_TITLE
const logo = '/ai-erp-logo.png'
</script>

<style lang="scss" scoped>
.sidebar-logo-container {
  position: relative;
  width: 100%;
  height: 50px;
  line-height: 50px;
  background: #ffffff;
  text-align: center;
  overflow: hidden;

  .sidebar-logo-link {
    height: 100%;
    width: 100%;
    text-decoration: none;

    .sidebar-logo {
      width: 32px;
      height: 32px;
      margin-right: 8px;
      object-fit: contain;
      vertical-align: middle;
    }

    .sidebar-title {
      display: inline-block;
      margin: 0;
      color: #001529;
      font-weight: 600;
      line-height: 50px;
      font-size: 14px;
      font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;
      vertical-align: middle;
    }
  }

  &.collapse .sidebar-logo {
    margin-right: 0;
  }
}
</style>
