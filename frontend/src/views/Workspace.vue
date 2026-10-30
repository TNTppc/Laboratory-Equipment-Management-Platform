<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  ElMessage,
  ElMessageBox,
  type UploadFile,
  type UploadFiles,
  type UploadRequestOptions,
} from "element-plus";
import * as echarts from "echarts";
import { api } from "../api";
import { useAuth } from "../stores/auth";
import {
  formatDateTime,
  localInputToUtc,
  timeZoneLabel,
} from "../utils/datetime";

const props = defineProps<{ active: string }>();
const emit = defineEmits<{ navigate: [page: string] }>();
const auth = useAuth();
const router = useRouter();
const busy = ref(false),
  rows = ref<any[]>([]),
  stats = ref<any>(null),
  dashboard = ref<any>(null),
  labs = ref<any[]>([]),
  cats = ref<any[]>([]),
  equipment = ref<any[]>([]),
  userOptions = ref<any[]>([]),
  contacts = ref<any[]>([]),
  autoApprovals = ref<any[]>([]),
  appeals = ref<any[]>([]),
  profile = ref<any>({}),
  profilePassword = ref(""),
  profileNewPassword = ref(""),
  autoMinutes = ref(60),
  showAutoApproval = ref(false),
  fulfillmentDialog = ref(false),
  fulfillmentRow = ref<any>(null),
  fulfillmentStage = ref(""),
  fulfillmentReason = ref(""),
  fulfillmentImageUrl = ref(""),
  fulfillmentEdit = ref(false),
  auditRows = ref<any[]>([]);
const autoScopeType = ref("LAB"),
  autoScopeId = ref<number | null>(null),
  autoAction = ref("APPROVE");
const keyword = ref(""),
  dialog = ref(false),
  detailDialog = ref(false),
  detail = ref<any>({}),
  editId = ref<number | null>(null),
  chartEl = ref<HTMLElement>(),
  page = ref(1),
  total = ref(0),
  form = ref<any>({});
const repairUploading = ref(false),
  repairFiles = ref<UploadFile[]>([]);
const faultTypes = [
  "无法开机",
  "运行异常",
  "测量不准",
  "部件损坏",
  "网络或软件故障",
  "安全隐患",
  "其他",
];
const currentTimeZone = timeZoneLabel();
const dateTimeFields = new Set([
  "starts_at",
  "ends_at",
  "submitted_at",
  "created_at",
  "updated_at",
  "read_at",
]);
const admin = computed(() =>
    ["LAB_ADMIN", "SYS_ADMIN"].includes(auth.user?.role),
  ),
  sysadmin = computed(() => auth.user?.role === "SYS_ADMIN"),
  viewer = computed(() => auth.user?.role === "READ_ONLY_ADMIN"),
  manager = computed(() => admin.value || viewer.value);
const autoScopeOptions = computed(() => {
  if (autoScopeType.value === "LAB")
    return labs.value.map((x) => ({ id: x.id, name: x.name }));
  if (autoScopeType.value === "CATEGORY")
    return cats.value.map((x) => ({ id: x.id, name: x.name }));
  return equipment.value.map((x) => ({
    id: x.id,
    name: `${x.code || ""} ${x.name}`.trim(),
  }));
});
const title = computed(() =>
  props.active === "设备档案"
    ? "设备档案"
    : props.active === "预约管理"
      ? manager.value
        ? "预约审批"
        : "我的预约"
      : props.active === "报修工单"
        ? "报修与维修"
        : props.active,
);
const columns = computed(() =>
  props.active === "操作审计"
    ? [
        ["created_at", "时间"],
        ["operator_name", "操作人"],
        ["action", "操作类型"],
        ["target_type", "对象类型"],
        ["target_id", "对象编号"],
        ["equipment_name", "设备"],
        ["detail", "详情"],
      ]
    : props.active === "设备档案"
    ? [
        ["code", "设备编号"],
        ["name", "设备名称"],
        ["category", "分类"],
        ["laboratory", "所属实验室"],
        ["ownerName", "责任人"],
        ["status", "状态"],
      ]
    : props.active === "预约管理"
      ? [
          ["equipment_name", "设备"],
          ["starts_at", "开始时间"],
          ["ends_at", "结束时间"],
          ["purpose", "使用目的"],
          ["status", "状态"],
        ]
      : props.active === "报修工单"
        ? [
            ["equipment_name", "设备"],
            ["reporter_name", "报修人"],
            ["fault_type", "故障类型"],
            ["status", "工单状态"],
            ["created_at", "提交时间"],
          ]
        : props.active === "用户管理"
          ? [
              ["username", "账号"],
              ["realName", "姓名"],
              ["role", "角色"],
              ["email", "邮箱"],
              ["enabled", "启用"],
            ]
          : props.active === "校园身份"
            ? [
                ["identityNo", "学号 / 工号"],
                ["realName", "姓名"],
                ["personType", "人员类型"],
                ["role", "默认角色"],
                ["username", "绑定账号"],
                ["active", "有效"],
              ]
            : props.active === "实验室与分类"
              ? [
                  ["dataType", "类型"],
                  ["name", "名称"],
                  ["location", "位置"],
                  ["description", "说明"],
                ]
              : [
                  ["title", "标题"],
                  ["content", "内容"],
                  ["read_at", "阅读时间"],
                  ["created_at", "时间"],
                ],
);

