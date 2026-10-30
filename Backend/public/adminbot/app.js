const API = "/api/adminbot/v1";
const TOKEN_KEY = "pop.adminbot.gate";
let gateToken = sessionStorage.getItem(TOKEN_KEY) || "";

const $ = (id) => document.getElementById(id);
const setText = (id, text) => { $(id).textContent = text; };

const fmtNumber = (value) => new Intl.NumberFormat("vi-VN").format(value ?? 0);
const escapeHTML = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
})[char]);
const fmtDateTime = (iso) => {
  if (!iso) return "—";
  const d = new Date(iso);
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"
  }).format(d);
};

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (gateToken) headers["x-gate-token"] = gateToken;
  const response = await fetch(`${API}${path}`, { ...options, headers });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (response.status === 401 || response.status === 403) {
    sessionStorage.removeItem(TOKEN_KEY);
    gateToken = "";
    showGate("Phiên đăng nhập đã hết hạn. Vui lòng nhập lại mật khẩu admin.");
    throw new Error(data?.message ?? "Phiên đăng nhập đã hết hạn.");
  }
  if (!response.ok) throw new Error(data?.message ?? `Lỗi ${response.status}`);
  return data;
}

function showGate(message) {
  $("gate").hidden = false;
  $("panel").hidden = true;
  $("logout").hidden = true;
  const error = $("gate-error");
  error.hidden = !message;
  error.textContent = message || "";
}

function showPanel() {
  $("gate").hidden = true;
  $("panel").hidden = false;
  $("logout").hidden = false;
}

function setStatus(message, kind) {
  const el = $("status");
  el.hidden = !message;
  el.textContent = message || "";
  el.className = `status${kind ? ` ${kind}` : ""}`;
}

function busy(button, isBusy) {
  button.disabled = isBusy;
}

async function loadOverview() {
  const data = await api("/overview");
  setText("class-title", `Lớp ${data.klass.name} · ${data.klass.schoolName}`);
  setText("class-sub", `Giáo viên bot: ${data.teacher.fullName} (${data.teacher.email})`);

  const badge = $("state-badge");
  badge.textContent = data.state.isRunning ? "Đang chạy" : "Đang dừng";
  badge.className = `badge${data.state.isRunning ? " on" : ""}`;
  $("toggle-state").textContent = data.state.isRunning ? "Kết thúc" : "Bắt đầu";
  $("toggle-state").dataset.running = data.state.isRunning ? "1" : "0";

  setText("state-meta",
    `Seeded: ${fmtDateTime(data.state.seededAt)} · Tạo gần nhất: ${fmtDateTime(data.state.lastGeneratedAt)} · ` +
    `Tổng phiên đã sinh: ${fmtNumber(data.state.generatedSessions)}`);

  const metrics = [
    ["Học sinh bot", data.totals.students],
    ["Phiên tập trung", data.totals.sessions],
    ["Nhật ký", data.totals.journals],
    ["Bài tự đánh giá", data.totals.assessments],
    ["Kế hoạch cá nhân", data.totals.plans],
    ["Phút tập trung", data.totals.focusMinutes]
  ];
  $("totals").innerHTML = metrics
    .map(([label, value]) => `<div class="metric"><span>${label}</span><strong>${fmtNumber(value)}</strong></div>`)
    .join("");

  setText("student-count", `${data.students.length} tài khoản`);
  const rows = data.students.map((s, index) => `
    <tr>
      <td>${index + 1}</td>
      <td>${escapeHTML(s.fullName)}</td>
      <td>${escapeHTML(s.email)}</td>
      <td class="num">${fmtNumber(s.sessions)}</td>
      <td class="num">${fmtNumber(s.completedSessions)}</td>
      <td class="num">${fmtNumber(s.focusMinutes)}</td>
      <td class="num">${fmtNumber(s.journals)}</td>
      <td class="num">${fmtNumber(s.assessments)}</td>
      <td>${s.hasPlan ? "Có" : "—"}</td>
      <td>${fmtDateTime(s.lastActivity)}</td>
    </tr>`);
  $("students").innerHTML = rows.join("") ||
    `<tr><td colspan="10" class="empty">Chưa có tài khoản học sinh bot nào trong lớp.</td></tr>`;
}

const seedOptions = () => ({
  days: Number($("opt-days").value),
  sessionsPerDay: Number($("opt-sessions").value),
  activityRate: Number($("opt-rate").value) / 100,
  assessmentsPerStudent: Number($("opt-assessments").value),
  includeJournals: $("opt-journals").checked,
  includeAssessments: Number($("opt-assessments").value) > 0,
  includePlans: $("opt-plans").checked,
  resetFirst: $("opt-reset").checked,
  alignJoinDate: $("opt-align").checked
});

$("gate-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = event.submitter ?? $("gate-form").querySelector("button");
  busy(button, true);
  try {
    const data = await api("/gate", {
      method: "POST",
      body: JSON.stringify({ password: $("gate-password").value })
    });
    gateToken = data.gateToken;
    sessionStorage.setItem(TOKEN_KEY, gateToken);
    $("gate-password").value = "";
    showPanel();
    await loadOverview();
  } catch (error) {
    showGate(error.message);
  } finally {
    busy(button, false);
  }
});

$("logout").addEventListener("click", () => {
  sessionStorage.removeItem(TOKEN_KEY);
  gateToken = "";
  showGate("");
});

$("seed").addEventListener("click", async (event) => {
  const button = event.currentTarget;
  busy(button, true);
  setStatus("Đang tạo dữ liệu giả…");
  try {
    const result = await api("/seed", { method: "POST", body: JSON.stringify(seedOptions()) });
    const s = result.summary;
    setStatus(
      `Đã tạo cho ${s.students} học sinh: ${fmtNumber(s.sessions)} phiên tập trung, ` +
      `${fmtNumber(s.journals)} nhật ký, ${fmtNumber(s.assessments)} bài tự đánh giá, ${fmtNumber(s.plans)} kế hoạch.`,
      "ok"
    );
    await loadOverview();
  } catch (error) {
    setStatus(error.message, "err");
  } finally {
    busy(button, false);
  }
});

$("reset").addEventListener("click", async (event) => {
  const button = event.currentTarget;
  if (!window.confirm("Xoá toàn bộ phiên tập trung, nhật ký, bài tự đánh giá và kế hoạch của các tài khoản bot?")) return;
  busy(button, true);
  setStatus("Đang xoá dữ liệu bot…");
  try {
    const result = await api("/reset", { method: "POST", body: JSON.stringify({}) });
    setStatus(`Đã xoá dữ liệu của ${result.clearedStudents} tài khoản bot.`, "ok");
    await loadOverview();
  } catch (error) {
    setStatus(error.message, "err");
  } finally {
    busy(button, false);
  }
});

$("toggle-state").addEventListener("click", async (event) => {
  const button = event.currentTarget;
  const running = button.dataset.running !== "1";
  busy(button, true);
  try {
    await api("/state", { method: "POST", body: JSON.stringify({ running }) });
    await loadOverview();
  } catch (error) {
    setStatus(error.message, "err");
  } finally {
    busy(button, false);
  }
});

$("reload").addEventListener("click", async () => {
  try {
    await loadOverview();
    setStatus("Đã tải lại dữ liệu.", "ok");
  } catch (error) {
    setStatus(error.message, "err");
  }
});

(async () => {
  if (!gateToken) return showGate("");
  showPanel();
  try {
    await loadOverview();
  } catch (error) {
    setStatus(error.message, "err");
  }
})();
