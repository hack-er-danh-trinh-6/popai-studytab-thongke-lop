"use strict";

const API = "/api/admin/v1";
const TOKEN_KEY = "sysadmin_token";
const appRoot = document.getElementById("app");
const toastRoot = document.getElementById("toast");
const state = { token: sessionStorage.getItem(TOKEN_KEY), data: null };
let pollTimer = null;
let toastTimer = null;

function h(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === "text") node.textContent = value;
    else if (key === "class") node.className = value;
    else if (key === "value") node.value = value;
    else if (key === "disabled") node.disabled = Boolean(value);
    else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat()) {
    if (child === undefined || child === null || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

async function api(path, { method = "GET", body } = {}) {
  const headers = {};
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  let response;
  try {
    response = await fetch(API + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store"
    });
  } catch {
    throw new ApiError("Không kết nối được backend. Vui lòng thử lại.", 0);
  }
  const data = response.status === 204 ? null : await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(data?.message || "Có lỗi xảy ra. Vui lòng thử lại.", response.status);
  return data;
}

function showToast(message, bad = false) {
  toastRoot.textContent = message;
  toastRoot.className = `toast show${bad ? " bad" : ""}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toastRoot.className = "toast"; }, 4500);
}

function logout(message = "") {
  clearInterval(pollTimer);
  pollTimer = null;
  state.token = null;
  state.data = null;
  sessionStorage.removeItem(TOKEN_KEY);
  renderLogin(message);
}

function renderLogin(message = "") {
  const password = h("input", { id: "admin-password", type: "password", autocomplete: "current-password", maxlength: "200", required: true });
  const error = h("div", { class: "error", hidden: !message, text: message });
  const submit = h("button", { type: "submit", text: "Đăng nhập" });
  const form = h("form", { onsubmit: async (event) => {
    event.preventDefault();
    submit.disabled = true;
    error.hidden = true;
    try {
      const result = await api("/login", { method: "POST", body: { password: password.value } });
      state.token = result.token;
      sessionStorage.setItem(TOKEN_KEY, result.token);
      await loadDashboard();
    } catch (e) {
      error.textContent = e.message;
      error.hidden = false;
    } finally {
      submit.disabled = false;
    }
  } },
  h("label", { for: "admin-password", text: "Mật khẩu admin hệ thống" }), password, error, submit);

  appRoot.replaceChildren(h("main", { class: "center" }, h("section", { class: "card auth" },
    h("h1", { text: "POP AI · Adminbot" }),
    h("p", { class: "muted", text: "Trang quản lý cohort mô phỏng. Đăng nhập bằng SYSTEM_ADMIN_PASSWORD đã đặt trên Render." }),
    form)));
  password.focus();
}

function displayTime(value) {
  if (!value) return "Chưa có";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Chưa có" : new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(date);
}

function csvCell(value) {
  let text = String(value ?? "");
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function downloadCredentials(credentials) {
  const lines = [
    ["Vai trò", "Họ tên", "Email đăng nhập", "Mật khẩu tạm"],
    ...credentials.map((entry) => [entry.role === "TEACHER" ? "Giáo viên" : "Học sinh", entry.fullName, entry.email, entry.password])
  ].map((row) => row.map(csvCell).join(","));
  const blob = new Blob(["\uFEFF", lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = h("a", { href: url, download: `adminbot-credentials-${new Date().toISOString().slice(0, 10)}.csv`, hidden: true });
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

function stat(label, value, note = "") {
  return h("div", { class: "stat" },
    h("div", { class: "label", text: label }),
    h("div", { class: "value", text: value }),
    note ? h("div", { class: "label", text: note }) : null);
}

function renderDashboard(data) {
  clearInterval(pollTimer);
  pollTimer = null;
  state.data = data;
  const status = data.running
    ? h("span", { class: "pill running", text: "Bot đang chạy" })
    : h("span", { class: "pill stopped", text: "Bot đã dừng" });
  const countStudents = data.users.filter((user) => user.role === "STUDENT").length;
  const minutes = Number(data.syntheticFocusMinutes || 0);
  const hours = `${Math.floor(minutes / 60)} giờ ${minutes % 60} phút`;
  const provisionButton = h("button", {
    text: "Tạo 39 tài khoản",
    disabled: data.provisioned,
    onclick: async (event) => {
      if (!window.confirm("Tạo 1 tài khoản giáo viên và 38 tài khoản học sinh cho THPT Tây Đô · lớp 11TN2? Mật khẩu chỉ được trả về một lần dưới dạng CSV.")) return;
      event.currentTarget.disabled = true;
      try {
        const result = await api("/adminbot/provision", { method: "POST" });
        if (result.created && Array.isArray(result.credentials)) {
          downloadCredentials(result.credentials);
          showToast("Đã tạo cohort. Đã tải file mật khẩu một lần; hãy lưu file an toàn.");
        } else {
          showToast(result.message || "Cohort đã tồn tại; không tạo tài khoản trùng.");
        }
        await loadDashboard();
      } catch (e) {
        if (e.status === 401) return logout("Phiên admin hết hạn. Đăng nhập lại.");
        showToast(e.message, true);
        event.currentTarget.disabled = false;
      }
    }
  });
  const rotateButton = h("button", {
    class: "secondary",
    text: "Đặt lại mật khẩu & tải CSV",
    disabled: !data.provisioned,
    onclick: async (event) => {
      if (!window.confirm("Đặt lại mật khẩu cho toàn bộ 39 tài khoản mô phỏng? Mật khẩu cũ sẽ mất hiệu lực và các phiên đăng nhập bị thu hồi; CSV mới sẽ được tải xuống.")) return;
      event.currentTarget.disabled = true;
      try {
        const result = await api("/adminbot/credentials/rotate", { method: "POST", body: {} });
        downloadCredentials(result.credentials);
        showToast("Đã đặt lại mật khẩu và tải CSV mới. Lưu file an toàn.");
        await loadDashboard();
      } catch (e) {
        if (e.status === 401) return logout("Phiên admin hết hạn. Đăng nhập lại.");
        showToast(e.message, true);
        event.currentTarget.disabled = false;
      }
    }
  });
  const startButton = h("button", {
    text: "Start Bot",
    disabled: !data.provisioned || data.running,
    onclick: async (event) => {
      if (!window.confirm("Bắt đầu tạo dữ liệu tập trung MÔ PHỎNG? Lần đầu sẽ tạo khoảng 1 phiên/học sinh/ngày cho 7 ngày gần nhất; sau đó tối đa 1 phiên mỗi 5 phút. Dữ liệu đã tạo không tự xóa khi Stop.")) return;
      event.currentTarget.disabled = true;
      try {
        const result = await api("/adminbot/start", { method: "POST", body: {} });
        showToast(result.seededSessions ? `Đã tạo dữ liệu nền mô phỏng (${result.seededSessions} phiên) và bật bot.` : "Đã bật bot.");
        await loadDashboard();
      } catch (e) {
        if (e.status === 401) return logout("Phiên admin hết hạn. Đăng nhập lại.");
        showToast(e.message, true);
        event.currentTarget.disabled = false;
      }
    }
  });
  const stopButton = h("button", {
    class: "danger",
    text: "Stop Bot",
    disabled: !data.running,
    onclick: async (event) => {
      event.currentTarget.disabled = true;
      try {
        await api("/adminbot/stop", { method: "POST", body: {} });
        showToast("Bot đã dừng; dữ liệu đã ghi vẫn được giữ nguyên.");
        await loadDashboard();
      } catch (e) {
        if (e.status === 401) return logout("Phiên admin hết hạn. Đăng nhập lại.");
        showToast(e.message, true);
        event.currentTarget.disabled = false;
      }
    }
  });

  const studentRows = data.users.filter((user) => user.role === "STUDENT").map((user, index) => h("tr", {},
    h("td", { text: String(index + 1) }),
    h("td", { text: user.fullName }),
    h("td", { text: user.email }),
    h("td", { text: user.created ? "Đã tạo" : "Chờ tạo" }),
    h("td", { text: String(user.completedSessions || 0) }),
    h("td", { text: `${user.focusMinutes || 0} phút` })
  ));
  const roster = h("div", { class: "table-wrap" }, h("table", {},
    h("thead", {}, h("tr", {},
      h("th", { text: "#" }), h("th", { text: "Học sinh" }), h("th", { text: "Email alias" }),
      h("th", { text: "Tài khoản" }), h("th", { text: "Phiên xong" }), h("th", { text: "Tập trung" })
    )),
    h("tbody", {}, studentRows)));

  appRoot.replaceChildren(
    h("header", { class: "topbar" },
      h("strong", { text: "POP AI · Adminbot" }),
      h("nav", {}, h("a", { href: "/sysadmin", text: "Admin hệ thống" }), h("a", { href: "/admin", text: "Trang giáo viên" }),
        h("button", { class: "ghost", text: "Đăng xuất", onclick: () => logout() }))),
    h("main", { class: "wrap" },
      h("div", { class: "banner" },
        h("strong", { text: "DỮ LIỆU MÔ PHỎNG — không phải hoạt động học thật." }),
        h("div", { class: "small", text: "Cohort riêng: THPT Tây Đô · lớp 11TN2. Email alias dùng miền .invalid nên không nhận thư. Stop chỉ dừng tạo mới, không xóa phiên đã ghi." })),
      h("section", { class: "card" },
        h("div", { class: "row" },
          h("div", {}, h("h1", { text: "Điều khiển cohort" }),
            h("div", { class: "muted small", text: `Giáo viên: ${data.teacher.fullName} · ${data.teacher.email}` })),
          status),
        h("div", { class: "grid" },
          stat("Học sinh", String(countStudents), data.provisioned ? "đã thuộc lớp 11TN2" : "danh sách chờ Provision"),
          stat("Phiên mô phỏng", String(data.syntheticSessions || 0), "đã ghi vào database"),
          stat("Tổng thời gian", hours, "phiên hoàn tất mô phỏng"),
          stat("Lần sinh gần nhất", displayTime(data.lastGeneratedAt), data.running ? "bot kiểm tra mỗi 15 giây; tối đa 1 phiên/5 phút" : "bot đang dừng")),
        h("div", { class: "actions" }, provisionButton, rotateButton, startButton, stopButton),
        h("p", { class: "muted small", text: `Trạng thái nền: ${data.seededAt ? `đã khởi tạo ${displayTime(data.seededAt)}` : "chưa khởi tạo"}. Lần Start đầu tạo phiên giả rải trong 7 ngày gần nhất.` })),
      h("section", { class: "card section-gap" },
        h("h2", { text: "Danh sách học sinh" }),
        h("p", { class: "muted small", text: "Mật khẩu chỉ được tải dạng CSV khi Provision. Nếu trình duyệt làm mất file, dùng ‘Đặt lại mật khẩu & tải CSV’; thao tác đó vô hiệu hóa toàn bộ mật khẩu cũ và thu hồi phiên đăng nhập." }),
        roster),
      h("section", { class: "notice small" },
        "Giáo viên đăng nhập ở ", h("a", { href: "/admin", text: "trang giáo viên" }),
        " bằng email/mật khẩu trong CSV và cổng mật khẩu giáo viên `TEACHER_ADMIN_PASSWORD` trên Render. Chỉ dùng cohort này để demo; các tài khoản học sinh là tài khoản mô phỏng."))
  );
  if (data.running) pollTimer = setInterval(() => loadDashboard().catch(handleError), 30_000);
}

function handleError(error) {
  if (error.status === 401) return logout("Phiên admin hết hạn. Đăng nhập lại.");
  showToast(error.message, true);
}

async function loadDashboard() {
  try {
    const data = await api("/adminbot/state");
    renderDashboard(data);
  } catch (error) {
    if (error.status === 401) return logout("Phiên admin hết hạn. Đăng nhập lại.");
    if (!state.token) return renderLogin(error.message);
    showToast(error.message, true);
  }
}

if (state.token) loadDashboard();
else renderLogin();
