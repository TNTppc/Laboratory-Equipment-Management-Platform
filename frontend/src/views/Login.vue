<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
const form=reactive({username:'student',password:'LabDemo123!'}), loading=ref(false), auth=useAuth(), router=useRouter()
async function submit(){ loading.value=true; try{await auth.login(form.username,form.password); router.replace('/')}finally{loading.value=false} }
</script>
<template><main class="login-page"><section class="login-brand"><div class="brand-mark large">L</div><p class="eyebrow">LABORATORY OPERATIONS</p><h1>实验室设备<br>预约与维护管理</h1><p class="login-copy">统一管理设备档案、预约审批与维修工单。</p><div class="login-bottom">高校实验室 · 设备运营平台</div></section><section class="login-form-wrap"><div class="login-form"><div class="form-kicker">账号登录</div><h2>欢迎使用</h2><p>使用平台账号进入工作空间</p><el-form @submit.prevent="submit"><el-form-item label="用户名"><el-input v-model="form.username" size="large" autocomplete="username" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" size="large" show-password autocomplete="current-password" @keyup.enter="submit" /></el-form-item><el-button type="primary" size="large" :loading="loading" class="login-submit" @click="submit">登录工作台</el-button></el-form><div class="demo-hint">演示账号 student / LabDemo123!</div></div></section></main></template>
