<template>
  <div class="tender-not-found">
    <el-empty description="该标讯已被删除或不存在">
      <el-button type="primary" @click="goBack">返回标讯列表</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
const goBack = () => router.push('/bidding')
</script>

<style scoped>
.tender-not-found {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}
</style>
