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
  appealDialog = ref(false),
  appealRow = ref<any>(null),
  appealReason = ref(""),
  appealImageUrl = ref(""),
  appealFiles = ref<UploadFile[]>([]),
  auditRows = ref<any[]>([]),
  cancelDialog = ref(false),
  cancelRow = ref<any>(null),
  cancelInfo = ref<any>({}),
  cancelRuleDialog = ref(false),
  changeDialog = ref(false),
  changeRows = ref<any[]>([]);
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
const cancelRules = [
  { window: "距预约开始 48 小时以上", penalty: "1 分" },
  { window: "距预约开始 24 小时以上、不足 48 小时", penalty: "2 分" },
  { window: "距预约开始不足 24 小时", penalty: "4 分" },
];
const equipmentChangeFields = [
  ["code", "设备编号"],
  ["name", "设备名称"],
  ["categoryId", "设备分类"],
  ["labId", "所属实验室"],
  ["brandModel", "品牌型号"],
  ["location", "存放位置"],
  ["description", "设备说明"],
  ["usageRules", "使用规则"],
  ["imageUrl", "图片地址"],
  ["purchaseDate", "购置日期"],
  ["status", "状态"],
] as const;
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
  manager = computed(() => admin.value || viewer.value),
  manageableLabIds = computed(() =>
    Array.isArray(auth.user?.labIds)
      ? auth.user.labIds.map((x: any) => Number(x))
      : [],
  ),
  editableLabs = computed(() =>
    sysadmin.value
      ? labs.value
      : labs.value.filter((x: any) =>
          manageableLabIds.value.includes(Number(x.id)),
        ),
  ),
  manageableLabNames = computed(() =>
    labs.value
      .filter((x: any) => manageableLabIds.value.includes(Number(x.id)))
      .map((x: any) => x.name),
  );
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
      if (admin.value) labs.value = await api.get("/labs");
    } else if (props.active === "操作审计") {
      auditRows.value = await api.get("/reservations/audit");
      rows.value = auditRows.value;
    } else if (props.active === "预约管理") {
      rows.value = manager.value
        ? await api.get("/reservations/pending")
        : await api.get("/reservations");
      if (admin.value) {
        autoApprovals.value = await api.get("/reservations/auto-approval");
        const [labData, categoryData, equipmentData]: any[] = await Promise.all([
          api.get("/labs"),
          api.get("/categories"),
          api.get("/equipment/options"),
        ]);
        labs.value = labData;
        cats.value = categoryData;
        equipment.value = equipmentData;
      }
    } else if (props.active === "履约申诉")
      appeals.value = await api.get("/reservations/appeals");
    else if (props.active === "报修工单")
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
  let message = "已保存";
  let creditApprovalNotice: string | null = null;
  if (props.active === "设备档案") {
    const result: any = editId.value
      ? await api.put(`/equipment/${editId.value}`, form.value)
      : await api.post("/equipment", form.value);
    if (result?.pendingApproval)
      message = "修改已提交，等待系统管理员审批后生效";
  } else if (props.active === "预约管理") {
    if (
      !form.value.equipmentId ||
      !form.value.startsAt ||
      !form.value.endsAt ||
      !form.value.purpose?.trim()
    )
      return ElMessage.warning("请完整填写设备、预约时间和使用目的");
    const selected = equipment.value.find(
      (x) => Number(x.id) === Number(form.value.equipmentId),
    );
    const startsAt = localInputToUtc(form.value.startsAt);
    const endsAt = localInputToUtc(form.value.endsAt);
    const result: any = await api.post("/reservations", {
      ...form.value,
      startsAt,
      endsAt,
    });
    if (result?.status === "PENDING")
      message = "预约已提交，等待实验室管理员审批";
    else if (result?.status === "REJECTED") message = "预约已被系统自动拒绝";
    else message = "预约已自动通过";
    if (result?.creditApproved) {
      creditApprovalNotice = [
        `设备：${escapeHtml(selected?.name || "所选设备")}`,
        `使用时间：${escapeHtml(formatDateTime(startsAt))} 至 ${escapeHtml(formatDateTime(endsAt))}`,
        `当前信誉积分：${escapeHtml(String(result.creditScore ?? ""))} 分（已达到 90 分），系统已自动通过本次预约。管理员如认为不应通过，可以撤销该自动通过。`,
      ].join("<br/>");
    }
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
  ElMessage.success(message);
  await load();
  if (creditApprovalNotice) {
    await ElMessageBox.alert(creditApprovalNotice, "预约已自动通过", {
      dangerouslyUseHTMLString: true,
      confirmButtonText: "知道了",
    });
  }
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
  const esc = (s: string | undefined | null) =>
    String(s ?? "").replace(/[&<>"']/g, (c) => {
      const map: Record<string, string> = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      };
      return map[c];
    });
  const { value } = await ElMessageBox.prompt(
    `预约人：${row.user_name}<br/>电话：${row.user_phone || "未填写"}<br/>邮箱：${row.user_email || "未填写"}<br/>填写审核意见`,
    "预约审核",
    {
      dangerouslyUseHTMLString: true,
    }
  );
  await api.post(`/reservations/${row.id}/review`, {
    decision,
    comment: value,
  });
  await load();
}
async function cancel(row: any) {
  const preview: any = await api.get(`/reservations/${row.id}/cancel-preview`);
  cancelRow.value = row;
  cancelInfo.value = preview;
  cancelDialog.value = true;
}
async function confirmCancel() {
  const result: any = await api.post(`/reservations/${cancelRow.value.id}/cancel`);
  cancelDialog.value = false;
  const penalty = Number(result?.penalty || 0);
  ElMessage.success(
    penalty > 0
      ? `预约已取消，已扣除 ${penalty} 分信誉积分`
      : "预约已取消，未扣除信誉积分",
  );
  await load();
}
async function revokeApproval(row: any) {
  await ElMessageBox.confirm(
    "撤销后该预约将回到待审批状态，需要重新审核，学生也会收到通知。预约开始前 2 小时内不能撤销通过。",
    "撤销通过",
    { type: "warning", confirmButtonText: "撤销通过", cancelButtonText: "返回" },
  );
  await api.post(`/reservations/${row.id}/revoke-approval`);
  ElMessage.success("已撤销通过，预约回到待审批状态");
  await load();
}
async function openChanges() {
  const [labData, categoryData]: any[] = await Promise.all([
    api.get("/labs"),
    api.get("/categories"),
  ]);
  labs.value = labData;
  cats.value = categoryData;
  await refreshChanges();
  changeDialog.value = true;
}
async function refreshChanges() {
  const requests: any[] = await api.get("/equipment/change-requests");
  changeRows.value = requests.map((row: any) => ({
    ...row,
    diff: buildChangeDiff(row),
  }));
}
function equipmentStatusLabel(status: string) {
  return (
    ({ AVAILABLE: "可预约", MAINTENANCE: "维修中", DISABLED: "停用" } as Record<
      string,
      string
    >)[status] ||
    status ||
    "—"
  );
}
function changeValue(field: string, value: any) {
  if (field === "status") return equipmentStatusLabel(value);
  if (field === "categoryId")
    return (
      cats.value.find((x) => Number(x.id) === Number(value))?.name ??
      (value ?? "—")
    );
  if (field === "labId")
    return (
      labs.value.find((x) => Number(x.id) === Number(value))?.name ??
      (value ?? "—")
    );
  return value === null || value === undefined || value === ""
    ? "—"
    : String(value);
}
function buildChangeDiff(row: any) {
  let payload: any = {};
  try {
    payload =
      typeof row.payload === "string" ? JSON.parse(row.payload) : row.payload || {};
  } catch {
    payload = {};
  }
  const current: Record<string, any> = {
    code: row.currentCode,
    name: row.currentName,
    categoryId: row.currentCategoryId,
    labId: row.currentLabId,
    brandModel: row.currentBrandModel,
    location: row.currentLocation,
    description: row.currentDescription,
    usageRules: row.currentUsageRules,
    imageUrl: row.currentImageUrl,
    purchaseDate: row.currentPurchaseDate,
    status: row.currentStatus,
  };
  return equipmentChangeFields
    .filter(([field]) => String(payload[field] ?? "") !== String(current[field] ?? ""))
    .map(([field, label]) => ({
      label,
      from: changeValue(field, current[field]),
      to: changeValue(field, payload[field]),
    }));
}
async function reviewChange(row: any, decision: string) {
  const { value } = await ElMessageBox.prompt(
    decision === "APPROVED" ? "审批通过后变更立即生效。" : "驳回后变更不会生效。",
    decision === "APPROVED" ? "通过设备变更" : "驳回设备变更",
    { inputType: "textarea", inputPlaceholder: "审批意见（可选）" },
  );
  await api.post(`/equipment/change-requests/${row.id}/review`, {
    decision,
    comment: value || "",
  });
  ElMessage.success(decision === "APPROVED" ? "变更已通过并生效" : "变更已驳回");
  await refreshChanges();
  await load();
}
async function submitAppeal(row: any) {
  appealRow.value = row;
  appealReason.value = "";
  appealImageUrl.value = "";
  appealFiles.value = [];
  appealDialog.value = true;
}
async function saveAppeal() {
  if (!appealReason.value.trim()) return ElMessage.warning("请填写申诉理由");
  await api.post(`/reservations/${appealRow.value.id}/appeal`, {
    reason: appealReason.value,
    imageUrl: appealImageUrl.value,
  });
  appealDialog.value = false;
  ElMessage.success("申诉已提交");
  await load();
}
async function uploadAppealImage(options: UploadRequestOptions) {
  try {
    const result: any = await api.upload("/uploads/repair-image", options.file);
    appealImageUrl.value = result.url;
    options.onSuccess(result);
  } catch (error) { options.onError(error as any); }
}
function removeAppealImage(_file: UploadFile, files: UploadFiles) {
  appealFiles.value = files;
  if (!files.length) appealImageUrl.value = "";
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
  const labels: Record<string, string> = { APPROVAL_TIMEOUT: "超过审批时限，系统自动驳回", ADMIN_REJECTED: "管理员审核意见："+row.review_comment || "管理员驳回", AUTO_REJECTED: row.review_comment || "命中自动审批拒绝规则" };
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
        OVERDUE_NOT_RETURNED: "danger",
        NOT_FULFILLED: "danger",
        CANCELLED: "info",
        OPEN: "warning",
        PROCESSING: "primary",
        WAIT_ACCEPTANCE: "warning",
        COMPLETED: "success",
      } as any
    )[status] || "info"
  );
}
function columnMinWidth(name: string) {
  if (name === "status") return props.active === "预约管理" && admin.value ? 190 : 125;
  if (name === "purpose") return 110;
  if (name === "equipment_name") return 115;
  return 125;
}
function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (c) => {
    const map: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    };
    return map[c];
  });
}
function displayValue(row: any, field: string) {
  if (field === "reject_reason") return rejectReasonLabel(row[field]);
  if (field === "fulfillment_status") return fulfillmentLabel(row[field]);
  if (field === "role") return roleLabel(row[field]);
  if (field === "personType") return personTypeLabel(row[field]);
  if (field === "active") return row[field] ? "是" : "否";
  if (field === "action") return operationLabel(row[field]);
  if (field === "target_type") return targetTypeLabel(row[field]);
  if (field === "detail") return formatAuditDetail(row[field]);
  return dateTimeFields.has(field)
    ? formatDateTime(row[field])
    : (row[field] ?? "—");
}
function rejectReasonLabel(reason: string) {
  return ({ APPROVAL_TIMEOUT: "超时拒绝", ADMIN_REJECTED: "管理员拒绝", AUTO_REJECTED: "自动审批拒绝" } as Record<string, string>)[reason] || (reason ? reason : "—");
}
function fulfillmentLabel(stage: string) {
  return ({ NOT_STARTED: "待登记", OVERDUE_NOT_RETURNED: "逾期未归还", NOT_FULFILLED: "未履约", RETURNED: "已归还", COMPLETED: "已完成" } as Record<string, string>)[stage] || "—";
}
function personTypeLabel(type: string) {
  return ({ STUDENT: "学生", STAFF: "教职工" } as Record<string, string>)[type] || type || "—";
}
function operationLabel(action: string) {
  return ({
    EQUIPMENT_CREATE: "新增设备",
    EQUIPMENT_UPDATE: "修改设备档案",
    EQUIPMENT_CHANGE_SUBMIT: "提交设备变更",
    EQUIPMENT_CHANGE_REVIEW: "审批设备变更",
    RESERVATION_REVIEW: "审核预约",
    RESERVATION_AUTO_REJECT: "自动驳回预约",
    RESERVATION_APPEAL: "提交履约申诉",
    RESERVATION_APPEAL_REVIEW: "审核履约申诉",
    RESERVATION_FULFILLMENT: "登记履约结果",
    RESERVATION_FULFILLMENT_CORRECTION: "修正履约结果",
    RESERVATION_REVOKE_APPROVAL: "撤销通过",
    RESERVATION_REVOKE_AUTO_APPROVAL: "撤销自动通过",
    REPAIR_PROCESS: "处理维修工单",
  } as Record<string, string>)[action] || action || "—";
}
function targetTypeLabel(type: string) {
  return ({ reservation: "预约", reservation_appeal: "履约申诉", equipment: "设备", repair_ticket: "维修工单", USER: "用户" } as Record<string, string>)[type] || type || "—";
}
const auditKeyLabels: Record<string, string> = {
  decision: "结果",
  status: "状态",
  stage: "履约阶段",
  reason: "原因",
  imageUrl: "图片",
  from: "原状态",
  to: "新状态",
  comment: "意见",
  code: "设备编号",
  requestId: "申请编号",
  note: "备注",
  username: "账号",
  reviewer: "操作人",
};
function auditValueLabel(value: string) {
  return ({
    APPROVED: "通过",
    REJECTED: "驳回",
    AVAILABLE: "可预约",
    MAINTENANCE: "维修中",
    DISABLED: "停用",
    NOT_FULFILLED: "未履约",
    OVERDUE_NOT_RETURNED: "逾期未归还",
    RETURNED: "已归还",
    NOT_STARTED: "未登记",
    PENDING: "待审批",
    OPEN: "待处理",
    PROCESSING: "处理中",
    WAIT_ACCEPTANCE: "待验收",
    COMPLETED: "已完成",
  } as Record<string, string>)[value] || value;
}
function formatAuditDetail(detail: any) {
  if (detail === null || detail === undefined || detail === "") return "—";
  let data = detail;
  if (typeof detail === "string") {
    try {
      data = JSON.parse(detail);
    } catch {
      return detail;
    }
  }
  if (typeof data !== "object") return String(data);
  return Object.entries(data)
    .map(
      ([key, value]) =>
        `${auditKeyLabels[key] || key}：${
          typeof value === "string" ? auditValueLabel(value) : String(value)
        }`,
    )
    .join("；");
}
function statusLabel(status: string, row?: any) {
  if (row?.fulfillment_status && row.fulfillment_status !== "NOT_STARTED" && row.fulfillment_status !== "RETURNED") {
    return fulfillmentLabel(row.fulfillment_status);
  }
  const labels: Record<string, string> = {
    PENDING: "待审批",
    APPROVED: manager.value ? "已通过" : "待使用",
    REJECTED: "已驳回",
    CANCELLED: "已取消",
    COMPLETED: "已完成",
    AVAILABLE: "可预约",
    MAINTENANCE: "维修中",
    DISABLED: "停用",
    OPEN: "待处理",
    PROCESSING: "处理中",
    WAIT_ACCEPTANCE: "待验收",
    NOT_STARTED: "待登记",
    RETURNED: "已归还",
    NOT_FULFILLED: "未履约",
    OVERDUE_NOT_RETURNED: "逾期未归还",
  };
  return labels[status] || status || "—";
}
function reservationEnded(row: any) {
  if (!row.ends_at) return false;
  const end = new Date(`${String(row.ends_at).replace(" ", "T")}Z`);
  return !Number.isNaN(end.getTime()) && end.getTime() <= Date.now();
}
function canRevoke(row: any) {
  if (!row?.starts_at) return false;
  const start = new Date(`${String(row.starts_at).replace(" ", "T")}Z`);
  return (
    !Number.isNaN(start.getTime()) &&
    Date.now() < start.getTime() - 2 * 60 * 60 * 1000
  );
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
function quickBookingDays() {
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() + index + 1);
    return date;
  });
}
function quickBookingLabel(date: Date) {
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", weekday: "short" }).format(date);
}
function quickBookingShortcuts() {
  return quickBookingDays().map(date => ({
    text: quickBookingLabel(date),
    value: () => {
      applyQuickBooking(date);
      return new Date(form.value.startsAt);
    },
  }));
}
function localInputAtLabOpening(date: Date, time: string) {
  const [hour, minute] = String(time || "08:00:00").split(":").map(Number);
  const instant = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), hour - 8, minute));
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(instant);
  const part = (type: string) => parts.find(item => item.type === type)?.value || "00";
  return `${part("year")}-${part("month")}-${part("day")}T${part("hour")}:${part("minute")}:00`;
}
function applyQuickBooking(date: Date) {
  const selected = equipment.value.find(item => Number(item.id) === Number(form.value.equipmentId));
  if (!selected) return ElMessage.warning("请先选择设备");
  const start = localInputAtLabOpening(date, selected.open_time);
  const endDate = new Date(new Date(start).getTime() + 60 * 60 * 1000);
  const pad = (value: number) => String(value).padStart(2, "0");
  form.value.startsAt = start;
  form.value.endsAt = `${endDate.getFullYear()}-${pad(endDate.getMonth() + 1)}-${pad(endDate.getDate())}T${pad(endDate.getHours())}:${pad(endDate.getMinutes())}:00`;
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
            ><el-table-column label="状态"
              ><template #default="s"
                ><el-tag :type="statusType(s.row.status)">{{
                  statusLabel(s.row.status, s.row)
                }}</el-tag></template
              ></el-table-column
            ></el-table>
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
    <template v-else-if="active === '履约申诉'">
      <div class="page-heading">
        <div>
          <div class="eyebrow">APPEALS</div>
          <h1>履约申诉</h1>
          <p>审核学生提交的履约申诉 · 时间按{{ currentTimeZone }}显示</p>
        </div>
        <el-button @click="load">刷新</el-button>
      </div>
      <div v-if="admin" class="notice-strip">
        申诉通过后返还本次登记扣除的全部信誉积分，并把履约状态改为“已完成”；驳回则保留原来的履约结果。
      </div>
      <div v-else class="notice-strip">
        只读管理员可以查看待处理申诉，但不能审核。
      </div>
      <div class="data-section">
        <el-table
          v-loading="busy"
          :data="appeals"
          stripe
          empty-text="暂无待处理申诉"
        >
          <el-table-column prop="applicant_name" label="申诉人" min-width="100" />
          <el-table-column label="违约情况" min-width="220"
            ><template #default="s"
              >{{ fulfillmentLabel(s.row.fulfillment_status) }}：{{
                s.row.fulfillment_reason || "—"
              }}</template
            ></el-table-column
          >
          <el-table-column prop="equipment_name" label="设备" min-width="140" />
          <el-table-column label="证据图片" width="110"
            ><template #default="s"
              ><a
                v-if="s.row.image_url"
                :href="s.row.image_url"
                target="_blank"
                rel="noreferrer"
                >查看图片</a
              ><span v-else>未上传</span></template
            ></el-table-column
          >
          <el-table-column prop="reason" label="申诉理由" min-width="260" />
          <el-table-column label="提交时间" width="150"
            ><template #default="s">{{
              formatDateTime(s.row.created_at)
            }}</template></el-table-column
          >
          <el-table-column v-if="admin" label="操作" width="150" fixed="right">
            <template #default="s">
              <el-button
                link
                type="success"
                @click="reviewAppeal(s.row, 'APPROVED')"
                >通过</el-button
              >
              <el-button
                link
                type="danger"
                @click="reviewAppeal(s.row, 'REJECTED')"
                >驳回</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
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
            v-if="active === '设备档案' && sysadmin"
            @click="openChanges"
            >变更审批</el-button
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
        预约开始前 2 小时仍未处理时，系统将自动驳回；已通过的预约也必须在开始前 2 小时以前撤销通过。请在截止时间前完成审批。
      </div>
      <div v-if="active === '设备档案' && auth.user?.role === 'LAB_ADMIN'" class="notice-strip">
        您只能修改自己负责实验室（{{ manageableLabNames.join("、") || "暂未分配" }}）的设备档案，其他实验室的设备仅供查看；责任人只能由系统管理员修改。
      </div>
      <div class="data-section">
        <el-table v-loading="busy" :data="rows" stripe empty-text="暂无数据"
          ><el-table-column
            v-for="column in columns"
            :key="column[0]"
            :prop="column[0]"
            :label="column[1]"
            :min-width="columnMinWidth(column[0])"
            ><template #default="scope"
              ><template v-if="column[0] === 'status'"
                ><div class="status-cell"
                  ><el-tag
                    :type="statusType(scope.row.fulfillment_status && scope.row.fulfillment_status !== 'NOT_STARTED' && scope.row.fulfillment_status !== 'RETURNED' ? scope.row.fulfillment_status : scope.row[column[0]])"
                    >{{ statusLabel(scope.row[column[0]], scope.row) }}</el-tag
                  ><el-tag
                    v-if="active === '预约管理' && admin && Number(scope.row.auto_credit_approved) === 1"
                    class="auto-source-tag"
                    size="small"
                    type="warning"
                    >信誉自动通过</el-tag
                  ></div
                ></template
              ><span v-else-if="column[0] === 'enabled'">{{
                scope.row[column[0]] ? "是" : "否"
              }}</span
              ><span v-else>{{
                displayValue(scope.row, column[0])
              }}</span></template
            ></el-table-column
          ><el-table-column
            v-if="active === '设备档案' && manager"
            label="变更审批"
            min-width="110"
            ><template #default="s"
              ><el-tag
                v-if="Number(s.row.changePending) === 1"
                size="small"
                type="warning"
                >待审批</el-tag
              ><span v-else>—</span></template
            ></el-table-column
          ><el-table-column
            v-if="active === '预约管理' && admin"
            label="审批截止时间"
            min-width="140"
            ><template #default="s">{{
              cutoff(s.row)
            }}</template></el-table-column
          ><el-table-column
            v-if="active === '预约管理' && admin"
            label="预约人联系方式"
            min-width="180"
            ><template #default="s"
              ><span
                >{{ s.row.user_name }} · {{ s.row.user_phone || "无电话" }} ·
                {{ s.row.user_email || "无邮箱" }}</span
              ></template
            ></el-table-column
          ><el-table-column
            v-if="active !== '操作审计'"
            label="操作"
            fixed="right"
            width="220"
            ><template #default="scope"
              ><template v-if="active === '预约管理'"
                ><el-button
                  v-if="scope.row.status === 'REJECTED'"
                  link
                  type="warning"
                  @click="viewRejectReason(scope.row)"
                  >查看拒绝原因</el-button
                ><el-button
                  v-if="admin && scope.row.status === 'APPROVED' && canRevoke(scope.row)"
                  link
                  type="warning"
                  @click="revokeApproval(scope.row)"
                  >撤销通过</el-button
                ><el-tooltip
                  v-if="admin && scope.row.status === 'APPROVED' && !canRevoke(scope.row)"
                  content="距离预约开始不足 2 小时，不能撤销通过"
                  placement="top"
                  ><span
                    ><el-button link disabled>撤销通过</el-button></span
                  ></el-tooltip
                ><el-button
                  v-if="admin && scope.row.fulfillment_operator_id != null && Number(scope.row.fulfillment_operator_id) === Number(auth.user?.id) && scope.row.fulfillment_status !== 'NOT_STARTED' && scope.row.appeal_status !== 'APPROVED'"
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
                  v-if="admin && ['APPROVED', 'COMPLETED'].includes(scope.row.status) && scope.row.fulfillment_status === 'NOT_STARTED' && reservationEnded(scope.row)"
                  link
                  type="primary"
                  @click="markFulfillment(scope.row)"
                  >登记履约</el-button
                ><el-button
                  v-if="!manager && ['OVERDUE_NOT_RETURNED', 'NOT_FULFILLED'].includes(scope.row.fulfillment_status) && !scope.row.appeal_status"
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
                  (active === '设备档案' &&
                    admin &&
                    (sysadmin ||
                      manageableLabIds.includes(Number(scope.row.labId)))) ||
                  (active === '用户管理' && sysadmin) ||
                  (active === '校园身份' && sysadmin) ||
                  (active === '实验室与分类' && sysadmin)
                "
                link
                type="primary"
                @click="open(scope.row)"
                >编辑</el-button
              ><el-tooltip
                v-if="
                  active === '设备档案' &&
                  auth.user?.role === 'LAB_ADMIN' &&
                  !manageableLabIds.includes(Number(scope.row.labId))
                "
                content="只能编辑自己负责实验室的设备"
                placement="top"
                ><span class="action-gap"
                  ><el-button link disabled>编辑</el-button></span
                ></el-tooltip
              ></template
            ></el-table-column
          ></el-table
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
                  v-for="x in editableLabs"
                  :key="x.id"
                  :label="x.name"
                  :value="x.id" /></el-select></el-form-item
            ><el-form-item label="品牌型号"
              ><el-input v-model="form.brandModel" /></el-form-item
            ><el-form-item label="存放位置"
              ><el-input v-model="form.location" /></el-form-item
            ><el-form-item label="责任人"
              ><el-select v-model="form.ownerId" clearable :disabled="!sysadmin"
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
          <div v-if="!sysadmin" class="upload-tip">
            责任人只能由系统管理员修改；实验室管理员提交的其他档案修改需系统管理员审批后才会生效。
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
              popper-class="reservation-start-picker"
              value-format="YYYY-MM-DDTHH:mm:ss"
              format="YYYY年MM月DD日 HH:mm"
              :show-now="false"
              :shortcuts="quickBookingShortcuts()"
              :disabled-date="disableBookingDate" /></el-form-item
          ><el-form-item :label="`结束时间（${currentTimeZone}）`" required
            ><el-date-picker
              v-model="form.endsAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              format="YYYY年MM月DD日 HH:mm"
              :show-now="false"
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

    <el-dialog v-model="appealDialog" title="预约履约申诉" width="560px">
      <template v-if="appealRow">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="履约结果">{{ fulfillmentLabel(appealRow.fulfillment_status) }}</el-descriptions-item>
          <el-descriptions-item label="登记原因">{{ appealRow.fulfillment_reason || "管理员未填写具体原因" }}</el-descriptions-item>
          <el-descriptions-item label="登记图片"><a v-if="appealRow.fulfillment_image_url" :href="appealRow.fulfillment_image_url" target="_blank" rel="noreferrer">查看管理员登记图片</a><span v-else>未提供</span></el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" class="appeal-form">
          <el-form-item label="申诉理由" required><el-input v-model="appealReason" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="说明实际履约情况" /></el-form-item>
          <el-form-item label="申诉证据图片"><el-upload v-model:file-list="appealFiles" list-type="picture-card" accept="image/jpeg,image/png,image/webp,image/gif" :limit="1" :http-request="uploadAppealImage" :on-remove="removeAppealImage"><span>上传图片</span><template #tip><div class="upload-tip">如有相关凭证，建议上传清晰、可靠的证据图片；图片为选填，支持 JPG、PNG、WEBP、GIF，最大 5MB。</div></template></el-upload></el-form-item>
        </el-form>
      </template>
      <template #footer><el-button @click="appealDialog = false">取消</el-button><el-button type="primary" @click="saveAppeal">提交申诉</el-button></template>
    </el-dialog>

    <el-dialog v-model="cancelDialog" title="取消预约" width="500px">
      <template v-if="cancelRow && cancelInfo">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="设备">{{ cancelRow.equipment_name }}</el-descriptions-item>
          <el-descriptions-item label="使用时间">{{ formatDateTime(cancelRow.starts_at) }} 至 {{ formatDateTime(cancelRow.ends_at) }}</el-descriptions-item>
          <el-descriptions-item label="距开始">{{ cancelInfo.hoursUntilStart }} 小时</el-descriptions-item>
          <el-descriptions-item label="信誉积分影响">
            <span v-if="Number(cancelInfo.penalty) > 0" class="cancel-penalty">将扣除 {{ cancelInfo.penalty }} 分</span>
            <span v-else>暂不扣除（预约尚未通过审批）</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="upload-tip cancel-tip">取消后无法恢复；扣分按当前时间计算，确认取消后立即生效。</div>
      </template>
      <template #footer>
        <el-button plain @click="cancelRuleDialog = true">退约规则</el-button>
        <el-button @click="cancelDialog = false">再想想</el-button>
        <el-button type="danger" @click="confirmCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelRuleDialog" title="退约规则" width="500px">
      <el-table :data="cancelRules" border>
        <el-table-column prop="window" label="取消时间" />
        <el-table-column prop="penalty" label="扣除信誉积分" width="130" />
      </el-table>
      <div class="upload-tip cancel-tip">规则只对已通过审批的预约生效，待审批预约取消不扣分；信誉积分最低扣至 0 分。申诉通过后可返还相应积分。</div>
      <template #footer><el-button type="primary" @click="cancelRuleDialog = false">知道了</el-button></template>
    </el-dialog>

    <el-dialog v-model="changeDialog" title="设备档案变更审批" width="900px">
      <el-table :data="changeRows" empty-text="暂无待审批的设备变更" stripe>
        <el-table-column prop="currentCode" label="设备编号" width="110" />
        <el-table-column label="设备" min-width="150"
          ><template #default="s"
            >{{ s.row.currentName }}（{{ s.row.laboratory }}）</template
          ></el-table-column
        >
        <el-table-column prop="applicant_name" label="申请人" width="100" />
        <el-table-column label="变更内容" min-width="300"
          ><template #default="s">
            <div v-for="item in s.row.diff" :key="item.label" class="change-line">
              <b>{{ item.label }}</b>：{{ item.from }} → {{ item.to }}
            </div>
            <span v-if="!s.row.diff?.length">无字段变化</span>
          </template></el-table-column
        >
        <el-table-column label="提交时间" width="150"
          ><template #default="s">{{
            formatDateTime(s.row.created_at)
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="s">
            <el-button link type="success" @click="reviewChange(s.row, 'APPROVED')">通过</el-button>
            <el-button link type="danger" @click="reviewChange(s.row, 'REJECTED')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="upload-tip cancel-tip">实验室管理员提交的变更需在这里审批，通过后立即写入设备档案；责任人只能由系统管理员直接修改。</div>
      <template #footer><el-button @click="changeDialog = false">关闭</el-button></template>
    </el-dialog>

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
            statusLabel(detail.ticket?.status)
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
            >{{ statusLabel(record.status) }} ·
            {{ record.note || "无备注" }}</el-timeline-item
          ></el-timeline
        ></template
      ></el-dialog
    >
  </div>
</template>
