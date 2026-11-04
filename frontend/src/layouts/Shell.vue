<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
import Workspace from "../views/Workspace.vue";
const router = useRouter(),
  auth = useAuth();
const active = ref("概览");
const roleLabel = computed(
  () =>
    (
      ({
        SYS_ADMIN: "系统管理员",
        LAB_ADMIN: "实验室管理员",
        READ_ONLY_ADMIN: "只读管理员",
        USER: "普通用户",
      }) as Record<string, string>
    )[auth.user?.role] || "普通用户",
);
const navItems = computed(() =>
  auth.user?.role === "SYS_ADMIN"
    ? [
        "概览",
        "设备档案",
        "培训与授权",
        "预约管理",
        "履约申诉",
        "操作审计",
        "报修工单",
        "用户管理",
        "校园身份",
        "实验室与分类",
        "站内通知",
        "个人信息",
      ]
    : auth.user?.role === "LAB_ADMIN"
      ? [
          "概览",
          "设备档案",
          "培训与授权",
          "预约管理",
          "履约申诉",
          "操作审计",
          "报修工单",
          "实验室与分类",
          "站内通知",
          "个人信息",
        ]
      : auth.user?.role === "READ_ONLY_ADMIN"
        ? [
            "概览",
            "设备档案",
            "培训与授权",
            "预约管理",
            "履约申诉",
            "操作审计",
            "报修工单",
            "用户管理",
            "校园身份",
            "站内通知",
            "个人信息",
          ]
        : [
            "概览",
            "设备档案",
            "培训与授权",
            "预约管理",
            "报修工单",
            "站内通知",
            "个人信息",
          ],
);
function exit() {
  auth.logout();
  router.replace("/login");
}
</script>
<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">L</div>
        <div><b>LABORATORY</b><small>实验室设备平台</small></div>
      </div>
      <div class="nav-label">工作空间</div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item"
          :class="{ selected: active === item }"
          @click="active = item"
        >
          <span class="nav-dot"></span>{{ item }}
        </button>
      </nav>
      <div class="side-foot">设备全生命周期管理</div>
    </aside>
    <main class="main">
      <header class="topbar">
        <div>
          <span class="crumb">实验室运营</span><span class="slash">/</span
          ><b>{{ active }}</b>
        </div>
        <div class="profile">
          <span class="status-dot"></span
          ><span>{{
            auth.user?.realName || auth.user?.username || "已登录用户"
          }}</span
          ><small>{{ roleLabel }}</small
          ><el-button text @click="exit">退出</el-button>
        </div>
      </header>
      <section class="content">
        <Workspace :active="active" @navigate="active = $event" />
      </section>
    </main>
  </div>
</template>
