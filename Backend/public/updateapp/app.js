"use strict";

const API = "/api/updateapp/v1";
// Dùng chung khoá phiên với /sysadmin: đã đăng nhập admin hệ thống thì vào thẳng.
const TOKEN_KEY = "sysadmin_token";
const store = {
  get(key) { try { return sessionStorage.getItem(key); } catch { return null; } },
  set(key, value) { try { sessionStorage.setItem(key, value); } catch { /* bỏ qua */ } },
  remove(key) { try { sessionStorage.removeItem(key); } catch { /* bỏ qua */ } }
};
let token = store.get(TOKEN_KEY) || "";

const $ = (id) => document.getElementById(id);
const fmtDateTime = (iso) => new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"
}).format(new Date(iso));

const fmtSize = (bytes) => {
  if (bytes == null) return "—";
  const units = ["B", "KB", "MB", "GB"];
  let size = Number(bytes);
  let u = 0;
  while (size >= 1024 && u < units.length - 1) { size /= 1024; u++; }
  return `${size.toFixed(u === 0 ? 0 : 2)} ${units[u]}`;
};

class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

async function api(path, { method = "GET", body } = {}) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;
  let response;
  try {
    response = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError("Không thể kết nối máy chủ. Vui lòng thử lại.", 0);
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(data.message || "Có lỗi xảy ra. Vui lòng thử lại.", response.status);
  return data;
}

// Gửi file nhị quan (multipart/form-data), không đặt Content-Type để trình duyệt tự gán boundary.
async function apiUpload(path, formData) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let response;
  try {
    response = await fetch(API + path, { method: "POST", headers, body: formData });
  } catch {
    throw new ApiError("Không thể kết nối máy chủ. Vui lòng thử lại.", 0);
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(data.message || "Có lỗi xảy ra. Vui lòng thử lại.", response.status);
  return data;
}

function showGate(message) {
  $("gate").hidden = false;
  $("panel").hidden = true;
  $("logout").hidden = true;
  $("gate-error").hidden = !message;
  $("gate-error").textContent = message || "";
}

function setStatus(message, bad = false) {
  const el = $("status");
  el.hidden = !message;
  el.textContent = message || "";
  el.className = `status${bad ? " bad" : ""}`;
}

function setUploadStatus(message, bad = false) {
  const el = $("upload-status");
  el.hidden = !message;
  el.textContent = message || "";
  el.className = `status${bad ? " bad" : ""}`;
}

function logout(message) {
  token = "";
  store.remove(TOKEN_KEY);
  showGate(message);
}

function updatePreview() {
  const version = $("version").value.trim();
  $("preview-title").textContent = "Có bản cập nhật POP AI mới";
  $("preview-body").textContent =
    `POP AI${version ? ` ${version}` : ""} đã sẵn sàng. Nhấn vào đây để mở App Hub và cập nhật phiên bản mới nhất.`;
}

function renderHistory(items) {
  const body = $("history");
  body.replaceChildren();
  if (items.length === 0) {
    const cell = document.createElement("td");
    cell.colSpan = 5;
    cell.className = "empty";
    cell.textContent = "Chưa gửi thông báo cập nhật nào.";
    const row = document.createElement("tr");
    row.append(cell);
    body.append(row);
    return;
  }
  for (const item of items) {
    const row = document.createElement("tr");
    const cells = [fmtDateTime(item.createdAt), item.body, item.recipientCount, item.deliveredCount, item.readCount];
    cells.forEach((value, index) => {
      const cell = document.createElement("td");
      if (index >= 2) cell.className = "num";
      cell.textContent = String(value);
      row.append(cell);
    });
    body.append(row);
  }
}

function renderVersions(items) {
  const body = $("versions");
  body.replaceChildren();
  if (items.length === 0) {
    const cell = document.createElement("td");
    cell.colSpan = 6;
    cell.className = "empty";
    cell.textContent = "Chưa có phiên bản APK nào được đăng ký.";
    const row = document.createElement("tr");
    row.append(cell);
    body.append(row);
    return;
  }
  for (const item of items) {
    const row = document.createElement("tr");
    const cells = [
      item.version,
      item.versionCode ?? "—",
      item.apkFileName,
      fmtSize(item.apkSize),
      item.isActive ? "Đang hoạt động" : "Đã ngừng",
      fmtDateTime(item.createdAt)
    ];
    cells.forEach((value, index) => {
      const cell = document.createElement("td");
      if (index === 3 || index === 5) cell.className = "num";
      cell.textContent = String(value);
      row.append(cell);
    });
    body.append(row);
  }
}

async function load() {
  try {
    const data = await api("/status");
    $("gate").hidden = true;
    $("panel").hidden = false;
    $("logout").hidden = false;
    $("user-count").textContent = new Intl.NumberFormat("vi-VN").format(data.userCount);
    $("link-url").textContent = data.linkUrl.replace(/^https:\/\//, "");
    renderHistory(data.history);
    renderVersions(data.versions || []);
    updatePreview();
  } catch (error) {
    if (error.status === 401 || error.status === 503) return logout(error.message);
    showGate(error.message);
  }
}

$("gate-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const data = await api("/login", { method: "POST", body: { password: $("gate-password").value } });
    token = data.token;
    store.set(TOKEN_KEY, token);
    $("gate-password").value = "";
    await load();
  } catch (error) {
    showGate(error.message);
  }
});

$("logout").addEventListener("click", () => logout());
$("version").addEventListener("input", updatePreview);

$("send").addEventListener("click", async () => {
  const count = $("user-count").textContent;
  if (!window.confirm(`Gửi thông báo cập nhật tới ${count} tài khoản? Không thể thu hồi sau khi gửi.`)) return;
  const button = $("send");
  button.disabled = true;
  setStatus("");
  try {
    const data = await api("/send", { method: "POST", body: { version: $("version").value.trim() } });
    setStatus(`Đã gửi thông báo tới ${new Intl.NumberFormat("vi-VN").format(data.recipientCount)} tài khoản.`);
    $("version").value = "";
    await load();
  } catch (error) {
    if (error.status === 401) return logout(error.message);
    setStatus(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$("upload-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const fileInput = $("upload-apk");
  if (!fileInput.files || fileInput.files.length === 0) {
    setUploadStatus("Vui lòng chọn file APK.", true);
    return;
  }
  const formData = new FormData();
  formData.append("apk", fileInput.files[0]);
  formData.append("version", $("upload-version").value.trim());
  const versionCode = $("upload-version-code").value.trim();
  if (versionCode) formData.append("versionCode", versionCode);

  const button = $("upload-btn");
  button.disabled = true;
  setUploadStatus("");
  try {
    const data = await apiUpload("/upload", formData);
    setUploadStatus(`Đã tải lên "${data.version}" (mã ${data.versionCode ?? "—"}) thành công.`);
    fileInput.value = "";
    $("upload-version").value = "";
    $("upload-version-code").value = "";
    await load();
  } catch (error) {
    setUploadStatus(error.message, true);
  } finally {
    button.disabled = false;
  }
});

if (token) load(); else showGate();