async function load() {
  busy.value = true;
  try {
    if (props.active === "概览") {
      if (manager.value) {
        stats.value = await api.get("/stats");
        await drawChart();
      } else dashboard.value = await api.get("/dashboard/me");
    } else if (props.active === "设备档案") {
      const result: any = await api.get("/equipment", {
        params: { page: page.value, size: 10, keyword: keyword.value },
      });
      rows.value = result.items;
      total.value = result.total;
    } else if (props.active === "操作审计") {
      auditRows.value = await api.get("/reservations/audit");
      rows.value = auditRows.value;
    } else if (props.active === "预约管理") {
      rows.value = manager.value
        ? await api.get("/reservations/pending")
        : await api.get("/reservations");
      if (admin.value) {
        autoApprovals.value = await api.get("/reservations/auto-approval");
        appeals.value = await api.get("/reservations/appeals");
        const [labData, categoryData, equipmentData]: any[] = await Promise.all([
          api.get("/labs"),
          api.get("/categories"),
          api.get("/equipment/options"),
        ]);
        labs.value = labData;
        cats.value = categoryData;
        equipment.value = equipmentData;
      }
    } else if (props.active === "报修工单")
      rows.value = await api.get("/repairs");
    else if (props.active === "用户管理")
      rows.value = await api.get("/admin/users");
    else if (props.active === "校园身份")
      rows.value = await api.get("/admin/identities");
    else if (props.active === "实验室与分类") {
      const [labData, categoryData]: any[] = await Promise.all([
        api.get("/labs"),
        api.get("/categories"),
      ]);
      labs.value = labData;
      cats.value = categoryData;
      rows.value = [
        ...labData.map((x: any) => ({
          ...x,
          dataType: "实验室",
          kind: "实验室",
        })),
        ...categoryData.map((x: any) => ({
          ...x,
          dataType: "设备分类",
          kind: "分类",
        })),
      ];
    } else if (props.active === "站内通知")
      rows.value = await api.get("/notifications");
    else if (props.active === "个人信息") {
      profile.value = await api.get("/auth/me");
      auth.user = profile.value;
    }
  } finally {
    busy.value = false;
  }
}
async function drawChart() {
  await nextTick();
  if (!chartEl.value || !stats.value?.byLab) return;
  const chart =
    echarts.getInstanceByDom(chartEl.value) || echarts.init(chartEl.value);
  chart.setOption({
    tooltip: { trigger: "axis" },
    grid: { left: 40, right: 20, top: 25, bottom: 30 },
    xAxis: {
      type: "category",
      data: stats.value.byLab.map((x: any) => x.laboratory),
    },
    yAxis: { type: "value" },
    series: [
      {
        type: "bar",
        data: stats.value.byLab.map((x: any) => x.bookings),
        barMaxWidth: 36,
        itemStyle: { color: "#3c8c7a" },
      },
    ],
  });
}
async function open(row?: any) {
  editId.value = row?.id ?? null;
  form.value = row ? { ...row } : { status: "AVAILABLE", participants: 1 };
  repairFiles.value = [];
  if (props.active === "设备档案") {
    const [labData, categoryData, users]: any[] = await Promise.all([
      api.get("/labs"),
      api.get("/categories"),
      api.get("/users/options"),
    ]);
    labs.value = labData;
    cats.value = categoryData;
    userOptions.value = users;
  }
  if (props.active === "预约管理" || props.active === "报修工单")
    equipment.value = (
      await api.get<any>("/equipment", {
        params: { page: 1, size: 100, status: "AVAILABLE" },
      })
    ).items;
  dialog.value = true;
}
async function save() {
  if (props.active === "设备档案")
    editId.value
      ? await api.put(`/equipment/${editId.value}`, form.value)
      : await api.post("/equipment", form.value);
  else if (props.active === "预约管理") {
    if (
      !form.value.equipmentId ||
      !form.value.startsAt ||
      !form.value.endsAt ||
      !form.value.purpose?.trim()
    )
      return ElMessage.warning("请完整填写设备、预约时间和使用目的");
    await api.post("/reservations", {
      ...form.value,
      startsAt: localInputToUtc(form.value.startsAt),
      endsAt: localInputToUtc(form.value.endsAt),
    });
  } else if (props.active === "报修工单") {
    if (!form.value.equipmentId) return ElMessage.warning("请选择报修设备");
    if (!form.value.faultType) return ElMessage.warning("请选择故障类型");
    if (!form.value.description?.trim())
      return ElMessage.warning("请填写故障描述");
    if (repairUploading.value) return ElMessage.warning("图片正在上传，请稍候");
    await api.post(`/repairs/equipment/${form.value.equipmentId}`, form.value);
  } else if (props.active === "用户管理")
    editId.value
      ? await api.put(`/admin/users/${editId.value}`, form.value)
      : await api.post("/admin/users", form.value);
  else if (props.active === "校园身份")
    editId.value
      ? await api.put(`/admin/identities/${editId.value}`, form.value)
      : await api.post("/admin/identities", form.value);
  else if (props.active === "实验室与分类") {
    const root =
      form.value.kind === "分类" ? "/admin/categories" : "/admin/labs";
    editId.value
      ? await api.put(`${root}/${editId.value}`, form.value)
      : await api.post(root, form.value);
  }
  dialog.value = false;
  ElMessage.success("已保存");
  await load();
}
async function uploadRepairImage(options: UploadRequestOptions) {
  repairUploading.value = true;
  try {
    const result: any = await api.upload("/uploads/repair-image", options.file);
    form.value.imageUrls = result.url;
    options.onSuccess(result);
    ElMessage.success("图片上传成功");
  } catch (error) {
    options.onError(error as any);
  } finally {
    repairUploading.value = false;
  }
}
function removeRepairImage(_file: UploadFile, files: UploadFiles) {
  repairFiles.value = files;
  if (!files.length) form.value.imageUrls = "";
}
async function showDetail(row: any) {
  detail.value =
    props.active === "设备档案"
      ? await api.get(`/equipment/${row.id}`)
      : await api.get(`/repairs/${row.id}`);
  detailDialog.value = true;
}
async function review(row: any, decision: string) {
  const { value } = await ElMessageBox.prompt(
    `预约人：${row.user_name}\n电话：${row.user_phone || "未填写"}\n邮箱：${row.user_email || "未填写"}\n填写审核意见`,
    "预约审核",
  );
  await api.post(`/reservations/${row.id}/review`, {
    decision,
    comment: value,
  });
  await load();
}
async function cancel(row: any) {
  await ElMessageBox.confirm("确定取消该预约？", "确认操作");
  await api.post(`/reservations/${row.id}/cancel`);
  await load();
}
async function submitAppeal(row: any) {
  const { value } = await ElMessageBox.prompt(
    "请说明实际履约情况，管理员审核后将决定是否恢复被扣除的信誉积分。",
    "申请预约履约申诉",
    { inputType: "textarea", inputPlaceholder: "申诉理由", inputValidator: value => !!value?.trim() || "请填写申诉理由" },
  );
  await api.post(`/reservations/${row.id}/appeal`, { reason: value });
  ElMessage.success("申诉已提交");
  await load();
}
async function markFulfillment(row: any) {
  fulfillmentRow.value = row;
  fulfillmentStage.value = "";
  fulfillmentReason.value = "";
  fulfillmentImageUrl.value = "";
  fulfillmentEdit.value = false;
  fulfillmentDialog.value = true;
}
function editFulfillment(row: any) {
  fulfillmentRow.value = row;
  fulfillmentStage.value = row.fulfillment_status;
  fulfillmentReason.value = row.fulfillment_reason || "";
  fulfillmentImageUrl.value = row.fulfillment_image_url || "";
  fulfillmentEdit.value = true;
  fulfillmentDialog.value = true;
}
async function saveFulfillment() {
  if (!fulfillmentStage.value) return ElMessage.warning("请选择履约阶段");
  if (!["RETURNED", "NOT_STARTED"].includes(fulfillmentStage.value) && !fulfillmentReason.value.trim()) return ElMessage.warning("请填写原因");
  const payload = { stage: fulfillmentStage.value, reason: fulfillmentReason.value, imageUrl: fulfillmentImageUrl.value };
  if (fulfillmentEdit.value) await api.put(`/reservations/${fulfillmentRow.value.id}/fulfillment`, payload);
  else await api.post(`/reservations/${fulfillmentRow.value.id}/fulfillment`, payload);
  fulfillmentDialog.value = false;
  ElMessage.success("履约结果已登记");
  await load();
}
async function uploadFulfillmentImage(options: UploadRequestOptions) {
  try {
    const result: any = await api.upload("/uploads/repair-image", options.file);
    fulfillmentImageUrl.value = result.url;
    options.onSuccess(result);
  } catch (error) { options.onError(error as any); }
}
function viewRejectReason(row: any) {
  const labels: Record<string, string> = { APPROVAL_TIMEOUT: "超过审批时限，系统自动驳回", ADMIN_REJECTED: row.review_comment || "管理员驳回", AUTO_REJECTED: row.review_comment || "命中自动审批拒绝规则" };
  ElMessageBox.alert(labels[row.reject_reason] || row.review_comment || "系统未提供详细原因", "拒绝原因");
}
async function reviewAppeal(row: any, decision: string) {
  const { value } = await ElMessageBox.prompt("填写申诉审核意见", "审核履约申诉", { inputType: "textarea", inputPlaceholder: "审核意见（可选）" });
  await api.post(`/reservations/appeals/${row.id}/review`, { decision, comment: value || "" });
  ElMessage.success("申诉已处理");
  await load();
}
async function process(row: any) {
  const { value } = await ElMessageBox.prompt(
    "填写维修说明；完成时输入“恢复”将设备恢复为可预约",
    "维修处理",
  );
  const status =
    row.status === "OPEN"
      ? "PROCESSING"
      : row.status === "PROCESSING"
        ? "WAIT_ACCEPTANCE"
        : "COMPLETED";
  await api.post(`/repairs/${row.id}/process`, {
    status,
    note: value,
    restoreAvailable: status === "COMPLETED" && value.includes("恢复"),
  });
  await load();
}
async function markRead(row: any) {
  await api.post(`/notifications/${row.id}/read`);
  await load();
}
function statusType(status: string) {
  return (
    (
      {
        AVAILABLE: "success",
        MAINTENANCE: "warning",
        DISABLED: "info",
        PENDING: "warning",
        APPROVED: "success",
        REJECTED: "danger",
        CANCELLED: "info",
        OPEN: "warning",
        PROCESSING: "primary",
        WAIT_ACCEPTANCE: "warning",
        COMPLETED: "success",
      } as any
    )[status] || "info"
  );
}
function displayValue(row: any, field: string) {
  if (field === "reject_reason") return rejectReasonLabel(row[field]);
  if (field === "fulfillment_status") return fulfillmentLabel(row[field]);
  if (field === "detail" && typeof row[field] === "object") return JSON.stringify(row[field]);
  return dateTimeFields.has(field)
    ? formatDateTime(row[field])
    : (row[field] ?? "—");
}
function rejectReasonLabel(reason: string) {
  return ({ APPROVAL_TIMEOUT: "超时拒绝", ADMIN_REJECTED: "管理员拒绝", AUTO_REJECTED: "自动审批拒绝" } as Record<string, string>)[reason] || (reason ? reason : "—");
}
function fulfillmentLabel(stage: string) {
  return ({ NOT_STARTED: "待登记", OVERDUE_NOT_RETURNED: "逾期未归还", NOT_FULFILLED: "未履约", RETURNED: "已归还" } as Record<string, string>)[stage] || "—";
}
function statusLabel(status: string) {
  return ({ PENDING: "待审批", APPROVED: "已通过", REJECTED: "已驳回", CANCELLED: "已取消", COMPLETED: "已完成" } as Record<string, string>)[status] || status || "—";
}
function cutoff(row: any) {
  if (!row.starts_at) return "—";
  const start = new Date(`${String(row.starts_at).replace(" ", "T")}Z`);
  return formatDateTime(
    new Date(start.getTime() - 2 * 60 * 60 * 1000).toISOString(),
  );
}
async function setAutoApproval(minutes: number) {
  if (!autoScopeId.value && autoScopeType.value !== "GLOBAL")
    return ElMessage.warning("请选择自动审批范围");
  await api.post("/reservations/auto-approval", {
    scopeType: autoScopeType.value,
    scopeId: autoScopeId.value,
    action: autoAction.value,
    minutes,
  });
  ElMessage.success(minutes ? "自动审批规则已开启" : "自动审批规则已关闭");
  await load();
}
async function saveProfile() {
  await api.put("/auth/me", {
    email: profile.value.email,
    phone: profile.value.phone,
    currentPassword: profilePassword.value || null,
    newPassword: profileNewPassword.value || null,
  });
  profilePassword.value = "";
  profileNewPassword.value = "";
  ElMessage.success("个人信息已更新");
  await load();
}
async function deactivateAccount() {
  if (sysadmin.value) {
    ElMessage.warning("系统管理员账号不能自行注销，请先交接系统管理权限");
    return;
  }
  try {
    const { value: currentPassword } = await ElMessageBox.prompt(
      "注销后将立即退出，账号不能再登录；历史预约和维修记录会保留。请输入当前密码确认。",
      "注销账号",
      { confirmButtonText: "确认注销", cancelButtonText: "取消", inputType: "password", inputPlaceholder: "当前密码", inputValidator: value => !!value || "请输入当前密码" },
    );
    await ElMessageBox.confirm("此操作不可撤销，确定注销当前账号吗？", "再次确认", { type: "warning", confirmButtonText: "注销账号", cancelButtonText: "返回" });
    await api.delete("/auth/me", { currentPassword });
    auth.logout();
    ElMessage.success("账号已注销");
    await router.replace("/login");
  } catch (error: any) {
    if (error !== "cancel" && error !== "close") ElMessage.error(error.response?.data?.message || error.message || "注销失败");
  }
}
function roleLabel(role: string) {
  return (
    (
      {
        SYS_ADMIN: "系统管理员",
        LAB_ADMIN: "实验室管理员",
        READ_ONLY_ADMIN: "只读管理员",
        USER: "普通用户",
      } as Record<string, string>
    )[role] ||
    role ||
    "—"
  );
}
function disableBookingDate(date: Date) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const min = new Date(today);
  min.setDate(min.getDate() + 1);
  const max = new Date(today);
  max.setDate(max.getDate() + 7);
  return date < min || date > max;
}
function bookingShortcuts(_field: "startsAt" | "endsAt") {
  return [{
    text: "最早可预约时间",
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() + 1);
      start.setHours(8, 0, 0, 0);
      const end = new Date(start.getTime() + 60 * 60 * 1000);
      const inputValue = (value: Date) => {
        const pad = (n: number) => String(n).padStart(2, "0");
        return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:00`;
      };
      form.value.startsAt = inputValue(start);
      form.value.endsAt = inputValue(end);
      return start;
    },
  }];
}
async function loadContacts() {
  contacts.value = await api.get("/auth/contacts");
}
async function showContacts() {
  await loadContacts();
  detail.value = { contacts: contacts.value };
  detailDialog.value = true;
}
watch(() => props.active, load);
onMounted(async () => {
  const me: any = await api.get("/auth/me");
  auth.user = Array.isArray(me) ? me[0] : me;
  await load();
});
</script>

<template>
  <div>
    <template v-if="active === '概览'"
      ><div class="page-heading">
        <div>
          <div class="eyebrow">OVERVIEW</div>
          <h1>{{ manager ? "运营概览" : "我的工作台" }}</h1>
          <p>
            {{
              manager ? "实验室设备与服务运行状态" : "个人预约与设备服务进度"
            }}
          </p>
        </div>
        <el-button v-if="manager" @click="load">刷新数据</el-button>
      </div>
      <template v-if="manager && stats"
        ><div class="metric-grid">
          <div class="metric">
            <span>设备总数</span><b>{{ stats.equipment.total || 0 }}</b
            ><small>当前在册设备</small>
          </div>
          <div class="metric">
            <span>可预约</span
            ><b class="green">{{ stats.equipment.available || 0 }}</b
            ><small>开放预约设备</small>
          </div>
          <div class="metric">
            <span>维修中</span
            ><b class="amber">{{ stats.equipment.maintenance || 0 }}</b
            ><small>待恢复设备</small>
          </div>
          <div class="metric">
            <span>预约通过率</span
            ><b
              >{{
                stats.reservations.reviewed
                  ? Math.round(
                      (stats.reservations.approved /
                        stats.reservations.reviewed) *
                        100,
                    )
                  : 0
              }}%</b
            ><small>已审核预约</small>
          </div>
        </div>
        <div class="data-section">
          <div class="section-title">
            <div>
              <h3>各实验室预约量</h3>
              <span>累计预约记录；使用时长按已通过和已完成预约计算</span>
            </div>
          </div>
          <div ref="chartEl" class="chart"></div></div></template
      ><template v-else-if="dashboard"
        ><div class="metric-grid">
          <div class="metric">
            <span>待审批预约</span
            ><b>{{ dashboard.reservations?.pending || 0 }}</b
            ><small>等待实验室管理员审核</small>
          </div>
          <div class="metric">
            <span>即将开始</span
            ><b class="green">{{ dashboard.reservations?.upcoming || 0 }}</b
            ><small>已通过且尚未结束</small>
          </div>
          <div class="metric">
            <span>处理中报修</span
            ><b class="amber">{{ dashboard.activeRepairs || 0 }}</b
            ><small>等待维修进展</small>
          </div>
          <div class="metric">
            <span>未读通知</span><b>{{ dashboard.unreadNotifications || 0 }}</b
            ><small>预约与报修消息</small>
          </div>
        </div>
        <div class="data-section">
          <div class="section-title">
            <div>
              <h3>近期预约</h3>
              <span>预约时间按{{ currentTimeZone }}显示</span>
            </div>
            <el-button text @click="emit('navigate', '预约管理')"
              >查看预约</el-button
            >
          </div>
          <el-table :data="dashboard.upcoming" empty-text="暂无即将开始的预约"
            ><el-table-column
              prop="equipment_name"
              label="设备" /><el-table-column
              prop="laboratory"
              label="实验室" /><el-table-column label="开始时间"
              ><template #default="s">{{
                formatDateTime(s.row.starts_at)
              }}</template></el-table-column
            ><el-table-column label="结束时间"
              ><template #default="s">{{
                formatDateTime(s.row.ends_at)
              }}</template></el-table-column
            ><el-table-column prop="status" label="状态"
          /></el-table>
        </div>
        <div class="data-section">
          <div class="section-title">
            <div>
              <h3>实验室管理员</h3>
              <span>预约审批未及时完成时，可联系管理员确认</span>
            </div>
          </div>
          <el-table :data="dashboard.managers" empty-text="暂无管理员联系方式"
            ><el-table-column prop="name" label="姓名" /><el-table-column
              prop="laboratory"
              label="负责实验室" /><el-table-column
              prop="phone"
              label="电话" /><el-table-column prop="email" label="邮箱"
          /></el-table></div></template
    ></template>
    <template v-else-if="active === '个人信息'"
      ><div class="page-heading">
        <div>
          <div class="eyebrow">PROFILE</div>
          <h1>个人信息</h1>
          <p>姓名及校园身份编号由学校身份登记信息确定</p>
        </div>
      </div>
      <div class="data-section profile-section" v-loading="busy">
        <el-descriptions :column="2" border
          ><el-descriptions-item label="姓名">{{
            profile.realName
          }}</el-descriptions-item
          ><el-descriptions-item label="学号 / 工号">{{
            profile.identityNo
          }}</el-descriptions-item
          ><el-descriptions-item label="用户名">{{
            profile.username
          }}</el-descriptions-item
          ><el-descriptions-item label="身份">{{
            roleLabel(profile.role)
          }}</el-descriptions-item></el-descriptions
        ><el-alert
          v-if="profile.role === 'USER'"
          class="credit-alert"
          :title="`当前信誉积分：${profile.creditScore ?? 100} 分`"
          :type="Number(profile.creditScore ?? 100) < 60 ? 'error' : Number(profile.creditScore ?? 100) < 90 ? 'warning' : 'success'"
          :closable="false"
          description="90分及以上自动通过，60至90分需管理员审批，低于60分将暂时禁止预约。"
        />
        ><el-form label-position="top" class="profile-form"
          ><el-form-item label="邮箱"
            ><el-input v-model="profile.email" type="email" /></el-form-item
          ><el-form-item label="电话"
            ><el-input v-model="profile.phone" /></el-form-item
          ><el-divider content-position="left">修改密码（可选）</el-divider
          ><el-form-item label="当前密码"
            ><el-input
              v-model="profilePassword"
              type="password"
              show-password /></el-form-item
          ><el-form-item label="新密码"
            ><el-input
              v-model="profileNewPassword"
              type="password"
              show-password
              placeholder="至少 8 位；不修改可留空" /></el-form-item
          ><el-button type="primary" @click="saveProfile"
            >保存个人信息</el-button
          >
          <div class="profile-note">
            姓名、学号或工号与权限由学校预登记信息确定，个人不能修改。
          </div></el-form
        ><el-divider content-position="left">账号管理</el-divider>
        <div class="deactivate-account">
          <div><b>注销账号</b><p>账号将停用，历史预约、报修和操作记录会保留。</p></div>
          <el-button type="danger" plain :disabled="sysadmin" @click="deactivateAccount">注销账号</el-button>
        </div>
        <div v-if="sysadmin" class="profile-note">系统管理员不能自行注销，以避免系统失去管理权限。</div>
      </div></template
    >
    <template v-else
      ><div class="page-heading">
        <div>
          <div class="eyebrow">MANAGEMENT</div>
          <h1>{{ title }}</h1>
          <p>
            {{
              active === "设备档案"
                ? "实验室设备清单与状态"
                : active === "预约管理"
                  ? `提交预约并跟踪审批状态 · 时间按${currentTimeZone}显示`
                  : active === "报修工单"
                    ? `故障申报与维修处理记录 · 时间按${currentTimeZone}显示`
                    : active === "站内通知"
                      ? `审批和维修进度消息 · 时间按${currentTimeZone}显示`
                      : ""
            }}
          </p>
        </div>
        <div class="heading-actions">
          <el-input
            v-if="active === '设备档案'"
            v-model="keyword"
            placeholder="搜索设备名称或编号"
            clearable
            @keyup.enter="load"
            @clear="load"
          /><el-button
            v-if="active === '设备档案' && admin"
            type="primary"
            @click="open()"
            >新增设备</el-button
          ><el-button
            v-if="active === '预约管理' && !manager"
            type="primary"
            @click="open()"
            >提交预约</el-button
          ><el-button
            v-if="active === '预约管理' && admin"
            @click="showAutoApproval = true"
            >自动审批</el-button
          ><el-button
            v-if="active === '报修工单' && !manager"
            type="primary"
            @click="open()"
            >提交报修</el-button
          ><el-button
            v-if="active === '用户管理' && sysadmin"
            type="primary"
            @click="open()"
            >新增用户</el-button
          ><el-button
            v-if="active === '校园身份' && sysadmin"
            type="primary"
            @click="open()"
            >新增校园身份</el-button
          ><el-dropdown v-if="active === '实验室与分类' && sysadmin"
            ><el-button type="primary">新增基础数据</el-button
            ><template #dropdown
              ><el-dropdown-menu
                ><el-dropdown-item
                  @click="
                    form = { kind: '实验室' };
                    editId = null;
                    dialog = true;
                  "
                  >新增实验室</el-dropdown-item
                ><el-dropdown-item
                  @click="
                    form = { kind: '分类' };
                    editId = null;
                    dialog = true;
                  "
                  >新增设备分类</el-dropdown-item
                ></el-dropdown-menu
              ></template
            ></el-dropdown
          >
        </div>
      </div>
      <div v-if="active === '预约管理' && admin" class="notice-strip">
        预约开始前 2 小时仍未处理时，系统将自动驳回。请在截止时间前完成审批。
      </div>
      <div class="data-section">
        <el-table v-loading="busy" :data="rows" stripe empty-text="暂无数据"
          ><el-table-column
            v-for="column in columns"
            :key="column[0]"
            :prop="column[0]"
            :label="column[1]"
            min-width="125"
            ><template #default="scope"
              ><el-tag
                v-if="column[0] === 'status'"
                :type="statusType(scope.row[column[0]])"
                >{{ statusLabel(scope.row[column[0]]) }}</el-tag
              ><span v-else-if="column[0] === 'enabled'">{{
                scope.row[column[0]] ? "是" : "否"
              }}</span
              ><span v-else>{{
                displayValue(scope.row, column[0])
              }}</span></template
            ></el-table-column
          ><el-table-column
            v-if="active === '预约管理' && admin"
            label="审批截止时间"
            min-width="170"
            ><template #default="s">{{
              cutoff(s.row)
            }}</template></el-table-column
          ><el-table-column
            v-if="active === '预约管理' && admin"
            label="预约人联系方式"
            min-width="220"
            ><template #default="s"
              ><span
                >{{ s.row.user_name }} · {{ s.row.user_phone || "无电话" }} ·
                {{ s.row.user_email || "无邮箱" }}</span
              ></template
            ></el-table-column
          ><el-table-column label="操作" fixed="right" width="260"
            ><template #default="scope"
              ><template v-if="active === '预约管理'"
                ><el-button
                  v-if="scope.row.status === 'REJECTED'"
                  link
                  type="warning"
                  @click="viewRejectReason(scope.row)"
                  >查看拒绝原因</el-button
                ><el-button
                  v-if="admin && scope.row.fulfillment_operator_id != null && Number(scope.row.fulfillment_operator_id) === Number(auth.user?.id) && scope.row.fulfillment_status !== 'NOT_STARTED'"
                  link
                  type="primary"
                  @click="editFulfillment(scope.row)"
                  >修正履约</el-button
                ><el-button
                  v-if="admin && scope.row.status === 'PENDING'"
                  link
                  type="success"
                  @click="review(scope.row, 'APPROVED')"
                  >通过</el-button
                ><el-button
                  v-if="admin && scope.row.status === 'PENDING'"
                  link
                  type="danger"
                  @click="review(scope.row, 'REJECTED')"
                  >驳回</el-button
                ><el-button
                  v-if="admin && ['APPROVED', 'COMPLETED'].includes(scope.row.status) && scope.row.fulfillment_status === 'NOT_STARTED'"
                  link
                  type="primary"
                  @click="markFulfillment(scope.row)"
                  >登记履约</el-button
                ><el-button
                  v-if="!manager && ['OVERDUE_NOT_RETURNED', 'NOT_FULFILLED'].includes(scope.row.fulfillment_status) && scope.row.appeal_status !== 'PENDING'"
                  link
                  type="warning"
                  @click="submitAppeal(scope.row)"
                  >发起申诉</el-button
                ><el-button
                  v-if="!manager && ['PENDING', 'APPROVED'].includes(scope.row.status)"
                  link
                  type="danger"
                  @click="cancel(scope.row)"
                  >取消</el-button
                ></template
              ><el-button
                v-if="['设备档案', '报修工单'].includes(active)"
                link
                @click="showDetail(scope.row)"
                >详情</el-button
              ><el-button
                v-if="
                  active === '报修工单' &&
                  admin &&
                  !['COMPLETED', 'REJECTED'].includes(scope.row.status)
                "
                link
                type="primary"
                @click="process(scope.row)"
                >处理</el-button
              ><el-button
                v-if="active === '站内通知' && !scope.row.read_at"
                link
                type="primary"
                @click="markRead(scope.row)"
                >标为已读</el-button
              ><el-button
                v-if="
                  (active === '设备档案' && admin) ||
                  (active === '用户管理' && sysadmin) ||
                  (active === '校园身份' && sysadmin) ||
                  (active === '实验室与分类' && sysadmin)
                "
                link
                type="primary"
                @click="open(scope.row)"
                >编辑</el-button
              ></template
            ></el-table-column
          ></el-table
          ><template v-if="active === '预约管理' && admin"
            ><div class="section-title reservation-appeals-title">
              <div>
                <h3>待处理履约申诉</h3>
                <span>申诉通过后按违约类型恢复 1 或 2 分信誉积分</span>
              </div>
            </div>
            <el-table :data="appeals" empty-text="暂无待处理申诉" stripe>
              <el-table-column prop="applicant_name" label="申诉人" />
              <el-table-column prop="equipment_name" label="设备" />
              <el-table-column prop="reason" label="申诉理由" min-width="240" />
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="s">
                  <el-button link type="success" @click="reviewAppeal(s.row, 'APPROVED')">通过</el-button>
                  <el-button link type="danger" @click="reviewAppeal(s.row, 'REJECTED')">驳回</el-button>
                </template>
              </el-table-column>
            </el-table></template
        ><el-pagination
          v-if="active === '设备档案'"
          v-model:current-page="page"
          :page-size="10"
          :total="total"
          layout="total,prev,pager,next"
          @current-change="load"
        /></div
    ></template>

    <el-dialog v-model="dialog" :title="editId ? '编辑' : '新增'" width="580px"
      ><el-form label-position="top"
        ><template v-if="active === '设备档案'"
          ><div class="form-grid">
            <el-form-item label="设备编号"
              ><el-input v-model="form.code" /></el-form-item
            ><el-form-item label="设备名称"
              ><el-input v-model="form.name" /></el-form-item
            ><el-form-item label="设备分类"
              ><el-select v-model="form.categoryId"
                ><el-option
                  v-for="x in cats"
                  :key="x.id"
                  :label="x.name"
                  :value="x.id" /></el-select></el-form-item
            ><el-form-item label="所属实验室"
              ><el-select v-model="form.labId"
                ><el-option
                  v-for="x in labs"
                  :key="x.id"
                  :label="x.name"
                  :value="x.id" /></el-select></el-form-item
            ><el-form-item label="品牌型号"
              ><el-input v-model="form.brandModel" /></el-form-item
            ><el-form-item label="存放位置"
              ><el-input v-model="form.location" /></el-form-item
            ><el-form-item label="责任人"
              ><el-select v-model="form.ownerId" clearable
                ><el-option
                  v-for="x in userOptions"
                  :key="x.id"
                  :label="x.name"
                  :value="x.id" /></el-select></el-form-item
            ><el-form-item label="购置日期"
              ><el-date-picker
                v-model="form.purchaseDate"
                type="date"
                value-format="YYYY-MM-DD"
            /></el-form-item>
          </div>
          <el-form-item label="设备说明"
            ><el-input
              v-model="form.description"
              type="textarea" /></el-form-item
          ><el-form-item label="使用规则"
            ><el-input
              v-model="form.usageRules"
              type="textarea" /></el-form-item
          ><el-form-item label="图片地址"
            ><el-input v-model="form.imageUrl" /></el-form-item
          ><el-form-item label="状态"
            ><el-select v-model="form.status"
              ><el-option label="可预约" value="AVAILABLE" /><el-option
                label="维修中"
                value="MAINTENANCE" /><el-option
                label="停用"
                value="DISABLED" /></el-select></el-form-item></template
        ><template v-else-if="active === '预约管理'"
          ><el-form-item label="设备" required
            ><el-select v-model="form.equipmentId" placeholder="请选择设备"
              ><el-option
                v-for="x in equipment"
                :key="x.id"
                :value="x.id"
                :label="x.name" /></el-select></el-form-item
          ><el-form-item :label="`开始时间（${currentTimeZone}）`" required
            ><el-date-picker
              v-model="form.startsAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              format="YYYY年MM月DD日 HH:mm"
              :show-now="false"
              :shortcuts="bookingShortcuts('startsAt')"
              :disabled-date="disableBookingDate" /></el-form-item
          ><el-form-item :label="`结束时间（${currentTimeZone}）`" required
            ><el-date-picker
              v-model="form.endsAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              format="YYYY年MM月DD日 HH:mm"
              :show-now="false"
              :shortcuts="bookingShortcuts('endsAt')"
              :disabled-date="disableBookingDate" /></el-form-item
          ><el-form-item label="使用目的" required
            ><el-input v-model="form.purpose" /></el-form-item
          ><el-form-item label="参与人数"
            ><el-input-number
              v-model="form.participants"
              :min="1" /></el-form-item></template
        ><template v-else-if="active === '报修工单'"
          ><el-form-item label="设备" required
            ><el-select v-model="form.equipmentId" placeholder="请选择设备"
              ><el-option
                v-for="x in equipment"
                :key="x.id"
                :value="x.id"
                :label="x.name" /></el-select></el-form-item
          ><el-form-item label="故障类型" required
            ><el-select v-model="form.faultType" placeholder="请选择故障类型"
              ><el-option
                v-for="type in faultTypes"
                :key="type"
                :label="type"
                :value="type" /></el-select></el-form-item
          ><el-form-item label="故障描述" required
            ><el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit /></el-form-item
          ><el-form-item label="故障图片"
            ><el-upload
              v-model:file-list="repairFiles"
              list-type="picture-card"
              accept="image/jpeg,image/png,image/webp,image/gif"
              :limit="1"
              :http-request="uploadRepairImage"
              :on-remove="removeRepairImage"
              ><span>上传图片</span
              ><template #tip
                ><div class="upload-tip">
                  支持 JPG、PNG、WEBP、GIF，最大 5MB
                </div></template
              ></el-upload
            ></el-form-item
          ></template
        ><template v-else-if="active === '用户管理'"
          ><el-form-item label="用户名"
            ><el-input
              v-model="form.username"
              :disabled="!!editId" /></el-form-item
          ><el-form-item label="身份编号"
            ><el-input
              v-model="form.identityNo"
              :disabled="!!editId"
              inputmode="numeric"
              maxlength="9"
              placeholder="学生9位；教职工7位" /></el-form-item
          ><el-form-item v-if="!editId" label="初始密码"
            ><el-input v-model="form.password" type="password" /></el-form-item
          ><el-form-item label="邮箱"
            ><el-input v-model="form.email" /></el-form-item
          ><el-form-item label="电话"
            ><el-input v-model="form.phone" /></el-form-item
          ><el-form-item label="身份角色"
            ><el-select v-model="form.role" :disabled="!!editId"
              ><el-option label="普通用户" value="USER" /><el-option
                label="实验室管理员"
                value="LAB_ADMIN" /><el-option
                label="系统管理员"
                value="SYS_ADMIN" /><el-option
                label="只读管理员"
                value="READ_ONLY_ADMIN" /></el-select></el-form-item
          ><el-form-item v-if="editId" label="启用"
            ><el-switch v-model="form.enabled" /></el-form-item></template
        ><template v-else-if="active === '校园身份'"
          ><el-form-item label="学号 / 工号"
            ><el-input
              v-model="form.identityNo"
              inputmode="numeric"
              maxlength="9"
              :placeholder="
                form.personType === 'STUDENT'
                  ? '例如 202601001（年份+班级+编号）'
                  : '例如 2026001（年份+流水号）'
              "
              :disabled="!!form.username" /></el-form-item
          ><el-form-item label="姓名"
            ><el-input
              v-model="form.realName"
              :disabled="!!form.username" /></el-form-item
          ><el-form-item label="人员类型"
            ><el-select v-model="form.personType" :disabled="!!form.username"
              ><el-option label="学生" value="STUDENT" /><el-option
                label="教职工"
                value="STAFF" /></el-select></el-form-item
          ><el-form-item label="默认角色"
            ><el-select v-model="form.role" :disabled="!!form.username"
              ><el-option label="普通用户" value="USER" /><el-option
                label="实验室管理员"
                value="LAB_ADMIN" /><el-option
                label="系统管理员"
                value="SYS_ADMIN" /><el-option
                label="只读管理员"
                value="READ_ONLY_ADMIN" /></el-select></el-form-item
          ><el-form-item v-if="editId" label="有效"
            ><el-switch v-model="form.active" /></el-form-item></template
        ><template v-else
          ><el-form-item label="类型"
            ><el-select v-model="form.kind" :disabled="!!editId"
              ><el-option label="实验室" value="实验室" /><el-option
                label="设备分类"
                value="分类" /></el-select></el-form-item
          ><el-form-item label="名称"
            ><el-input v-model="form.name" /></el-form-item
          ><el-form-item v-if="form.kind === '实验室'" label="位置"
            ><el-input v-model="form.location" /></el-form-item
          ><el-form-item v-else label="说明"
            ><el-input
              v-model="form.description" /></el-form-item></template></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="repairUploading" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >

    <el-dialog v-model="fulfillmentDialog" :title="fulfillmentEdit ? '修正履约结果' : '登记履约结果'" width="480px"
      ><el-form label-position="top"
        ><el-form-item label="履约阶段" required
          ><el-select v-model="fulfillmentStage" placeholder="请选择履约阶段" style="width: 100%"
            ><el-option label="已归还（恢复 0.5 分）" value="RETURNED" /><el-option
              label="未履约（扣 3 至 5 分）"
              value="NOT_FULFILLED" /><el-option
              label="逾期未归还（扣 5 至 10 分）"
              value="OVERDUE_NOT_RETURNED" /><el-option v-if="fulfillmentEdit" label="撤销登记" value="NOT_STARTED" /></el-select></el-form-item
        ><el-form-item v-if="!['RETURNED', 'NOT_STARTED'].includes(fulfillmentStage)" label="登记原因" required
          ><el-input v-model="fulfillmentReason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item
        ><el-form-item v-if="!['RETURNED', 'NOT_STARTED'].includes(fulfillmentStage)" label="现场图片"
          ><el-upload :http-request="uploadFulfillmentImage" :limit="1" accept="image/jpeg,image/png,image/webp,image/gif" list-type="picture-card" :auto-upload="true" :file-list="fulfillmentImageUrl ? [{ name: '履约现场图片', url: fulfillmentImageUrl }] : []"><el-button>上传图片</el-button></el-upload></el-form-item
        ><a v-if="fulfillmentImageUrl" :href="fulfillmentImageUrl" target="_blank" rel="noreferrer">查看已上传图片</a>
        <div class="upload-tip">正常履约每次恢复 0.5 分；连续违约逐次增加扣分，未履约最高 5 分、逾期最高 10 分。仅登记人可修正或撤销。</div></el-form
      ><template #footer
        ><el-button @click="fulfillmentDialog = false">取消</el-button
        ><el-button type="primary" @click="saveFulfillment">确定登记</el-button></template
    ></el-dialog>

    <el-dialog v-model="showAutoApproval" title="自动审批规则" width="520px"
      ><el-form label-position="top"
        ><el-form-item label="自动审批范围"
          ><el-select v-model="autoScopeType" style="width: 100%"
            ><el-option v-if="sysadmin" label="全局" value="GLOBAL" /><el-option
              label="实验室"
              value="LAB" /><el-option label="设备分类" value="CATEGORY" /><el-option
              label="具体设备"
              value="EQUIPMENT" /></el-select></el-form-item
        ><el-form-item v-if="autoScopeType !== 'GLOBAL'" label="范围对象"
          ><el-select v-model="autoScopeId" style="width: 100%"
            ><el-option
              v-for="item in autoScopeOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select></el-form-item
        ><el-form-item label="自动处理方式"
          ><el-radio-group v-model="autoAction"
            ><el-radio-button label="APPROVE">自动通过</el-radio-button
            ><el-radio-button label="REJECT">自动拒绝</el-radio-button></el-radio-group
          ></el-form-item
        ><el-form-item label="生效时长"
          ><el-select v-model="autoMinutes" style="width: 100%"
            ><el-option label="1 小时" :value="60" /><el-option
              label="4 小时"
              :value="240" /><el-option
              label="8 小时"
              :value="480" /></el-select
        ></el-form-item>
        <div class="upload-tip">
          规则优先级为具体设备、设备分类、实验室、全局；同一范围的新规则覆盖旧规则。操作将记录在系统日志中。
        </div></el-form
      ><template #footer
        ><el-button @click="showAutoApproval = false">关闭</el-button
        ><el-button
          v-if="autoApprovals.some((x) => x.scope_type === autoScopeType && (autoScopeType === 'GLOBAL' || x.scope_id === autoScopeId) && x.active)"
          @click="setAutoApproval(0)"
          >关闭自动审批</el-button
        ><el-button type="primary" @click="setAutoApproval(autoMinutes)"
          >临时开启</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="detailDialog"
      :title="
        detail.contacts
          ? '联系人员'
          : active === '设备档案'
            ? '设备详情'
            : '维修工单详情'
      "
      width="680px"
      ><template v-if="detail.contacts"
        ><el-table :data="detail.contacts" empty-text="暂无联系人员"
          ><el-table-column prop="name" label="姓名" /><el-table-column
            prop="laboratory"
            label="实验室" /><el-table-column
            prop="identityNo"
            label="学号 / 工号" /><el-table-column
            prop="phone"
            label="电话" /><el-table-column
            prop="email"
            label="邮箱" /></el-table></template
      ><template v-else-if="active === '设备档案'"
        ><img
          v-if="detail.image_url"
          class="detail-image"
          :src="detail.image_url"
          alt="设备图片"
        /><el-descriptions :column="2" border
          ><el-descriptions-item label="编号">{{
            detail.code
          }}</el-descriptions-item
          ><el-descriptions-item label="名称">{{
            detail.name
          }}</el-descriptions-item
          ><el-descriptions-item label="分类">{{
            detail.category
          }}</el-descriptions-item
          ><el-descriptions-item label="实验室">{{
            detail.laboratory
          }}</el-descriptions-item
          ><el-descriptions-item label="品牌型号">{{
            detail.brand_model || "—"
          }}</el-descriptions-item
          ><el-descriptions-item label="责任人">{{
            detail.ownerName || "—"
          }}</el-descriptions-item
          ><el-descriptions-item label="存放位置">{{
            detail.location || "—"
          }}</el-descriptions-item
          ><el-descriptions-item label="购置日期">{{
            detail.purchase_date || "—"
          }}</el-descriptions-item
          ><el-descriptions-item label="使用规则" :span="2">{{
            detail.usage_rules || "—"
          }}</el-descriptions-item
          ><el-descriptions-item label="说明" :span="2">{{
            detail.description || "—"
          }}</el-descriptions-item></el-descriptions
        ></template
      ><template v-else
        ><img
          v-if="detail.ticket?.image_urls"
          class="detail-image"
          :src="detail.ticket.image_urls"
          alt="故障图片"
        /><el-descriptions :column="2" border
          ><el-descriptions-item label="设备">{{
            detail.ticket?.equipment_name
          }}</el-descriptions-item
          ><el-descriptions-item label="状态">{{
            detail.ticket?.status
          }}</el-descriptions-item
          ><el-descriptions-item label="故障类型">{{
            detail.ticket?.fault_type
          }}</el-descriptions-item
          ><el-descriptions-item label="费用">{{
            detail.ticket?.cost ?? "—"
          }}</el-descriptions-item
          ><el-descriptions-item label="故障描述" :span="2">{{
            detail.ticket?.description
          }}</el-descriptions-item
          ><el-descriptions-item label="维修结果" :span="2">{{
            detail.ticket?.result || "—"
          }}</el-descriptions-item></el-descriptions
        ><el-timeline class="repair-timeline"
          ><el-timeline-item
            v-for="record in detail.records"
            :key="record.id"
            :timestamp="formatDateTime(record.created_at)"
            >{{ record.status }} ·
            {{ record.note || "无备注" }}</el-timeline-item
          ></el-timeline
        ></template
      ></el-dialog
    >
  </div>
</template>
