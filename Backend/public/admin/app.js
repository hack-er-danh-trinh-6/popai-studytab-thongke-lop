"use strict";

const API = "/api/teacher/v1";
const TIME_ZONE = (() => {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Ho_Chi_Minh"; } catch { return "Asia/Ho_Chi_Minh"; }
})();

const store = {
  get(key) { try { return sessionStorage.getItem(key); } catch { return null; } },
  set(key, value) { try { sessionStorage.setItem(key, value); } catch { /* bỏ qua */ } },
  remove(key) { try { sessionStorage.removeItem(key); } catch { /* bỏ qua */ } }
};

const state = {
  gateToken: store.get("pop_gate"),
  gateExpiresAt: Number(store.get("pop_gate_exp") || 0),
  token: store.get("pop_token"),
  user: null,
  classes: [],
  classId: store.get("pop_class"),
  tab: "requests",
  days: 7,
  requests: [],
  students: null,
  analytics: null,
  surveyAnalytics: null,
  usage: null,
  selectedStudentId: null,
  studentDetail: null,
  history: []
};

// ---------- tiện ích DOM (chỉ dùng textContent để tránh XSS) ----------
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null) continue;
    if (key.startsWith("aria-")) { el.setAttribute(key, String(value)); continue; }
    if (value === false) continue;
    // CSP chặn thuộc tính style nội tuyến; gán qua CSSOM thì được phép.
    if (key === "style") el.style.cssText = value;
    else if (key === "class") el.className = value;
    else if (key === "text") el.textContent = value;
    else if (key.startsWith("on") && typeof value === "function") el.addEventListener(key.slice(2), value);
    else if (key === "value") el.value = value;
    else if (key === "checked" || key === "disabled" || key === "selected") el[key] = Boolean(value);
    else el.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat()) {
    if (child === undefined || child === null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

const root = document.getElementById("app");
function mount(...nodes) { root.replaceChildren(...nodes); }

let toastTimer;
function toast(message, bad = false) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.className = `toast show${bad ? " bad" : ""}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = "toast"; }, 3500);
}

const fmtDateTime = (iso) => iso ? new Date(iso).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—";
function timeAgo(iso) {
  if (!iso) return "Chưa có";
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return "Vừa xong";
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  return `${Math.round(hours / 24)} ngày trước`;
}
const fmtMinutes = (m) => m >= 60 ? `${Math.floor(m / 60)} giờ ${m % 60} phút` : `${m} phút`;

// ---------- gọi API ----------
class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

async function api(path, { method = "GET", body, gate = false, auth = true } = {}) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth && state.token) headers.Authorization = `Bearer ${state.token}`;
  if (gate && state.gateToken) headers["X-Gate-Token"] = state.gateToken;
  let response;
  try {
    response = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError("Không thể kết nối máy chủ. Vui lòng thử lại.", 0);
  }
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(data.message || "Có lỗi xảy ra. Vui lòng thử lại.", response.status);
  return data;
}

function clearSession({ keepGate = false } = {}) {
  state.token = null; state.user = null; state.classes = []; state.students = null; state.analytics = null; state.surveyAnalytics = null; state.usage = null;
  store.remove("pop_token");
  if (!keepGate) {
    state.gateToken = null; state.gateExpiresAt = 0;
    store.remove("pop_gate"); store.remove("pop_gate_exp");
  }
}

/** Xử lý lỗi dùng chung; trả true nếu đã chuyển màn hình. */
function handleFatal(error) {
  if (error.status === 401) {
    clearSession({ keepGate: true });
    toast("Phiên đăng nhập đã hết hạn.", true);
    start();
    return true;
  }
  return false;
}

const gateValid = () => Boolean(state.gateToken) && state.gateExpiresAt > Date.now();

// ---------- màn hình 1: mật khẩu admin ----------
function renderGate(message) {
  const input = h("input", { type: "password", id: "gate-password", autocomplete: "current-password", placeholder: "Mật khẩu admin" });
  const error = h("div", { class: "error", role: "alert", hidden: !message, text: message || "" });
  const button = h("button", { class: "full", type: "button", text: "Vào trang giáo viên" });
  async function submit() {
    if (!input.value) return;
    button.disabled = true;
    try {
      const data = await api("/gate", { method: "POST", body: { password: input.value }, auth: false });
      state.gateToken = data.gateToken;
      state.gateExpiresAt = new Date(data.expiresAt).getTime();
      store.set("pop_gate", state.gateToken);
      store.set("pop_gate_exp", String(state.gateExpiresAt));
      renderAuth("login");
    } catch (e) {
      error.hidden = false; error.textContent = e.message; button.disabled = false; input.select();
    }
  }
  button.addEventListener("click", submit);
  input.addEventListener("keydown", (e) => { if (e.key === "Enter") submit(); });
  mount(h("div", { class: "center-wrap" }, h("div", { class: "card auth-card" },
    h("div", { class: "brand" }, h("div", { class: "logo", text: "🍿" }), h("h1", { text: "POP AI · Giáo viên" }),
      h("p", { class: "muted", text: "Nhập mật khẩu admin để vào trang giáo viên." })),
    h("label", { for: "gate-password", text: "Mật khẩu admin" }), input, error, button)));
  input.focus();
}

// ---------- màn hình 2: đăng nhập / đăng ký giáo viên ----------
function renderAuth(mode, message) {
  if (!gateValid()) return renderGate("Phiên nhập mật khẩu admin đã hết hạn. Vui lòng nhập lại.");
  const fields = {
    fullName: h("input", { id: "f-name", autocomplete: "name", maxlength: 100, placeholder: "Nguyễn Văn A" }),
    email: h("input", { id: "f-email", type: "email", autocomplete: "email", placeholder: "giaovien@truong.edu.vn" }),
    password: h("input", { id: "f-pass", type: "password", autocomplete: mode === "login" ? "current-password" : "new-password" }),
    school: h("input", { id: "f-school", maxlength: 150, placeholder: "THPT Nguyễn Huệ" }),
    className: h("input", { id: "f-class", maxlength: 60, placeholder: "10A1" })
  };
  const error = h("div", { class: "error", role: "alert", hidden: !message, text: message || "" });
  const submit = h("button", { class: "full", type: "button", text: mode === "login" ? "Đăng nhập" : "Tạo tài khoản giáo viên" });

  async function run() {
    error.hidden = true;
    submit.disabled = true;
    try {
      const body = mode === "login"
        ? { email: fields.email.value, password: fields.password.value }
        : {
          email: fields.email.value, password: fields.password.value, fullName: fields.fullName.value,
          school: fields.school.value, className: fields.className.value
        };
      const data = await api(mode === "login" ? "/auth/login" : "/auth/register", { method: "POST", body, gate: true, auth: false });
      state.token = data.accessToken;
      store.set("pop_token", state.token);
      state.user = data.user;
      await loadDashboard();
    } catch (e) {
      if (e.status === 403 && /admin/i.test(e.message)) { clearSession(); return renderGate(e.message); }
      error.hidden = false; error.textContent = e.message; submit.disabled = false;
    }
  }
  submit.addEventListener("click", run);
  root.addEventListener("keydown", function onKey(e) {
    if (e.key === "Enter" && e.target instanceof HTMLInputElement && root.contains(submit)) run();
    else if (!root.contains(submit)) root.removeEventListener("keydown", onKey);
  });

  const field = (label, input) => [h("label", { for: input.id, text: label }), input];
  mount(h("div", { class: "center-wrap" }, h("div", { class: "card auth-card" },
    h("div", { class: "brand" }, h("div", { class: "logo", text: "🍿" }), h("h1", { text: "Trang giáo viên" })),
    h("div", { class: "tabs-auth" },
      h("button", { type: "button", class: mode === "login" ? "" : "secondary", text: "Đăng nhập", onclick: () => renderAuth("login") }),
      h("button", { type: "button", class: mode === "register" ? "" : "secondary", text: "Đăng ký", onclick: () => renderAuth("register") })),
    mode === "register" && field("Họ và tên", fields.fullName),
    field("Email", fields.email),
    field("Mật khẩu", fields.password),
    mode === "register" && h("p", { class: "muted small", text: "Ít nhất 8 ký tự." }),
    mode === "register" && field("Trường", fields.school),
    mode === "register" && field("Lớp", fields.className),
    mode === "register" && h("p", { class: "hint", text: "Học sinh sẽ chọn đúng tên trường và lớp này trong ứng dụng để gửi yêu cầu vào lớp. Bạn có thể thêm lớp khác sau khi đăng nhập." }),
    error, submit)));
  (mode === "login" ? fields.email : fields.fullName).focus();
}

// ---------- dashboard ----------
async function loadDashboard() {
  mount(h("p", { class: "boot", text: "Đang tải lớp học…" }));
  try {
    if (!state.user) state.user = await api("/auth/me");
    state.classes = await api("/classes");
  } catch (e) {
    if (handleFatal(e)) return;
    clearSession({ keepGate: true });
    return renderAuth("login", e.message);
  }
  if (!state.classes.some((c) => c.id === state.classId)) state.classId = state.classes[0]?.id ?? null;
  if (state.classId) store.set("pop_class", state.classId);
  state.students = null; state.analytics = null; state.studentDetail = null; state.selectedStudentId = null;
  await loadTab();
}

const currentClass = () => state.classes.find((c) => c.id === state.classId) || null;

async function refreshClasses() {
  state.classes = await api("/classes");
}

async function loadTab() {
  const klass = currentClass();
  if (klass) {
    try {
      if (state.tab === "requests") state.requests = await api(`/classes/${klass.id}/requests`);
      else if (state.tab === "students") {
        state.students = await api(`/classes/${klass.id}/students?days=${state.days}&timeZone=${encodeURIComponent(TIME_ZONE)}`);
        if (state.selectedStudentId) await loadStudentDetail(state.selectedStudentId, false);
      } else if (state.tab === "analytics") {
        state.analytics = await api(`/classes/${klass.id}/analytics?days=${state.days}&timeZone=${encodeURIComponent(TIME_ZONE)}`);
      } else if (state.tab === "usage") {
        state.usage = await api(`/classes/${klass.id}/usage?days=${state.days}&timeZone=${encodeURIComponent(TIME_ZONE)}`);
      } else if (state.tab === "survey") {
        state.surveyAnalytics = await api(`/classes/${klass.id}/assessment-analytics`);
      } else if (state.tab === "notifications") {
        const [history, students] = await Promise.all([
          api(`/classes/${klass.id}/notifications`),
          state.students ? Promise.resolve(state.students)
            : api(`/classes/${klass.id}/students?days=7&timeZone=${encodeURIComponent(TIME_ZONE)}`)
        ]);
        state.history = history; state.students = state.students || students;
      }
    } catch (e) {
      if (handleFatal(e)) return;
      toast(e.message, true);
    }
  }
  renderDashboard();
}

async function loadStudentDetail(userId, rerender = true) {
  const klass = currentClass();
  try {
    state.studentDetail = await api(
      `/classes/${klass.id}/students/${userId}/focus?days=${Math.max(state.days, 14)}&timeZone=${encodeURIComponent(TIME_ZONE)}`
    );
    state.selectedStudentId = userId;
  } catch (e) {
    if (handleFatal(e)) return;
    toast(e.message, true);
  }
  if (rerender) renderDashboard();
}

async function logout() {
  try { await api("/auth/logout", { method: "POST" }); } catch { /* vẫn đăng xuất cục bộ */ }
  clearSession();
  state.classId = null; store.remove("pop_class");
  renderGate();
}

function renderDashboard() {
  const klass = currentClass();
  const pendingTotal = state.classes.reduce((sum, c) => sum + c.pendingCount, 0);

  const select = h("select", { id: "class-select", "aria-label": "Chọn lớp", onchange: async (e) => {
    state.classId = e.target.value; store.set("pop_class", state.classId);
    state.students = null; state.analytics = null; state.studentDetail = null; state.selectedStudentId = null;
    await loadTab();
  } }, state.classes.map((c) => h("option", {
    value: c.id, selected: c.id === state.classId,
    text: `${c.schoolName} — ${c.name}${c.pendingCount ? ` (${c.pendingCount} chờ duyệt)` : ""}`
  })));

  const addForm = renderAddClass();

  const header = h("div", { class: "topbar" },
    h("div", { class: "who" }, h("span", { text: "🍿" }), h("strong", { text: "POP AI · Giáo viên" })),
    h("div", { class: "who" },
      h("span", { class: "muted", text: state.user?.fullName || "" }),
      h("button", { class: "ghost", type: "button", text: "Đăng xuất", onclick: logout })));

  const body = [
    h("div", { class: "card" }, h("div", { class: "row" },
      h("div", { class: "grow" }, h("label", { for: "class-select", text: "Lớp đang quản lý" }), state.classes.length ? select : h("p", { class: "muted", text: "Chưa có lớp nào." })),
      h("button", { class: "secondary", type: "button", text: "+ Thêm lớp", onclick: () => addForm.toggleAttribute("hidden") })),
      addForm)
  ];

  if (klass) {
    const totals = state.students?.totals;
    body.push(
      h("div", { class: "stats" },
        stat(klass.pendingCount, "Chờ duyệt"),
        stat(klass.studentCount, "Học sinh trong lớp"),
        totals ? stat(fmtMinutes(totals.focusMinutes), `Tập trung (${state.students.days} ngày)`) : null,
        totals ? stat(`${totals.activeStudents}/${klass.studentCount}`, "Học sinh có hoạt động") : null),
      h("div", { class: "card" },
        h("div", { class: "tabs", role: "tablist" },
          tabButton("requests", "Duyệt học sinh", klass.pendingCount),
          tabButton("analytics", "Thống kê lớp"),
          tabButton("usage", "TikTok & YouTube"),
          tabButton("survey", "Khảo sát"),
          tabButton("students", "Thói quen tập trung"),
          tabButton("notifications", "Thông báo")),
        state.tab === "requests" ? renderRequests(klass)
          : state.tab === "analytics" ? renderAnalytics(klass)
            : state.tab === "usage" ? renderUsage()
            : state.tab === "survey" ? renderSurveyAnalytics()
            : state.tab === "students" ? renderStudents(klass)
              : renderNotifications(klass)));
  } else {
    body.push(h("div", { class: "card empty", text: "Hãy thêm lớp đầu tiên để bắt đầu nhận học sinh." }));
  }
  if (pendingTotal > 0 && klass && klass.pendingCount === 0) {
    body.unshift(h("div", { class: "hint", text: `Các lớp khác của bạn còn ${pendingTotal} yêu cầu đang chờ duyệt.` }));
  }
  mount(header, h("div", { class: "container" }, body));
}

const stat = (num, label) => h("div", { class: "stat" }, h("div", { class: "num", text: String(num) }), h("div", { class: "muted small", text: label }));

function tabButton(id, label, badge) {
  return h("button", {
    type: "button", role: "tab", class: state.tab === id ? "active" : "", "aria-selected": state.tab === id,
    onclick: async () => { state.tab = id; await loadTab(); }
  }, label, badge ? h("span", { class: "badge", text: String(badge) }) : null);
}

function renderAddClass() {
  const school = h("input", { id: "add-school", maxlength: 150, placeholder: "Tên trường", list: "schools-list" });
  const className = h("input", { id: "add-class", maxlength: 60, placeholder: "Tên lớp, ví dụ 11A2" });
  const knownSchools = [...new Set(state.classes.map((c) => c.schoolName))];
  const list = h("datalist", { id: "schools-list" }, knownSchools.map((name) => h("option", { value: name })));
  const save = h("button", { type: "button", text: "Lưu lớp", onclick: async () => {
    save.disabled = true;
    try {
      const created = await api("/classes", { method: "POST", body: { school: school.value, className: className.value } });
      state.classId = created.id; store.set("pop_class", created.id);
      state.classes = await api("/classes");
      state.students = null; state.analytics = null; state.studentDetail = null; state.selectedStudentId = null;
      toast("Đã thêm lớp.");
      await loadTab();
    } catch (e) {
      if (handleFatal(e)) return;
      toast(e.message, true); save.disabled = false;
    }
  } });
  return h("div", { hidden: true, class: "row", style: "margin-top:.75rem" },
    h("div", { class: "grow" }, h("label", { for: "add-school", text: "Trường" }), school, list),
    h("div", { class: "grow" }, h("label", { for: "add-class", text: "Lớp" }), className), save);
}

// ----- tab: duyệt học sinh -----
function renderRequests(klass) {
  async function decide(userId, action, name) {
    try {
      await api(`/classes/${klass.id}/requests/${userId}/${action}`, { method: "POST" });
      toast(action === "approve" ? `Đã duyệt ${name}.` : `Đã từ chối ${name}.`);
      await refreshClasses(); state.students = null; state.analytics = null;
      await loadTab();
    } catch (e) {
      if (handleFatal(e)) return;
      toast(e.message, true); await loadTab();
    }
  }
  const approveAll = h("button", { type: "button", text: `Duyệt tất cả (${state.requests.length})`, onclick: async () => {
    if (!confirm(`Duyệt ${state.requests.length} học sinh vào lớp ${klass.name}?`)) return;
    try {
      const result = await api(`/classes/${klass.id}/requests/approve-all`, { method: "POST" });
      toast(`Đã duyệt ${result.updated} học sinh.`);
      await refreshClasses(); state.students = null; state.analytics = null;
      await loadTab();
    } catch (e) {
      if (handleFatal(e)) return;
      toast(e.message, true);
    }
  } });
  if (state.requests.length === 0) {
    return h("div", { class: "empty" }, h("p", { text: "Không có yêu cầu nào đang chờ." }),
      h("p", { class: "small", text: `Học sinh chọn trường “${klass.schoolName}” và lớp “${klass.name}” trong ứng dụng POP AI để gửi yêu cầu.` }));
  }
  return h("div", {},
    h("div", { class: "row", style: "margin-top:.75rem; justify-content: space-between" },
      h("h3", { text: "Học sinh xin vào lớp" }), state.requests.length > 1 ? approveAll : null),
    h("div", { class: "list" }, state.requests.map((r) => h("div", { class: "item" },
      h("div", {}, h("strong", { text: r.fullName }), h("div", { class: "muted small", text: `${r.email} · ${timeAgo(r.requestedAt)}` })),
      h("div", { class: "actions" },
        h("button", { type: "button", text: "Duyệt", onclick: () => decide(r.userId, "approve", r.fullName) }),
        h("button", { type: "button", class: "danger", text: "Từ chối", onclick: () => decide(r.userId, "reject", r.fullName) }))))));
}

// ----- tab: thống kê lớp -----
const ATTENTION_LABELS = {
  NO_ACTIVITY: "Chưa có phiên nào",
  INACTIVE: "Lâu chưa hoạt động",
  INTERRUPTED: "Hay bị ngắt",
  LOW_COMPLETION: "Hoàn thành ít phiên"
};

/** Biểu đồ cột đứng; mỗi phần tử { label, value, title }. */
function barChart(items, { height = 120, ariaLabel, showValues = true } = {}) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return h("div", { class: "bars", "aria-label": ariaLabel },
    items.map((i) => h("div", { class: "bar-col", title: i.title || `${i.label}: ${i.value}` },
      showValues ? h("span", { text: i.value ? String(i.value) : "" }) : null,
      h("div", { class: "bar", style: `height:${Math.round((i.value / max) * height)}px` }),
      h("span", { text: i.label }))));
}

/** Thanh ngang kèm số lượng; mỗi phần tử { label, count }. */
function hbars(items, total) {
  const max = Math.max(1, ...items.map((i) => i.count));
  return h("div", { class: "hbars" }, items.map((i) => h("div", { class: "hbar-row" },
    h("span", { class: "hbar-label", text: i.label }),
    h("div", { class: "hbar-track" }, h("div", { class: "hbar-fill", style: `width:${Math.round((i.count / max) * 100)}%` })),
    h("span", { class: "hbar-count", text: total ? `${i.count} (${Math.round((i.count / total) * 100)}%)` : String(i.count) }))));
}

function renderAnalytics(klass) {
  const data = state.analytics;
  if (!data) return h("div", { class: "empty", text: "Đang tải…" });
  const period = h("select", { id: "period", "aria-label": "Khoảng thời gian", onchange: async (e) => {
    state.days = Number(e.target.value); state.students = null; await loadTab();
  } }, [7, 14, 30].map((d) => h("option", { value: d, selected: d === state.days, text: `${d} ngày gần nhất` })));
  const intro = h("div", { class: "row", style: "margin-top:.75rem; justify-content: space-between" },
    h("p", { class: "muted small", style: "margin:0", text: "Số liệu tổng hợp của cả lớp, chỉ tính phiên tập trung từ lúc học sinh được duyệt vào lớp." }), period);
  const sm = data.summary;
  if (sm.studentCount === 0) {
    return h("div", {}, intro, h("div", { class: "empty", text: "Lớp chưa có học sinh nào được duyệt nên chưa có thống kê." }));
  }

  const cards = h("div", { class: "stats", style: "margin-top:.75rem" },
    stat(`${sm.participationRate}%`, `Tham gia (${sm.activeStudents}/${sm.studentCount} học sinh)`),
    stat(fmtMinutes(sm.focusMinutes), "Tổng thời gian tập trung"),
    stat(fmtMinutes(Math.round(sm.avgMinutesPerStudent)), "Trung bình mỗi học sinh"),
    stat(`${sm.completionRate}%`, `Phiên hoàn thành (${sm.completedSessions}/${sm.totalSessions})`),
    stat(String(sm.interruptionsPerSession), "Lần bị ngắt mỗi phiên"),
    stat(sm.avgRating ? `${sm.avgRating}/5` : "—", "Tự đánh giá trung bình"));

  const dailyItems = data.daily.map((d) => ({
    label: d.date.slice(8, 10) + "/" + d.date.slice(5, 7), value: d.focusMinutes,
    title: `${d.date}: ${d.focusMinutes} phút · ${d.activeStudents} học sinh hoạt động · ${d.completedSessions} phiên hoàn thành`
  }));
  const dailyCard = h("div", { class: "card inner" },
    h("h3", { text: "Phút tập trung của cả lớp mỗi ngày" }),
    data.summary.focusMinutes === 0 ? h("p", { class: "muted", text: "Chưa có phiên tập trung nào trong khoảng này." })
      : barChart(dailyItems, { ariaLabel: "Phút tập trung cả lớp mỗi ngày" }));

  const activeItems = data.daily.map((d) => ({
    label: d.date.slice(8, 10) + "/" + d.date.slice(5, 7), value: d.activeStudents,
    title: `${d.date}: ${d.activeStudents}/${sm.studentCount} học sinh hoạt động`
  }));
  const activeCard = h("div", { class: "card inner" },
    h("h3", { text: "Số học sinh hoạt động mỗi ngày" }),
    barChart(activeItems, { height: 90, ariaLabel: "Số học sinh hoạt động mỗi ngày" }));

  const hourItems = data.hourly.map((x) => ({ label: String(x.hour), value: x.focusMinutes, title: `${x.hour}h: ${x.focusMinutes} phút` }));
  const bestHour = data.hourly.reduce((best, x) => (x.focusMinutes > best.focusMinutes ? x : best), data.hourly[0]);
  const hourCard = h("div", { class: "card inner" },
    h("h3", { text: "Khung giờ học sinh hay tập trung" }),
    bestHour.focusMinutes > 0 ? h("p", { class: "muted small", text: `Nhiều nhất vào khoảng ${bestHour.hour}h–${(bestHour.hour + 1) % 24}h (${bestHour.focusMinutes} phút).` }) : null,
    barChart(hourItems, { height: 90, showValues: false, ariaLabel: "Phút tập trung theo giờ trong ngày" }));

  const levelCard = h("div", { class: "card inner" },
    h("h3", { text: "Mức tập trung của học sinh" }),
    hbars(data.levels, sm.studentCount));

  const ratingTotal = data.ratings.reduce((n, r) => n + r.count, 0);
  const ratingCard = h("div", { class: "card inner" },
    h("h3", { text: "Học sinh tự đánh giá phiên học" }),
    ratingTotal === 0 ? h("p", { class: "muted", text: "Chưa có đánh giá nào." })
      : hbars(data.ratings.map((r) => ({ label: `${r.rating} sao`, count: r.count })), ratingTotal));

  const openStudent = async (userId) => { state.tab = "students"; state.selectedStudentId = userId; await loadTab(); };
  const top = h("div", { class: "card inner" },
    h("h3", { text: "Tập trung nhiều nhất" }),
    data.topStudents.length === 0 ? h("p", { class: "muted", text: "Chưa có dữ liệu." })
      : h("ol", { class: "ranking" }, data.topStudents.map((s) => h("li", {},
        h("button", { type: "button", class: "link", text: s.fullName, onclick: () => openStudent(s.userId) }),
        h("span", { class: "muted small", text: ` · ${fmtMinutes(s.focusMinutes)} · ${s.activeDays}/${data.days} ngày` })))));

  const attention = h("div", { class: "card inner" },
    h("h3", { text: "Cần quan tâm" }),
    data.attention.length === 0 ? h("p", { class: "muted", text: "Không có học sinh nào cần lưu ý." })
      : h("div", { class: "list", style: "margin-top:.4rem" }, data.attention.map((a) => h("div", { class: "item" },
        h("div", {},
          h("button", { type: "button", class: "link", text: a.fullName, onclick: () => openStudent(a.userId) }),
          h("div", { class: "muted small", text: a.totalSessions === 0 ? "Chưa có hoạt động" : `Gần nhất: ${timeAgo(a.lastActivity)}` })),
        h("div", { class: "actions" }, a.reasons.map((r) => h("span", { class: "pill warn", text: ATTENTION_LABELS[r] || r })))))));

  return h("div", {}, intro, cards,
    h("div", { class: "grid2" }, dailyCard, activeCard),
    h("div", { class: "grid2" }, hourCard, levelCard),
    h("div", { class: "grid2" }, ratingCard, top),
    attention);
}

// ----- tab: TikTok & YouTube -----
function renderUsage() {
  const data = state.usage;
  if (!data) return h("div", { class: "empty", text: "Đang tải…" });
  const period = h("select", { id: "period", "aria-label": "Khoảng thời gian", onchange: async (e) => {
    state.days = Number(e.target.value); state.usage = null; await loadTab();
  } }, [7, 14, 30].map((d) => h("option", { value: d, selected: d === state.days, text: `${d} ngày gần nhất` })));
  const intro = h("div", { class: "row", style: "margin-top:.75rem; justify-content: space-between" },
    h("p", { class: "muted small", style: "margin:0", text: "Thời gian mở TikTok và YouTube trên điện thoại học sinh (do app POP AI gửi lên khi học sinh bật theo dõi)." }), period);
  const sm = data.summary;
  if (sm.studentCount === 0) {
    return h("div", {}, intro, h("div", { class: "empty", text: "Lớp chưa có học sinh nào được duyệt nên chưa có thống kê." }));
  }
  if (sm.reportingStudents === 0) {
    return h("div", {}, intro, h("div", { class: "empty", text: "Chưa có học sinh nào gửi dữ liệu. Học sinh cần bật theo dõi thời gian dùng ứng dụng trong app." }));
  }

  const cards = h("div", { class: "stats", style: "margin-top:.75rem" },
    stat(fmtMinutes(sm.tiktokMinutes), "Tổng thời gian TikTok"),
    stat(fmtMinutes(sm.youtubeMinutes), "Tổng thời gian YouTube"),
    stat(`${sm.tiktokAvgPerStudentPerDay} phút`, "TikTok / học sinh / ngày"),
    stat(`${sm.youtubeAvgPerStudentPerDay} phút`, "YouTube / học sinh / ngày"),
    stat(`${sm.reportingStudents}/${sm.studentCount}`, "Học sinh có dữ liệu"));

  const label = (d) => d.date.slice(8, 10) + "/" + d.date.slice(5, 7);
  const dailyCard = (title, key) => h("div", { class: "card inner" },
    h("h3", { text: title }),
    barChart(data.daily.map((d) => ({ label: label(d), value: d[key], title: `${d.date}: ${d[key]} phút` })),
      { ariaLabel: title, showValues: data.daily.length <= 14 }));

  const split = h("div", { class: "card inner" },
    h("h3", { text: "So sánh cả lớp" }),
    hbars([
      { label: "🎵 TikTok", count: sm.tiktokMinutes },
      { label: "▶️ YouTube", count: sm.youtubeMinutes }
    ], sm.totalMinutes));

  const rows = data.students.map((s) => h("tr", {},
    h("td", { text: s.fullName }),
    h("td", { text: s.reportedDays ? fmtMinutes(s.tiktok) : "—" }),
    h("td", { text: s.reportedDays ? fmtMinutes(s.youtube) : "—" }),
    h("td", {}, h("strong", { text: s.reportedDays ? fmtMinutes(s.totalMinutes) : "—" })),
    h("td", { text: s.reportedDays ? `${s.reportedDays}/${data.days}` : "Chưa gửi" }),
    h("td", { text: timeAgo(s.lastReport) })));
  const table = h("div", { class: "card inner" },
    h("h3", { text: "Từng học sinh (nhiều nhất lên đầu)" }),
    h("div", { class: "table-wrap" }, h("table", {},
      h("thead", {}, h("tr", {}, ["Học sinh", "TikTok", "YouTube", "Tổng", "Số ngày có dữ liệu", "Gửi lần cuối"].map((x) => h("th", { text: x })))),
      h("tbody", {}, rows))));

  return h("div", {}, intro, cards, split,
    h("div", { class: "grid2" }, dailyCard("TikTok mỗi ngày (phút, cả lớp)", "tiktok"), dailyCard("YouTube mỗi ngày (phút, cả lớp)", "youtube")),
    table);
}

// ----- tab: khảo sát trước/sau thực nghiệm -----
function renderSurveyAnalytics() {
  const data = state.surveyAnalytics;
  if (!data) return h("div", { class: "empty", text: "Đang tải dữ liệu khảo sát…" });
  const groups = ["VN", "PT", "PB", "TT", "DG"];
  const score = (phase, group) => data.averages?.[phase]?.[group];
  const chart = h("div", { class: "bars", "aria-label": "So sánh điểm khảo sát trước và sau" }, groups.map((g) =>
    h("div", { class: "bar-col", title: `${g} · trước ${score("PRE", g) ?? "—"} · sau ${score("POST", g) ?? "—"}` },
      h("span", { text: `${score("PRE", g) ?? "—"}/${score("POST", g) ?? "—"}` }),
      h("div", { class: "bar-pair" },
        h("div", { class: "bar", style: `height:${Math.round((Number(score("PRE", g) || 0) / 5) * 100)}px; background:#8bb8ff` }),
        h("div", { class: "bar", style: `height:${Math.round((Number(score("POST", g) || 0) / 5) * 100)}px; background:#44c49a` })),
      h("span", { text: g }))));
  const rows = data.students.map((s) => h("tr", {},
    h("td", {}, h("strong", { text: s.fullName }), h("div", { class: "muted small", text: s.email })),
    ...groups.map((g) => h("td", { text: `${s.phases?.PRE?.scores?.[g] ?? "—"} / ${s.phases?.POST?.scores?.[g] ?? "—"}` }))));
  return h("div", {},
    h("p", { class: "muted small", text: "Điểm theo đúng các nhóm trong phiếu; định dạng là Trước / Sau. DG chỉ có ở phiếu sau thực nghiệm." }),
    h("div", { class: "card inner" }, h("h3", { text: "Biểu đồ điểm trung bình trước và sau" }), chart,
      h("p", { class: "muted small", text: "Xanh nhạt: trước · Xanh lá: sau" })),
    h("div", { class: "table-wrap" }, h("table", {}, h("thead", {}, h("tr", {}, ["Học sinh", ...groups].map((x) => h("th", { text: x })) )), h("tbody", {}, rows))));
}

