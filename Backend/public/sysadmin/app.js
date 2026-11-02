"use strict";

const API = "/api/admin/v1";
const WEEKDAYS = [2, 3, 4, 5, 6];
const SESSIONS = [["MORNING", "Buổi sáng"], ["AFTERNOON", "Buổi chiều"]];
const MAX_PERIODS = 5;
const SUBJECT_SUGGESTIONS = [
  "Toán", "Ngữ văn", "Tiếng Anh", "Vật lí", "Hóa học", "Sinh học", "Lịch sử", "Địa lí", "GDCD",
  "Tin học", "Công nghệ", "Thể dục", "Âm nhạc", "Mĩ thuật", "Sinh hoạt lớp", "Hoạt động trải nghiệm"
];

const store = {
  get(key) { try { return sessionStorage.getItem(key); } catch { return null; } },
  set(key, value) { try { sessionStorage.setItem(key, value); } catch { /* bỏ qua */ } },
  remove(key) { try { sessionStorage.removeItem(key); } catch { /* bỏ qua */ } }
};

const state = {
  token: store.get("sysadmin_token"),
  classes: [],
  filter: "",
  settings: null,
  selected: null,   // thông tin lớp đang chọn
  model: null,      // model[weekday][session] = { on, subjects: [5] }
  dirty: false,
  view: "timetable", // "timetable" | "stats"
  stats: null        // trạng thái trang thống kê (khởi tạo trong stats.js)
};

