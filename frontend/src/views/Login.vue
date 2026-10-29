<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuth } from "../stores/auth";

const form = reactive({
  username: "student",
  password: "LabDemo123!",
  identityNo: "",
  realName: "",
  email: "",
  phone: "",
});
const loading = ref(false);
const registering = ref(false);
const auth = useAuth();
const router = useRouter();

async function submit() {
  if (loading.value) return;
  if (!form.username.trim() || !form.password) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  loading.value = true;
  try {
    await auth.login(form.username.trim(), form.password);
    await router.replace("/");
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || "登录失败，请检查账号和密码");
  } finally {
    loading.value = false;
  }
}

async function register() {
  if (loading.value) return;
  if (![form.username, form.realName, form.identityNo, form.email, form.phone, form.password].every(value => value.trim())) {
    ElMessage.warning("请完整填写注册信息");
    return;
  }
  if (form.password.length < 8) {
    ElMessage.warning("密码至少需要 8 位");
    return;
  }
  loading.value = true;
  try {
    await auth.register(form);
    ElMessage.success("注册成功，请登录");
    registering.value = false;
    form.password = "";
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || "注册失败，请检查填写内容");
  } finally {
    loading.value = false;
  }
}

function handleSubmit() {
  return registering.value ? register() : submit();
}
</script>

<template>
  <main class="login-page">
    <section class="login-brand">
      <div class="brand-mark large">L</div>
      <p class="eyebrow">LABORATORY OPERATIONS</p>
      <h1>实验室设备<br />预约与维护管理</h1>
      <p class="login-copy">统一管理设备档案、预约审批与维修工单。</p>
      <div class="login-bottom">高校实验室 · 设备运营平台</div>
    </section>
    <section class="login-form-wrap">
      <div class="login-form">
        <div class="form-kicker">{{ registering ? "校园身份注册" : "账号登录" }}</div>
        <h2>{{ registering ? "创建账号" : "欢迎使用" }}</h2>
        <p>{{ registering ? "请使用学校预登记的姓名和学号或工号" : "使用平台账号进入工作空间" }}</p>
        <el-form label-position="left" label-width="92px" @submit.prevent="handleSubmit">
          <template v-if="registering">
            <el-form-item label="用户名">
              <el-input v-model="form.username" size="large" autocomplete="username" />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="form.realName" size="large" autocomplete="name" />
            </el-form-item>
            <el-form-item label="学号 / 工号">
              <el-input v-model="form.identityNo" inputmode="numeric" maxlength="9" placeholder="学生9位 / 教职工7位" size="large" />
              <div class="field-hint">学生示例：202601001；教职工示例：2026001</div>
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" type="email" size="large" autocomplete="email" />
            </el-form-item>
            <el-form-item label="电话">
              <el-input v-model="form.phone" size="large" autocomplete="tel" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="用户名">
              <el-input v-model="form.username" size="large" autocomplete="username" />
            </el-form-item>
          </template>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" size="large" show-password :autocomplete="registering ? 'new-password' : 'current-password'" />
          </el-form-item>
          <el-button native-type="submit" type="primary" size="large" :loading="loading" class="login-submit" @click="handleSubmit">
            {{ registering ? "注册" : "登录工作台" }}
          </el-button>
        </el-form>
        <el-button text class="login-submit switch-mode" @click="registering = !registering">
          {{ registering ? "返回登录" : "使用校园身份注册" }}
        </el-button>
        <div v-if="!registering" class="demo-hint">演示账号 student / LabDemo123!</div>
      </div>
    </section>
  </main>
</template>