// ----- tab: thói quen tập trung -----
function renderStudents(klass) {
  const period = h("select", { id: "period", "aria-label": "Khoảng thời gian", onchange: async (e) => {
    state.days = Number(e.target.value); await loadTab();
  } }, [7, 14, 30].map((d) => h("option", { value: d, selected: d === state.days, text: `${d} ngày gần nhất` })));
  const data = state.students;
  if (!data) return h("div", { class: "empty", text: "Đang tải…" });
  const intro = h("div", { class: "row", style: "margin-top:.75rem; justify-content: space-between" },
    h("p", { class: "muted small", style: "margin:0", text: "Chỉ hiển thị phiên tập trung từ lúc học sinh được duyệt vào lớp." }), period);
  if (data.students.length === 0) {
    return h("div", {}, intro, h("div", { class: "empty", text: "Lớp chưa có học sinh nào được duyệt." }));
  }
  const table = h("div", { class: "table-wrap" }, h("table", {},
    h("thead", {}, h("tr", {}, ["Học sinh", "Phút tập trung", "Phiên hoàn thành", "Ngày hoạt động", "Lần bị ngắt", "Tự đánh giá TB", "Hoạt động gần nhất"].map((t) => h("th", { text: t })))),
    h("tbody", {}, data.students.map((s) => h("tr", {
      class: `clickable${s.userId === state.selectedStudentId ? " selected" : ""}`, tabindex: 0,
      onclick: () => loadStudentDetail(s.userId),
      onkeydown: (e) => { if (e.key === "Enter") loadStudentDetail(s.userId); }
    },
    h("td", {}, h("strong", { text: s.fullName }), h("div", { class: "muted small", text: s.email })),
    h("td", { text: String(s.focusMinutes) }),
    h("td", { text: String(s.completedSessions) }),
    h("td", { text: `${s.activeDays}/${data.days}` }),
    h("td", { text: String(s.interruptions) }),
    h("td", { text: s.avgRating ? `${s.avgRating}/5` : "—" }),
    h("td", {}, s.totalSessions === 0 ? h("span", { class: "pill warn", text: "Chưa có" }) : h("span", { text: timeAgo(s.lastActivity) })))))));
  return h("div", {}, intro, table, state.studentDetail ? renderStudentDetail(klass) : h("p", { class: "muted small", text: "Chọn một học sinh để xem chi tiết." }));
}