// ---------- DOM (chỉ dùng textContent để tránh XSS) ----------
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === "class") el.className = value;
    else if (key === "text") el.textContent = value;
    else if (key.startsWith("on") && typeof value === "function") el.addEventListener(key.slice(2), value);
    else if (key === "value") el.value = value;
    else if (key === "checked" || key === "disabled") el[key] = Boolean(value);
    else el.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat()) {
    if (child === undefined || child === null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}
const root = document.getElementById("app");
const mount = (...nodes) => root.replaceChildren(...nodes);

let toastTimer;
function toast(message, bad = false) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.className = `toast show${bad ? " bad" : ""}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = "toast"; }, 4000);
}

// ---------- API ----------
class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

async function api(path, { method = "GET", body } = {}) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
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

function logout(message) {
  state.token = null; state.selected = null; state.model = null; state.dirty = false;
  state.view = "timetable"; state.stats = null;
  store.remove("sysadmin_token");
  if (message) toast(message, true);
  renderLogin();
}

/** Trả true nếu lỗi là hết phiên (đã chuyển về màn hình đăng nhập). */
function handleFatal(error) {
  if (error.status === 401) { logout("Phiên đăng nhập đã hết hạn."); return true; }
  return false;
}

// ---------- Mô hình thời khóa biểu ----------
function emptyModel() {
  const model = {};
  for (const weekday of WEEKDAYS) {
    model[weekday] = {};
    for (const [session] of SESSIONS) model[weekday][session] = { on: false, subjects: Array(MAX_PERIODS).fill("") };
  }
  return model;
}

function modelFromEntries(entries) {
  const model = emptyModel();
  for (const entry of entries) {
    const slot = model[entry.weekday]?.[entry.session];
    if (!slot || entry.period < 1 || entry.period > MAX_PERIODS) continue;
    slot.on = true;
    slot.subjects[entry.period - 1] = entry.subject;
  }
  return model;
}

function entriesFromModel(model) {
  const entries = [];
  for (const weekday of WEEKDAYS) {
    for (const [session] of SESSIONS) {
      const slot = model[weekday][session];
      if (!slot.on) continue;
      slot.subjects.forEach((subject, index) => {
        const value = subject.replace(/\s+/g, " ").trim();
        if (value) entries.push({ weekday, session, period: index + 1, subject: value });
      });
    }
  }
  return entries;
}

// ---------- Đăng nhập ----------
function renderLogin() {
  const password = h("input", { type: "password", id: "pw", autocomplete: "current-password", maxlength: "200", required: true });
  const error = h("div", { class: "error", hidden: true });
  const submit = h("button", { type: "submit", class: "full", text: "Đăng nhập" });
  const form = h("form", {
    onsubmit: async (event) => {
      event.preventDefault();
      submit.disabled = true; error.hidden = true;
      try {
        const data = await api("/login", { method: "POST", body: { password: password.value } });
        state.token = data.token;
        store.set("sysadmin_token", data.token);
        await startApp();
      } catch (err) {
        error.textContent = err.message; error.hidden = false;
      } finally {
        submit.disabled = false;
      }
    }
  },
  h("label", { for: "pw", text: "Mật khẩu admin" }), password, error, submit);
  mount(h("div", { class: "center-wrap" },
    h("div", { class: "card auth-card" },
      h("h1", { text: "POP AI · Admin" }),
      h("p", { class: "muted", text: "Trang quản trị thời khóa biểu theo lớp. Dành riêng cho quản trị viên." }),
      form)));
  password.focus();
}

// ---------- Ứng dụng ----------
async function startApp() {
  try {
    const [classes, settings] = await Promise.all([api("/classes"), api("/settings")]);
    state.classes = classes;
    state.settings = settings;
  } catch (error) {
    if (handleFatal(error)) return;
    toast(error.message, true);
    renderLogin();
    return;
  }
  renderApp();
}

async function reloadClasses() {
  state.classes = await api(`/classes${state.filter ? `?q=${encodeURIComponent(state.filter)}` : ""}`);
}

function confirmDiscard() {
  return !state.dirty || window.confirm("Thời khóa biểu đang sửa chưa được lưu. Bỏ thay đổi và tiếp tục?");
}

async function selectClass(classId) {
  if (state.selected?.id !== classId && !confirmDiscard()) return;
  try {
    const data = await api(`/classes/${classId}/timetable`);
    state.selected = data.class;
    state.model = modelFromEntries(data.entries);
    state.dirty = false;
  } catch (error) {
    if (!handleFatal(error)) toast(error.message, true);
    return;
  }
  renderApp();
}

function switchView(view) {
  if (state.view === view) return;
  if (state.view === "timetable" && !confirmDiscard()) return;
  state.dirty = false;
  state.view = view;
  renderApp();
}

function renderApp() {
  const tab = (view, label) => h("button", {
    type: "button", class: `tab${state.view === view ? " active" : ""}`, "aria-current": state.view === view ? "page" : undefined,
    text: label, onclick: () => switchView(view)
  });
  mount(
    h("div", { class: "topbar" },
      h("strong", { text: "POP AI · Admin hệ thống" }),
      h("nav", { class: "tabs", "aria-label": "Chức năng" }, tab("timetable", "Thời khóa biểu"), tab("stats", "Thống kê")),
      h("button", { class: "ghost", text: "Đăng xuất", onclick: () => { if (state.view !== "timetable" || confirmDiscard()) logout(); } })),
    state.view === "stats"
      ? h("div", { class: "stats-page" }, renderStatsView())
      : h("div", { class: "layout" },
        h("div", { class: "side" }, renderSettings(), renderClassPicker()),
        h("div", { id: "editor" }, renderEditor())));
}

function renderSettings() {
  const time = h("input", { type: "time", id: "sendTime", value: state.settings.sendTime, required: true });
  const enabled = h("input", { type: "checkbox", checked: state.settings.enabled });
  return h("section", { class: "card" },
    h("h2", { text: "Thông báo hằng ngày" }),
    h("p", { class: "muted small", text: `Tự động gửi thời khóa biểu của ngày kế tiếp vào 19:30 ngày hôm trước (múi giờ ${state.settings.timeZone}).` }),
    h("label", { for: "sendTime", text: "Giờ gửi" }), time,
    h("label", { class: "check" }, enabled, "Bật gửi tự động"),
    h("div", { class: "actions" },
      h("button", {
        text: "Lưu cài đặt",
        onclick: async (event) => {
          event.target.disabled = true;
          try {
            state.settings = await api("/settings", { method: "PUT", body: { sendTime: time.value, enabled: enabled.checked } });
            toast("Đã lưu cài đặt gửi thông báo.");
          } catch (error) {
            if (!handleFatal(error)) toast(error.message, true);
          } finally {
            event.target.disabled = false;
          }
        }
      })));
}

function renderClassPicker() {
  const list = h("div", { class: "class-list" });
  const fill = () => {
    list.replaceChildren(...(state.classes.length === 0
      ? [h("p", { class: "empty", text: "Không có lớp nào." })]
      : state.classes.map((klass) => h("button", {
        class: `class-btn${state.selected?.id === klass.id ? " active" : ""}`,
        onclick: () => selectClass(klass.id)
      },
      h("span", { class: `count${klass.lessonCount === 0 ? " empty" : ""}`, text: klass.lessonCount === 0 ? "Chưa có TKB" : `${klass.lessonCount} tiết` }),
      klass.name,
      h("span", { class: "sub", text: `${klass.schoolName} · ${klass.studentCount} học sinh` })))));
  };
  fill();
  let timer;
  const search = h("input", {
    type: "text", id: "classSearch", placeholder: "Tìm lớp hoặc trường…", value: state.filter, maxlength: "100",
    oninput: (event) => {
      state.filter = event.target.value;
      clearTimeout(timer);
      timer = setTimeout(async () => {
        try { await reloadClasses(); fill(); } catch (error) { if (!handleFatal(error)) toast(error.message, true); }
      }, 250);
    }
  });
  return h("section", { class: "card" }, h("h2", { text: "Chọn lớp" }), search, list);
}

function renderEditor() {
  if (!state.selected) {
    return h("section", { class: "card" }, h("p", { class: "empty", text: "Chọn một lớp ở bên trái để nhập thời khóa biểu." }));
  }
  const klass = state.selected;
  const datalist = h("datalist", { id: "subjects" }, SUBJECT_SUGGESTIONS.map((name) => h("option", { value: name })));
  const days = h("div", { class: "days" }, WEEKDAYS.map((weekday) => h("div", { class: "day" },
    h("h3", { text: `Thứ ${weekday}` }),
    SESSIONS.map(([session, label]) => renderSession(weekday, session, label)))));

  const copySelect = h("select", { id: "copyFrom" },
    h("option", { value: "", text: "Sao chép từ lớp khác…" }),
    state.classes.filter((other) => other.id !== klass.id && other.lessonCount > 0)
      .map((other) => h("option", { value: other.id, text: `${other.name} (${other.schoolName})` })));
  copySelect.addEventListener("change", async () => {
    const id = copySelect.value;
    if (!id) return;
    if (state.dirty && !window.confirm("Thay thế nội dung đang nhập bằng thời khóa biểu của lớp đã chọn?")) { copySelect.value = ""; return; }
    try {
      const data = await api(`/classes/${id}/timetable`);
      state.model = modelFromEntries(data.entries);
      state.dirty = true;
      refreshEditor();
      toast("Đã sao chép. Nhấn Lưu để áp dụng cho lớp này.");
    } catch (error) {
      if (!handleFatal(error)) toast(error.message, true);
    }
  });

  const sendDay = h("select", { id: "sendDay", "aria-label": "Ngày gửi thử" },
    h("option", { value: "", text: "Hôm nay" }),
    WEEKDAYS.map((weekday) => h("option", { value: String(weekday), text: `Thứ ${weekday}` })));

  return h("section", { class: "card" },
    datalist,
    h("div", { class: "head" },
      h("div", {},
        h("h2", { text: `${klass.name} · ${klass.schoolName}` }),
        h("span", { class: "muted small", text: `Giáo viên: ${klass.teacherName} · ${klass.studentCount} học sinh đã tham gia` })),
      h("span", { class: `pill${state.dirty ? " warn" : ""}`, text: state.dirty ? "Chưa lưu" : "Đã lưu" })),
    h("p", { class: "muted small", text: "Tích chọn buổi có học trong ngày rồi nhập tối đa 5 tiết cho mỗi buổi. Tiết để trống sẽ không hiện trong thông báo." }),
    days,
    h("div", { class: "actions" },
      h("button", {
        text: "Lưu thời khóa biểu",
        onclick: async (event) => {
          event.target.disabled = true;
          try {
            const result = await api(`/classes/${klass.id}/timetable`, { method: "PUT", body: { entries: entriesFromModel(state.model) } });
            state.dirty = false;
            const row = state.classes.find((item) => item.id === klass.id);
            if (row) row.lessonCount = result.lessonCount;
            state.selected.lessonCount = result.lessonCount;
            renderApp();
            toast(`Đã lưu ${result.lessonCount} tiết.`);
          } catch (error) {
            if (!handleFatal(error)) toast(error.message, true);
            event.target.disabled = false;
          }
        }
      }),
      h("button", {
        class: "danger", text: "Xóa toàn bộ",
        onclick: () => {
          if (!window.confirm("Xóa toàn bộ thời khóa biểu đang nhập của lớp này? (Chỉ áp dụng sau khi nhấn Lưu)")) return;
          state.model = emptyModel(); state.dirty = true; refreshEditor();
        }
      }),
      copySelect),
    h("div", { class: "hint" },
      h("strong", { text: "Gửi thử: " }),
      "gửi ngay thông báo thời khóa biểu tới học sinh của lớp này (dùng bản đã lưu).",
      h("div", { class: "actions" }, sendDay,
        h("button", {
          class: "secondary", text: "Gửi thông báo ngay",
          onclick: async (event) => {
            if (state.dirty && !window.confirm("Bạn chưa lưu thay đổi; thông báo sẽ dùng bản đã lưu trước đó. Vẫn gửi?")) return;
            event.target.disabled = true;
            try {
              const body = sendDay.value ? { weekday: Number(sendDay.value) } : {};
              const result = await api(`/classes/${klass.id}/timetable/send-now`, { method: "POST", body });
              toast(`Đã gửi tới ${result.recipientCount} học sinh.`);
            } catch (error) {
              if (!handleFatal(error)) toast(error.message, true);
            } finally {
              event.target.disabled = false;
            }
          }
        }))));
}

function renderSession(weekday, session, label) {
  const slot = state.model[weekday][session];
  const box = h("input", {
    type: "checkbox", checked: slot.on, id: `on-${weekday}-${session}`,
    onchange: (event) => {
      slot.on = event.target.checked;
      if (!slot.on && slot.subjects.some((subject) => subject.trim()) &&
        !window.confirm(`Tắt ${label.toLowerCase()} sẽ bỏ các tiết đã nhập. Tiếp tục?`)) {
        slot.on = true; event.target.checked = true; return;
      }
      if (!slot.on) slot.subjects = Array(MAX_PERIODS).fill("");
      state.dirty = true;
      refreshEditor();
    }
  });
  return h("div", { class: `sess ${session}` },
    h("label", { class: "check", for: box.id }, box, label),
    slot.subjects.map((subject, index) => h("div", { class: "period" },
      h("input", {
        type: "text", list: "subjects", maxlength: "40", value: subject, disabled: !slot.on, placeholder: `Tiết ${index + 1}`,
        "aria-label": `Thứ ${weekday} ${label} tiết ${index + 1}`,
        oninput: (event) => { slot.subjects[index] = event.target.value; markDirty(); }
      }))));
}

function markDirty() {
  if (state.dirty) return;
  state.dirty = true;
  const pill = document.querySelector("#editor .head .pill");
  if (pill) { pill.textContent = "Chưa lưu"; pill.classList.add("warn"); }
}

function refreshEditor() {
  document.getElementById("editor").replaceChildren(renderEditor());
}

window.addEventListener("beforeunload", (event) => {
  if (state.dirty) { event.preventDefault(); event.returnValue = ""; }
});

if (state.token) startApp(); else renderLogin();