function renderStudentDetail(klass) {
  const detail = state.studentDetail;
  const maxMinutes = Math.max(1, ...detail.daily.map((d) => d.focusMinutes));
  const bars = detail.daily.length === 0
    ? h("p", { class: "muted", text: "Chưa có phiên tập trung nào." })
    : h("div", { class: "bars", "aria-label": "Phút tập trung mỗi ngày" }, detail.daily.map((d) => h("div", { class: "bar-col", title: `${d.date}: ${d.focusMinutes} phút` },
      h("span", { text: String(d.focusMinutes) }),
      h("div", { class: "bar", style: `height:${Math.round((d.focusMinutes / maxMinutes) * 100)}px` }),
      h("span", { text: d.date.slice(8, 10) + "/" + d.date.slice(5, 7) }))));
  const statusLabel = { COMPLETED: "Hoàn thành", PAUSED: "Tạm dừng", RUNNING: "Đang chạy", NOT_STARTED: "Chưa bắt đầu" };
  const sessions = detail.sessions.length === 0 ? null : h("div", { class: "table-wrap" }, h("table", {},
    h("thead", {}, h("tr", {}, ["Thời gian", "Nhiệm vụ", "Thực tế / mục tiêu", "Bị ngắt", "Tự đánh giá", "Trạng thái"].map((t) => h("th", { text: t })))),
    h("tbody", {}, detail.sessions.slice(0, 30).map((s) => h("tr", {},
      h("td", { text: fmtDateTime(s.createdAt) }),
      h("td", { text: s.taskName || "—" }),
      h("td", { text: `${s.actualMinutes}/${s.targetMinutes} phút` }),
      h("td", { text: String(s.interruptionCount) }),
      h("td", { text: s.selfRating ? `${s.selfRating}/5` : "—" }),
      h("td", {}, h("span", { class: `pill${s.status === "COMPLETED" ? " ok" : ""}`, text: statusLabel[s.status] || s.status })))))));
  return h("div", { class: "card", style: "margin-top:1rem" },
    h("div", { class: "row", style: "justify-content: space-between" },
      h("div", {}, h("h3", { text: detail.student.fullName }), h("div", { class: "muted small", text: `${detail.student.email} · vào lớp ${fmtDateTime(detail.student.joinedAt)}` })),
      h("button", { type: "button", class: "danger", text: "Xoá khỏi lớp", onclick: async () => {
        if (!confirm(`Xoá ${detail.student.fullName} khỏi lớp ${klass.name}?`)) return;
        try {
          await api(`/classes/${klass.id}/students/${detail.student.userId}`, { method: "DELETE" });
          toast("Đã xoá học sinh khỏi lớp.");
          state.selectedStudentId = null; state.studentDetail = null; state.students = null; state.analytics = null;
          await refreshClasses(); await loadTab();
        } catch (e) {
          if (handleFatal(e)) return;
          toast(e.message, true);
        }
      } })),
    h("h3", { style: "margin-top:1rem", text: `Phút tập trung hoàn thành mỗi ngày (${detail.days} ngày)` }), bars, sessions);
}

// ----- tab: thông báo -----
function renderNotifications(klass) {
  const title = h("input", { id: "n-title", maxlength: 80, placeholder: "Ví dụ: Nhắc nhở giờ tự học tối nay" });
  const body = h("textarea", { id: "n-body", maxlength: 500, placeholder: "Nội dung học sinh sẽ thấy trên điện thoại…" });
  const counter = h("div", { class: "counter", text: "0/500" });
  body.addEventListener("input", () => { counter.textContent = `${body.value.length}/500`; });

  const students = state.students?.students || [];
  const checks = students.map((s) => h("label", {}, h("input", { type: "checkbox", value: s.userId, checked: true }), s.fullName));
  const checkBox = h("div", { class: "checks", hidden: true }, checks);
  let scope = "all";
  const radio = h("div", { class: "radio" },
    h("label", {}, h("input", { type: "radio", name: "scope", value: "all", checked: true, onchange: () => { scope = "all"; checkBox.hidden = true; } }), "Cả lớp"),
    h("label", {}, h("input", { type: "radio", name: "scope", value: "some", onchange: () => { scope = "some"; checkBox.hidden = false; } }), "Chọn học sinh"));

  const send = h("button", { type: "button", text: "Gửi thông báo", disabled: students.length === 0, onclick: async () => {
    const payload = { title: title.value, body: body.value };
    if (scope === "some") {
      payload.studentIds = checks.map((label) => label.firstChild).filter((box) => box.checked).map((box) => box.value);
      if (payload.studentIds.length === 0) return toast("Hãy chọn ít nhất một học sinh.", true);
    }
    send.disabled = true;
    try {
      const result = await api(`/classes/${klass.id}/notifications`, { method: "POST", body: payload });
      toast(`Đã gửi tới ${result.recipientCount} học sinh.`);
      title.value = ""; body.value = ""; counter.textContent = "0/500";
      await loadTab();
    } catch (e) {
      if (handleFatal(e)) return;
      toast(e.message, true); send.disabled = false;
    }
  } });

  const history = state.history.length === 0
    ? h("p", { class: "muted", text: "Chưa gửi thông báo nào." })
    : h("div", { class: "list" }, state.history.map((n) => h("div", { class: "item" },
      h("div", {}, h("strong", { text: n.title }), h("div", { text: n.body }), h("div", { class: "muted small", text: fmtDateTime(n.createdAt) })),
      h("span", { class: "pill", text: `Đã đọc ${n.readCount}/${n.recipientCount}` }))));

  return h("div", {},
    h("div", { style: "margin-top:.75rem" },
      h("h3", { text: "Gửi thông báo tới điện thoại học sinh" }),
      students.length === 0 ? h("p", { class: "hint", text: "Lớp chưa có học sinh được duyệt nên chưa thể gửi thông báo." }) : null,
      h("label", { for: "n-title", text: "Tiêu đề" }), title,
      h("label", { for: "n-body", text: "Nội dung" }), body, counter,
      h("label", { text: "Người nhận" }), radio, checkBox,
      h("div", { style: "margin-top:1rem" }, send)),
    h("h3", { style: "margin-top:1.5rem", text: "Đã gửi gần đây" }), history);
}

// ---------- khởi động ----------
async function start() {
  if (state.token) {
    try {
      state.user = await api("/auth/me");
      return loadDashboard();
    } catch (e) {
      clearSession({ keepGate: true });
    }
  }
  if (gateValid()) renderAuth("login");
  else renderGate();
}

start();
