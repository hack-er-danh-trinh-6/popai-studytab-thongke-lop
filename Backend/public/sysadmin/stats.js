"use strict";

// Trang "Thống kê" của sysadmin: bảng + biểu đồ SVG tự vẽ (không dùng thư viện ngoài vì CSP chỉ cho script 'self').
// Dùng chung h(), api(), state, toast(), handleFatal() từ app.js. Chỉ dùng textContent/thuộc tính SVG, không innerHTML.

const SVG_NS = "http://www.w3.org/2000/svg";
const nf = new Intl.NumberFormat("vi-VN");
const GROUP_NAMES = {
  VN: "Thói quen xem video ngắn",
  PT: "Mức độ phụ thuộc thiết bị số",
  PB: "Biểu hiện phân mảnh chú ý",
  TT: "Khả năng tập trung sâu"
};
const GROUP_KEYS = ["VN", "PT", "PB", "TT"];
const ROLE_LABELS = { STUDENT: "Học sinh", TEACHER: "Giáo viên" };
const FOCUS_STATUS_LABELS = { COMPLETED: "Hoàn thành", PAUSED: "Tạm dừng", RUNNING: "Đang chạy", NOT_STARTED: "Chưa bắt đầu" };
const MEMBER_STATUS_LABELS = { APPROVED: "Đã duyệt", PENDING: "Chờ duyệt", REJECTED: "Từ chối" };
const STATS_TABS = [
  ["overview", "Tổng quan"],
  ["users", "Người dùng"],
  ["assessment", "Đánh giá"],
  ["focus", "Tập trung"],
  ["journal", "Nhật ký & kế hoạch"],
  ["classes", "Lớp học"],
  ["list", "Danh sách chi tiết"]
];

let statsRoot = null;

function statsState() {
  if (!state.stats) {
    state.stats = {
      range: 30, tab: "overview", overview: null, surveys: null, loading: false, error: null,
      classSort: { key: "students", dir: "desc" },
      list: { q: "", role: "", sort: "createdAt", dir: "desc", page: 1, pageSize: 20, data: null, loading: false, detail: null }
    };
  }
  return state.stats;
}

// ---------- Định dạng ----------
const fmtNum = (value) => (value === null || value === undefined ? "—" : nf.format(value));
const fmtDec = (value) => (value === null || value === undefined ? "—" : nf.format(Math.round(Number(value) * 10) / 10));
const fmtPct = (part, total) => (total > 0 ? `${Math.round((part * 100) / total)}%` : "—");
function fmtMin(minutes) {
  if (minutes === null || minutes === undefined) return "—";
  if (minutes < 60) return `${nf.format(minutes)} phút`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${nf.format(hours)} giờ ${rest} phút` : `${nf.format(hours)} giờ`;
}
const dayLabel = (iso) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
function fmtDateTime(iso, withTime = true) {
  if (!iso) return "—";
  const options = { day: "2-digit", month: "2-digit", year: "numeric", ...(withTime ? { hour: "2-digit", minute: "2-digit" } : {}) };
  const zone = state.stats?.overview?.timeZone;
  try { return new Intl.DateTimeFormat("vi-VN", { ...options, timeZone: zone }).format(new Date(iso)); }
  catch { return new Intl.DateTimeFormat("vi-VN", options).format(new Date(iso)); }
}
const scoreText = (scores) => (scores ? GROUP_KEYS.map((key) => `${key} ${fmtDec(scores[key])}`).join(" · ") : "—");

// ---------- SVG ----------
function svgEl(tag, attrs = {}, ...children) {
  const el = document.createElementNS(SVG_NS, tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === "text") el.textContent = value;
    else el.setAttribute(key, String(value));
  }
  for (const child of children.flat()) if (child) el.append(child);
  return el;
}
const tip = (text) => svgEl("title", { text });

function niceMax(value) {
  if (!(value > 0)) return 1;
  const exp = 10 ** Math.floor(Math.log10(value));
  const f = value / exp;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * exp;
}
function axisTicks(max) {
  const count = max <= 4 && Number.isInteger(max) ? max : 4;
  return Array.from({ length: count + 1 }, (_, i) => (max * i) / count);
}
const hasData = (values) => values.some((value) => Number(value) > 0);
const emptyNote = (text = "Chưa có dữ liệu trong khoảng thời gian này.") => h("p", { class: "empty small", text });
const clip = (text, max) => (text.length > max ? `${text.slice(0, max - 1)}…` : text);

function gridAndAxis(svg, { W, L, R, T, ih }, max, format) {
  for (const tick of axisTicks(max)) {
    const y = T + ih - (ih * tick) / max;
    svg.append(svgEl("line", { x1: L, x2: W - R, y1: y, y2: y, class: "grid" }));
    svg.append(svgEl("text", { x: L - 6, y: y + 4, "text-anchor": "end", class: "ax", text: format(tick) }));
  }
}

function columnChart({ data, color = 0, unit = "", label }) {
  if (!hasData(data.map((d) => d.value))) return emptyNote();
  const dims = { W: 640, H: 220, L: 44, R: 8, T: 10, B: 28 };
  dims.iw = dims.W - dims.L - dims.R; dims.ih = dims.H - dims.T - dims.B;
  const max = niceMax(Math.max(...data.map((d) => d.value)));
  const svg = svgEl("svg", { viewBox: `0 0 ${dims.W} ${dims.H}`, class: "chart", role: "img", "aria-label": label });
  gridAndAxis(svg, dims, max, fmtNum);
  const bw = dims.iw / data.length;
  const every = Math.ceil(data.length / 8);
  data.forEach((d, i) => {
    const height = (dims.ih * d.value) / max;
    svg.append(svgEl("rect", {
      x: dims.L + i * bw + Math.min(3, bw * 0.15), y: dims.T + dims.ih - height,
      width: Math.max(1, bw - Math.min(6, bw * 0.3)), height, rx: 2, class: `f${color}`
    }, tip(`${d.label}: ${fmtNum(d.value)}${unit}`)));
    if (i % every === 0) svg.append(svgEl("text", { x: dims.L + i * bw + bw / 2, y: dims.H - 8, "text-anchor": "middle", class: "ax", text: d.label }));
  });
  return svg;
}

function lineChart({ labels, series, label }) {
  if (!series.some((item) => hasData(item.values.filter((v) => v !== null)))) return emptyNote();
  const dims = { W: 640, H: 220, L: 44, R: 12, T: 10, B: 28 };
  dims.iw = dims.W - dims.L - dims.R; dims.ih = dims.H - dims.T - dims.B;
  const max = niceMax(Math.max(...series.flatMap((item) => item.values.filter((v) => v !== null))));
  const svg = svgEl("svg", { viewBox: `0 0 ${dims.W} ${dims.H}`, class: "chart", role: "img", "aria-label": label });
  gridAndAxis(svg, dims, max, fmtNum);
  const step = labels.length > 1 ? dims.iw / (labels.length - 1) : 0;
  const xAt = (i) => dims.L + (labels.length > 1 ? i * step : dims.iw / 2);
  const yAt = (v) => dims.T + dims.ih - (dims.ih * v) / max;
  const every = Math.ceil(labels.length / 8);
  labels.forEach((text, i) => {
    if (i % every === 0) svg.append(svgEl("text", { x: xAt(i), y: dims.H - 8, "text-anchor": "middle", class: "ax", text }));
  });
  series.forEach((item, index) => {
    const color = item.color ?? index;
    let path = ""; let pen = false;
    item.values.forEach((v, i) => {
      if (v === null || v === undefined) { pen = false; return; }
      path += `${pen ? "L" : "M"}${xAt(i).toFixed(1)} ${yAt(v).toFixed(1)}`;
      pen = true;
    });
    svg.append(svgEl("path", { d: path, class: `line k${color}`, fill: "none" }));
    item.values.forEach((v, i) => {
      if (v === null || v === undefined) return;
      svg.append(svgEl("circle", { cx: xAt(i), cy: yAt(v), r: labels.length <= 31 ? 3 : 2, class: `f${color}` }, tip(`${labels[i]} · ${item.name}: ${fmtNum(v)}`)));
    });
  });
  return svg;
}

function hBarChart({ data, color = 0, format = fmtNum, label, maxValue }) {
  if (!data.length || !hasData(data.map((d) => d.count))) return emptyNote();
  const rowH = 30; const W = 440; const labelW = 150; const valueW = 70;
  const max = maxValue ?? Math.max(...data.map((d) => d.count));
  const svg = svgEl("svg", { viewBox: `0 0 ${W} ${data.length * rowH + 4}`, class: "chart", role: "img", "aria-label": label });
  data.forEach((d, i) => {
    const y = i * rowH + 2;
    const width = Math.max(d.count > 0 ? 2 : 0, ((W - labelW - valueW) * d.count) / max);
    svg.append(svgEl("text", { x: 0, y: y + 19, class: "ax label", text: clip(d.label, 24) }, tip(d.label)));
    svg.append(svgEl("rect", { x: labelW, y: y + 5, width, height: rowH - 12, rx: 3, class: `f${color}` }, tip(`${d.label}: ${format(d.count)}`)));
    svg.append(svgEl("text", { x: labelW + width + 6, y: y + 19, class: "ax val", text: format(d.count) }));
  });
  return svg;
}

function donutChart({ data, label }) {
  const total = data.reduce((sum, d) => sum + d.count, 0);
  if (total === 0) return emptyNote();
  const R = 60; const C = 2 * Math.PI * R;
  const svg = svgEl("svg", { viewBox: "0 0 160 160", class: "donut", role: "img", "aria-label": label });
  let offset = 0;
  data.forEach((d, i) => {
    if (d.count <= 0) return;
    const length = (C * d.count) / total;
    svg.append(svgEl("circle", {
      cx: 80, cy: 80, r: R, fill: "none", "stroke-width": 26, class: `k${i % 6}`,
      "stroke-dasharray": `${length} ${C - length}`, "stroke-dashoffset": -offset, transform: "rotate(-90 80 80)"
    }, tip(`${d.label}: ${fmtNum(d.count)} (${fmtPct(d.count, total)})`)));
    offset += length;
  });
  svg.append(svgEl("text", { x: 80, y: 78, "text-anchor": "middle", class: "donut-total", text: fmtNum(total) }));
  svg.append(svgEl("text", { x: 80, y: 96, "text-anchor": "middle", class: "ax", text: "tổng" }));
  const legend = h("ul", { class: "legend" }, data.map((d, i) => h("li", {},
    h("span", { class: `sw bg${i % 6}` }), `${d.label}: `, h("strong", { text: fmtNum(d.count) }), ` (${fmtPct(d.count, total)})`)));
  return h("div", { class: "donut-wrap" }, svg, legend);
}

function stackedBars({ rows, label }) {
  if (!rows.some((row) => hasData(row.values))) return emptyNote();
  const W = 440; const labelW = 150; const rowH = 34;
  const svg = svgEl("svg", { viewBox: `0 0 ${W} ${rows.length * rowH + 4}`, class: "chart", role: "img", "aria-label": label });
  rows.forEach((row, r) => {
    const total = row.values.reduce((sum, v) => sum + v, 0);
    const y = r * rowH + 2;
    svg.append(svgEl("text", { x: 0, y: y + 21, class: "ax label", text: row.label }, tip(row.title ?? row.label)));
    let x = labelW;
    row.values.forEach((value, level) => {
      if (value <= 0 || total === 0) return;
      const width = ((W - labelW) * value) / total;
      svg.append(svgEl("rect", { x, y: y + 4, width, height: rowH - 10, class: "f0", "fill-opacity": 0.2 + level * 0.2 },
        tip(`${row.title ?? row.label} · mức ${level + 1}: ${fmtNum(value)} người (${fmtPct(value, total)})`)));
      if (width > 22) svg.append(svgEl("text", { x: x + width / 2, y: y + 21, "text-anchor": "middle", class: level >= 3 ? "ax on-dark" : "ax", text: fmtNum(value) }));
      x += width;
    });
  });
  return svg;
}

// ---------- Thành phần giao diện ----------
const kpi = (title, value, sub) => h("div", { class: "kpi" },
  h("div", { class: "kpi-title", text: title }), h("div", { class: "kpi-value", text: value }), sub ? h("div", { class: "kpi-sub", text: sub }) : null);
const kpiRow = (...items) => h("div", { class: "kpis" }, items);
const card = (title, body, note) => h("section", { class: "card chart-card" },
  h("h3", { text: title }), note ? h("p", { class: "muted small", text: note }) : null, body);
const grid = (cols, ...items) => h("div", { class: `grid${cols}` }, items);
const legendLine = (items) => h("ul", { class: "legend inline" }, items.map((item) => h("li", {},
  h("span", { class: `sw bg${item.color}` }), item.name)));

function dataTable({ columns, rows, sort, dir, onSort, empty = "Không có dữ liệu.", onRow }) {
  const head = h("tr", {}, columns.map((col) => {
    const active = sort === col.key;
    const content = col.sort && onSort
      ? h("button", { class: "th-sort", type: "button", onclick: () => onSort(col.key) }, col.label, h("span", { class: "arrow", text: active ? (dir === "asc" ? " ▲" : " ▼") : "" }))
      : col.label;
    return h("th", { scope: "col", class: col.align, "aria-sort": active ? (dir === "asc" ? "ascending" : "descending") : undefined }, content);
  }));
  const body = rows.length === 0
    ? [h("tr", {}, h("td", { colspan: columns.length, class: "empty", text: empty }))]
    : rows.map((row) => {
      const tr = h("tr", { class: onRow ? "clickable" : "", tabindex: onRow ? "0" : undefined },
        columns.map((col) => h("td", { class: col.align }, col.render ? col.render(row) : (row[col.key] ?? "—"))));
      if (onRow) {
        tr.addEventListener("click", () => onRow(row));
        tr.addEventListener("keydown", (event) => { if (event.key === "Enter") onRow(row); });
      }
      return tr;
    });
  return h("div", { class: "table-wrap" }, h("table", { class: "data" }, h("thead", {}, head), h("tbody", {}, body)));
}

const twoLine = (main, sub) => h("div", {}, h("div", { text: main }), sub ? h("div", { class: "muted small", text: sub }) : null);

// ---------- Tải dữ liệu ----------
async function loadStatsOverview() {
  const st = statsState();
  st.loading = true; st.error = null; paintStats();
  try {
    [st.overview, st.surveys] = await Promise.all([
      api(`/stats/overview?days=${st.range}`),
      api("/stats/surveys")
    ]);
  } catch (error) {
    if (handleFatal(error)) return;
    st.error = error.message;
  } finally {
    st.loading = false;
  }
  paintStats();
}

async function loadStatsUsers() {
  const list = statsState().list;
  list.loading = true; paintUserResults();
  const params = new URLSearchParams({ q: list.q, role: list.role, sort: list.sort, dir: list.dir, page: String(list.page), pageSize: String(list.pageSize) });
  try {
    list.data = await api(`/stats/users?${params}`);
  } catch (error) {
    if (!handleFatal(error)) toast(error.message, true);
  } finally {
    list.loading = false;
  }
  paintUserResults();
}

async function openUserDetail(userId) {
  const list = statsState().list;
  try {
    list.detail = await api(`/stats/users/${userId}`);
  } catch (error) {
    if (!handleFatal(error)) toast(error.message, true);
    return;
  }
  paintUserResults();
  document.getElementById("userDetail")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

async function exportUsersCsv() {
  const list = statsState().list;
  const params = new URLSearchParams({ q: list.q, role: list.role, sort: list.sort, dir: list.dir, all: "1" });
  let data;
  try { data = await api(`/stats/users?${params}`); } catch (error) { if (!handleFatal(error)) toast(error.message, true); return; }
  const headers = ["Họ tên", "Email", "Vai trò", "Trường", "Lớp", "Ngày tạo", "Hoạt động gần nhất", "Số bài đánh giá", "VN", "PT", "PB", "TT",
    "Phiên tập trung", "Phiên hoàn thành", "Phút tập trung", "Lần gián đoạn", "Ngày có nhật ký", "Video ngắn TB (phút/ngày)", "Có kế hoạch"];
  const rows = data.users.map((u) => [u.fullName, u.email, ROLE_LABELS[u.role] ?? u.role, u.schoolName, u.className, fmtDateTime(u.createdAt, false),
    fmtDateTime(u.lastActive), u.assessmentCount, ...GROUP_KEYS.map((key) => u.latestScores?.[key]), u.focusSessions, u.completedSessions,
    u.focusMinutes, u.interruptions, u.journalDays, u.avgVideoMinutes, u.hasPlan ? "Có" : "Chưa"]);
  const csv = statsCsv(headers, rows);
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const link = h("a", { href: url, download: `pop-ai-nguoi-dung-${new Date().toISOString().slice(0, 10)}.csv` });
  document.body.append(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast(`Đã xuất ${data.users.length} người dùng.`);
}

// Chặn CSV/Excel formula injection (tên người dùng do người dùng tự nhập).
function statsCsv(headers, rows) {
  const cell = (value) => {
    if (value === null || value === undefined) return "";
    let text = String(value);
    if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
    return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  return `\uFEFF${[headers, ...rows].map((row) => row.map(cell).join(",")).join("\r\n")}`;
}

// ---------- Các tab ----------
const series = (rows, key) => rows.map((row) => row[key]);
const dayData = (rows, key) => rows.map((row) => ({ label: dayLabel(row.day), value: row[key] }));
const rangeNote = (st) => `${st.range} ngày gần nhất (múi giờ ${st.overview.timeZone})`;

function overviewTab(o, st) {
  const u = o.users;
  return h("div", { class: "stack" },
    kpiRow(
      kpi("Tổng người dùng", fmtNum(u.total), `${fmtNum(u.newInRange)} mới trong ${o.days} ngày`),
      kpi("Hoạt động 24 giờ", fmtNum(u.active1), fmtPct(u.active1, u.total) + " tổng số"),
      kpi("Hoạt động 7 ngày", fmtNum(u.active7), fmtPct(u.active7, u.total) + " tổng số"),
      kpi("Hoạt động 30 ngày", fmtNum(u.active30), fmtPct(u.active30, u.total) + " tổng số"),
      kpi("Chưa có hoạt động nào", fmtNum(u.neverActive), "đã đăng ký nhưng chưa dùng"),
      kpi("Phút tập trung", fmtNum(o.focus.completedMinutes), `${fmtNum(o.focus.sessions)} phiên · ${o.days} ngày`),
      kpi("Nhật ký đã ghi", fmtNum(o.journals.entries), `${fmtNum(o.journals.users)} người · ${o.days} ngày`),
      kpi("Bài đánh giá", fmtNum(o.assessments.total), `${fmtNum(o.assessments.users)} người đã làm`)),
    grid(2,
      card("Người dùng hoạt động mỗi ngày", lineChart({ labels: o.users.activePerDay.map((r) => dayLabel(r.day)), series: [{ name: "Người dùng hoạt động", values: series(u.activePerDay, "count"), color: 0 }], label: "Số người dùng hoạt động mỗi ngày" }),
        "Hoạt động = có phiên tập trung, nhật ký hoặc bài đánh giá trong ngày. " + rangeNote(st)),
      card("Đăng ký mới mỗi ngày", columnChart({ data: dayData(u.signupsPerDay, "count"), color: 1, unit: " người", label: "Số tài khoản đăng ký mới mỗi ngày" }), rangeNote(st))),
    grid(2,
      card("Phút tập trung hoàn thành mỗi ngày", columnChart({ data: dayData(o.focus.perDay, "minutes"), color: 4, unit: " phút", label: "Phút tập trung mỗi ngày" }), rangeNote(st)),
      card("Phễu kích hoạt (toàn thời gian)", hBarChart({ data: u.funnel, color: 5, label: "Số người dùng ở từng bước sử dụng" }),
        "Số người đã thực hiện từng hành động ít nhất một lần.")));
}

function usersTab(o) {
  const u = o.users;
  const inactive = Math.max(0, u.total - u.active30 - u.neverActive);
  return h("div", { class: "stack" },
    kpiRow(kpi("Tổng người dùng", fmtNum(u.total)), kpi("Học sinh", fmtNum(u.byRole.find((r) => r.label === "STUDENT")?.count ?? 0)),
      kpi("Giáo viên", fmtNum(u.byRole.find((r) => r.label === "TEACHER")?.count ?? 0)),
      kpi("Học sinh chưa vào lớp", fmtNum(o.classroom.studentsWithoutClass)),
      kpi("Tỉ lệ hoạt động 30 ngày", fmtPct(u.active30, u.total))),
    grid(2,
      card("Vai trò", donutChart({ data: u.byRole.map((r) => ({ label: ROLE_LABELS[r.label] ?? r.label, count: r.count })), label: "Phân bố vai trò" })),
      card("Mức độ hoạt động", donutChart({
        data: [{ label: "Hoạt động 30 ngày", count: u.active30 }, { label: "Không hoạt động 30 ngày", count: inactive }, { label: "Chưa từng hoạt động", count: u.neverActive }],
        label: "Phân bố mức độ hoạt động"
      }))),
    grid(2,
      card("Đăng ký mới mỗi ngày", columnChart({ data: dayData(u.signupsPerDay, "count"), color: 1, unit: " người", label: "Đăng ký mới mỗi ngày" })),
      card("Phễu kích hoạt", hBarChart({ data: u.funnel, color: 5, label: "Phễu kích hoạt" }))));
}

function assessmentTab(o, st) {
  const a = o.assessments;
  const survey = st.surveys;
  const avgRows = GROUP_KEYS.map((key) => ({ label: `${key} · ${GROUP_NAMES[key]}`, count: a.avg[key] ?? 0 }));
  return h("div", { class: "stack" },
    kpiRow(kpi("Lượt làm bài", fmtNum(a.total)), kpi("Người đã làm", fmtNum(a.users), fmtPct(a.users, o.users.total) + " tổng người dùng"),
      kpi("Trung bình lượt/người", a.users ? fmtDec(a.total / a.users) : "—")),
    grid(2,
      card("Điểm trung bình theo nhóm", hBarChart({ data: avgRows, color: 0, format: fmtDec, maxValue: 5, label: "Điểm trung bình từng nhóm trên thang 1 đến 5" }),
        "Dùng kết quả gần nhất của mỗi người (thang 1–5)."),
      card("Phân bố điểm theo nhóm", stackedBars({
        rows: GROUP_KEYS.map((key) => ({ label: key, title: GROUP_NAMES[key], values: a.distribution[key] })), label: "Phân bố điểm 1 đến 5 theo nhóm"
      }), "Mỗi thanh là số người ở từng mức 1 → 5 (nhạt → đậm).")),
    grid(2,
      card("Lượt làm bài mỗi ngày", columnChart({ data: dayData(a.perDay, "count"), color: 2, unit: " lượt", label: "Số lượt làm bài mỗi ngày" }), `${o.days} ngày gần nhất`),
      card("Ý nghĩa các nhóm", h("ul", { class: "plain" }, GROUP_KEYS.map((key) => h("li", {}, h("strong", { text: `${key}: ` }), GROUP_NAMES[key]))))),
    survey ? surveyRecordsCard(survey) : h("p", { class: "muted", text: "Đang tải dữ liệu từng phiếu…" }));
}

function surveyRecordsCard(survey) {
  const groups = ["VN", "PT", "PB", "TT", "DG"];
  const rows = survey.records.map((r) => h("tr", {},
    h("td", {}, h("strong", { text: r.fullName }), h("div", { class: "muted small", text: `${r.email} · ${r.className || "Chưa vào lớp"}` })),
    h("td", { text: r.phase === "PRE" ? "Trước" : "Sau" }),
    ...groups.map((g) => h("td", { text: r.scores?.[g] ?? "—" })),
    h("td", { text: fmtDateTime(r.completedAt) })));
  return h("div", { class: "card chart-card" }, h("h3", { text: "Từng phiếu khảo sát của tất cả người dùng" }),
    h("p", { class: "muted small", text: `Tổng ${fmtNum(survey.total)} phiếu · Trước: ${fmtNum(survey.phases.PRE)} · Sau: ${fmtNum(survey.phases.POST)}. Dữ liệu câu trả lời đầy đủ có trong API sysadmin.` }),
    h("div", { class: "table-wrap" }, h("table", { class: "data" }, h("thead", {}, h("tr", {}, ["Người dùng", "Đợt", ...groups, "Thời gian"].map((x) => h("th", { text: x })))), h("tbody", {}, rows))));
}

function focusTab(o, st) {
  const f = o.focus;
  const hours = f.byHour.map((count, hour) => ({ label: String(hour).padStart(2, "0"), value: count }));
  return h("div", { class: "stack" },
    kpiRow(kpi("Phiên tập trung", fmtNum(f.sessions), `${fmtNum(f.users)} người`), kpi("Tổng thời gian hoàn thành", fmtMin(f.completedMinutes)),
      kpi("Tỉ lệ đạt mục tiêu", f.completionRate ? `${f.completionRate}%` : "—", "phiên hoàn thành, so với thời lượng đặt"),
      kpi("Lần gián đoạn", fmtNum(f.interruptions), f.sessions ? `${fmtDec(f.interruptions / f.sessions)} / phiên` : null),
      kpi("Tự đánh giá TB", f.avgRating === null ? "—" : `${fmtDec(f.avgRating)} / 5`)),
    grid(2,
      card("Trạng thái phiên", donutChart({ data: f.byStatus.map((s) => ({ label: FOCUS_STATUS_LABELS[s.label] ?? s.label, count: s.count })), label: "Phân bố trạng thái phiên tập trung" }), rangeNote(st)),
      card("Tự đánh giá sau phiên", columnChart({ data: f.rating.map((count, i) => ({ label: `${i + 1} sao`, value: count })), color: 2, unit: " phiên", label: "Phân bố tự đánh giá từ 1 đến 5" }))),
    grid(2,
      card("Phút tập trung mỗi ngày", columnChart({ data: dayData(f.perDay, "minutes"), color: 4, unit: " phút", label: "Phút tập trung hoàn thành mỗi ngày" })),
      card("Phiên hoàn thành mỗi ngày", columnChart({ data: dayData(f.perDay, "completed"), color: 1, unit: " phiên", label: "Số phiên hoàn thành mỗi ngày" }))),
    grid(2,
      card("Giờ bắt đầu phiên trong ngày", columnChart({ data: hours, color: 5, unit: " phiên", label: "Số phiên theo giờ trong ngày" }), "Theo giờ địa phương (0–23)."),
      card("Thời lượng đặt cho phiên", hBarChart({ data: f.targetMinutes, color: 0, label: "Thời lượng mục tiêu của các phiên" }))),
    card("Lý do gián đoạn phổ biến", hBarChart({ data: f.reasons, color: 3, label: "Các lý do gián đoạn phổ biến" }), "Top 8 lý do người dùng ghi nhận."));
}

function journalTab(o) {
  const j = o.journals; const p = o.plans;
  return h("div", { class: "stack" },
    h("h2", { text: "Nhật ký" }),
    kpiRow(kpi("Bản ghi nhật ký", fmtNum(j.entries), `${fmtNum(j.users)} người`), kpi("Video ngắn TB", fmtMin(j.avgVideoMinutes), "mỗi người mỗi ngày"),
      kpi("Tập trung TB", fmtMin(j.avgFocusMinutes), "mỗi ngày"), kpi("Gián đoạn TB", fmtDec(j.avgInterruptions), "lần / ngày")),
    grid(2,
      card("Video ngắn và tập trung theo ngày (TB)", h("div", {},
        lineChart({ labels: j.perDay.map((r) => dayLabel(r.day)), series: [{ name: "Video ngắn (phút)", values: series(j.perDay, "avg_video").map((v, i) => (j.perDay[i].entries ? v : null)), color: 3 }, { name: "Tập trung (phút)", values: series(j.perDay, "avg_focus").map((v, i) => (j.perDay[i].entries ? v : null)), color: 1 }], label: "Trung bình phút video ngắn và phút tập trung theo ngày" }),
        legendLine([{ name: "Video ngắn", color: 3 }, { name: "Tập trung", color: 1 }]))),
      card("Phân bố thời gian xem video ngắn", columnChart({ data: j.videoBuckets.map((b) => ({ label: b.label.replace(" phút", ""), value: b.count })), color: 3, unit: " bản ghi", label: "Phân bố số phút xem video ngắn mỗi ngày" }), "Số bản ghi theo khoảng phút/ngày.")),
    card("Yếu tố gây xao nhãng", hBarChart({ data: j.distractors, color: 2, label: "Các yếu tố gây xao nhãng phổ biến" }), "Top 8 do người dùng chọn/ghi."),
    h("h2", { text: "Kế hoạch cá nhân" }),
    kpiRow(kpi("Kế hoạch đã lập", fmtNum(p.total), fmtPct(p.total, o.users.total) + " tổng người dùng"), kpi("Giới hạn video TB", fmtMin(p.avgLimitMinutes), "mỗi ngày")),
    grid(2,
      card("Giới hạn video ngắn mỗi ngày", columnChart({ data: p.limitBuckets.map((b) => ({ label: b.label.replace(" phút", ""), value: b.count })), color: 3, unit: " kế hoạch", label: "Phân bố giới hạn video ngắn trong kế hoạch" })),
      card("Mục tiêu số ngày", donutChart({ data: p.targetDays, label: "Phân bố mục tiêu số ngày" }))),
    grid(2,
      card("Thời lượng phiên trong kế hoạch", hBarChart({ data: p.sessionDuration, color: 4, label: "Thời lượng phiên trong kế hoạch" })),
      card("Nền tảng được theo dõi", hBarChart({ data: p.platforms, color: 5, label: "Nền tảng được theo dõi nhiều nhất" }), "Top 8 nền tảng.")));
}

function classesTab(o, st) {
  const c = o.classroom;
  const approved = c.members.find((m) => m.label === "APPROVED")?.count ?? 0;
  const sort = st.classSort;
  const rows = [...c.table].sort((a, b) => {
    const av = a[sort.key] ?? -1; const bv = b[sort.key] ?? -1;
    const result = typeof av === "string" ? av.localeCompare(bv, "vi") : av - bv;
    return sort.dir === "asc" ? result : -result;
  });
  const columns = [
    { key: "name", label: "Lớp", sort: true, render: (r) => twoLine(r.name, r.schoolName) },
    { key: "teacherName", label: "Giáo viên", sort: true },
    { key: "students", label: "Học sinh", sort: true, align: "num", render: (r) => fmtNum(r.students) },
    { key: "pending", label: "Chờ duyệt", sort: true, align: "num", render: (r) => fmtNum(r.pending) },
    { key: "activeStudents", label: `Có tập trung (${o.days}n)`, sort: true, align: "num", render: (r) => `${fmtNum(r.activeStudents)} (${fmtPct(r.activeStudents, r.students)})` },
    { key: "focusMinutes", label: "Phút tập trung", sort: true, align: "num", render: (r) => fmtNum(r.focusMinutes) },
    { key: "avgVideoMinutes", label: "Video TB (phút/ngày)", sort: true, align: "num", render: (r) => fmtNum(r.avgVideoMinutes) }
  ];
  return h("div", { class: "stack" },
    kpiRow(kpi("Trường", fmtNum(c.schools)), kpi("Lớp", fmtNum(c.classes)), kpi("Lớp đã có TKB", `${fmtNum(c.classesWithTimetable)} / ${fmtNum(c.classes)}`, fmtPct(c.classesWithTimetable, c.classes)),
      kpi("Học sinh đã vào lớp", fmtNum(approved)), kpi("Thông báo đã gửi", fmtNum(c.notifications), `${fmtNum(c.recipients)} lượt nhận`),
      kpi("Tỉ lệ đã đọc", fmtPct(c.read, c.recipients), `${fmtNum(c.read)} / ${fmtNum(c.recipients)}`)),
    grid(2,
      card("Trạng thái yêu cầu vào lớp", donutChart({ data: c.members.map((m) => ({ label: MEMBER_STATUS_LABELS[m.label] ?? m.label, count: m.count })), label: "Phân bố trạng thái yêu cầu vào lớp" })),
      card("Thông báo", hBarChart({ data: [{ label: "Lượt nhận", count: c.recipients }, { label: "Đã gửi tới máy", count: c.delivered }, { label: "Đã đọc", count: c.read }], color: 4, label: "Lượt nhận, đã gửi và đã đọc thông báo" }))),
    card("Chi tiết theo lớp", dataTable({
      columns, rows, sort: sort.key, dir: sort.dir, empty: "Chưa có lớp nào.",
      onSort: (key) => { st.classSort = { key, dir: sort.key === key && sort.dir === "desc" ? "asc" : "desc" }; paintStats(); }
    }), rangeNote(st)));
}

// ---------- Danh sách người dùng chi tiết ----------
function listTab() {
  const list = statsState().list;
  let timer;
  const search = h("input", {
    type: "text", id: "userSearch", placeholder: "Tìm theo tên hoặc email…", value: list.q, maxlength: "100", "aria-label": "Tìm người dùng",
    oninput: (event) => {
      list.q = event.target.value; list.page = 1;
      clearTimeout(timer); timer = setTimeout(loadStatsUsers, 300);
    }
  });
  const role = h("select", { "aria-label": "Lọc theo vai trò", onchange: (event) => { list.role = event.target.value; list.page = 1; loadStatsUsers(); } },
    [["", "Tất cả vai trò"], ["STUDENT", "Học sinh"], ["TEACHER", "Giáo viên"]].map(([value, text]) => h("option", { value, text, selected: list.role === value })));
  const toolbar = h("div", { class: "row card" },
    h("div", { class: "grow" }, h("label", { for: "userSearch", text: "Tìm kiếm" }), search),
    h("div", {}, h("label", { text: "Vai trò" }), role),
    h("button", { class: "secondary", text: "Xuất CSV", onclick: exportUsersCsv }));
  const results = h("div", { id: "userResults" });
  queueMicrotask(() => { paintUserResults(); if (!list.data && !list.loading) loadStatsUsers(); });
  return h("div", { class: "stack" }, toolbar, results);
}

function paintUserResults() {
  const box = document.getElementById("userResults");
  if (!box) return;
  const list = statsState().list;
  const columns = [
    { key: "name", label: "Người dùng", sort: true, render: (u) => twoLine(u.fullName, u.email) },
    { key: "role", label: "Vai trò", sort: true, align: "nowrap", render: (u) => ROLE_LABELS[u.role] ?? u.role },
    { key: "class", label: "Lớp / Trường", render: (u) => (u.className ? twoLine(u.className, u.schoolName) : "—") },
    { key: "createdAt", label: "Ngày tạo", sort: true, align: "nowrap", render: (u) => fmtDateTime(u.createdAt, false) },
    { key: "lastActive", label: "Hoạt động gần nhất", sort: true, align: "nowrap", render: (u) => fmtDateTime(u.lastActive) },
    { key: "assessments", label: "Đánh giá", sort: true, align: "num", render: (u) => twoLine(fmtNum(u.assessmentCount), u.latestScores ? scoreText(u.latestScores) : null) },
    { key: "focusMinutes", label: "Phút tập trung", sort: true, align: "num", render: (u) => fmtNum(u.focusMinutes) },
    { key: "completedSessions", label: "Phiên xong", sort: true, align: "num", render: (u) => `${fmtNum(u.completedSessions)} / ${fmtNum(u.focusSessions)}` },
    { key: "journalDays", label: "Ngày nhật ký", sort: true, align: "num", render: (u) => fmtNum(u.journalDays) },
    { key: "avgVideoMinutes", label: "Video TB (phút)", sort: true, align: "num", render: (u) => fmtNum(u.avgVideoMinutes) },
    { key: "plan", label: "Kế hoạch", align: "num", render: (u) => (u.hasPlan ? "✓" : "—") }
  ];
  const nodes = [];
  if (list.detail) nodes.push(renderUserDetail(list));
  if (!list.data) {
    nodes.push(h("p", { class: "empty", text: list.loading ? "Đang tải…" : "Chưa có dữ liệu." }));
  } else {
    const pages = Math.max(1, Math.ceil(list.data.total / list.pageSize));
    nodes.push(h("section", { class: "card" },
      dataTable({
        columns, rows: list.data.users, sort: list.sort, dir: list.dir, empty: "Không tìm thấy người dùng nào.",
        onSort: (key) => { list.dir = list.sort === key && list.dir === "desc" ? "asc" : "desc"; list.sort = key; list.page = 1; loadStatsUsers(); },
        onRow: (user) => openUserDetail(user.id)
      }),
      h("div", { class: "pager" },
        h("span", { class: "muted small", text: `${fmtNum(list.data.total)} người dùng · trang ${list.page}/${pages}${list.loading ? " · đang tải…" : ""}` }),
        h("div", { class: "actions" },
          h("button", { class: "ghost", text: "‹ Trước", disabled: list.page <= 1 || list.loading, onclick: () => { list.page -= 1; loadStatsUsers(); } }),
          h("button", { class: "ghost", text: "Sau ›", disabled: list.page >= pages || list.loading, onclick: () => { list.page += 1; loadStatsUsers(); } }))),
      h("p", { class: "muted small", text: "Nhấn vào một dòng để xem chi tiết. Chỉ hiển thị số liệu; nội dung nhật ký, nhiệm vụ và câu hỏi gửi POP-AI không được hiển thị." })));
  }
  box.replaceChildren(...nodes);
}

function renderUserDetail(list) {
  const d = list.detail; const u = d.user;
  const labels = d.focusPerDay.map((r) => dayLabel(r.day));
  const planText = d.plan
    ? `Giới hạn video ${fmtMin(d.plan.dailyShortVideoLimitMinutes)}/ngày · phiên ${fmtMin(d.plan.sessionDurationMinutes)} · mục tiêu ${d.plan.targetDays} ngày · theo dõi: ${d.plan.trackedPlatforms.join(", ") || "—"}`
    : "Chưa lập kế hoạch.";
  return h("section", { class: "card stack", id: "userDetail" },
    h("div", { class: "head" },
      h("div", {}, h("h2", { text: u.fullName }), h("p", { class: "muted small", text: `${u.email} · ${ROLE_LABELS[u.role] ?? u.role}${u.className ? ` · ${u.className} (${u.schoolName})` : ""}` }),
        h("p", { class: "muted small", text: `Tạo ${fmtDateTime(u.createdAt, false)} · hoạt động gần nhất ${fmtDateTime(u.lastActive)}` })),
      h("button", { class: "ghost", text: "Đóng", onclick: () => { list.detail = null; paintUserResults(); } })),
    kpiRow(kpi("Phút tập trung", fmtNum(u.focusMinutes)), kpi("Phiên hoàn thành", `${fmtNum(u.completedSessions)} / ${fmtNum(u.focusSessions)}`),
      kpi("Gián đoạn", fmtNum(u.interruptions)), kpi("Ngày có nhật ký", fmtNum(u.journalDays)), kpi("Video TB", u.avgVideoMinutes === null ? "—" : fmtMin(u.avgVideoMinutes))),
    h("p", { class: "hint", text: planText }),
    grid(2,
      card("Phút tập trung 30 ngày", columnChart({ data: d.focusPerDay.map((r) => ({ label: dayLabel(r.day), value: r.minutes })), color: 4, unit: " phút", label: "Phút tập trung 30 ngày gần nhất" })),
      card("Nhật ký 30 ngày", h("div", {},
        lineChart({ labels, series: [{ name: "Video ngắn", values: d.journalPerDay.map((r) => r.video), color: 3 }, { name: "Tập trung", values: d.journalPerDay.map((r) => r.focus), color: 1 }], label: "Phút video ngắn và tập trung theo nhật ký 30 ngày" }),
        legendLine([{ name: "Video ngắn", color: 3 }, { name: "Tập trung", color: 1 }])))),
    h("h3", { text: "Lịch sử đánh giá" }),
    dataTable({
      columns: [{ key: "completedAt", label: "Thời điểm", render: (r) => fmtDateTime(r.completedAt) },
        ...GROUP_KEYS.map((key) => ({ key, label: key, align: "num", render: (r) => fmtDec(r.scores?.[key]) }))],
      rows: d.assessments, empty: "Chưa làm bài đánh giá nào."
    }));
}

// ---------- Khung trang ----------
function paintStats() {
  if (!statsRoot) return;
  const st = statsState();
  const range = h("div", { class: "seg", role: "group", "aria-label": "Khoảng thời gian" },
    [7, 30, 90].map((days) => h("button", {
      type: "button", class: `seg-btn${st.range === days ? " active" : ""}`, "aria-pressed": st.range === days ? "true" : "false", text: `${days} ngày`,
      onclick: () => { if (st.range !== days) { st.range = days; loadStatsOverview(); } }
    })));
  const header = h("div", { class: "head" },
    h("div", {}, h("h2", { text: "Thống kê dữ liệu người dùng" }),
      h("p", { class: "muted small", text: st.overview ? `Cập nhật ${fmtDateTime(st.overview.generatedAt)} · số liệu tổng hợp, không hiển thị nội dung riêng tư` : "Số liệu tổng hợp, không hiển thị nội dung riêng tư" })),
    h("div", { class: "actions" }, range,
      h("button", { class: "secondary", text: st.loading ? "Đang tải…" : "Làm mới", disabled: st.loading, onclick: () => { loadStatsOverview(); if (st.tab === "list") loadStatsUsers(); } })));
  const tabs = h("div", { class: "subtabs", role: "tablist" }, STATS_TABS.map(([key, label]) => h("button", {
    type: "button", role: "tab", class: `subtab${st.tab === key ? " active" : ""}`, "aria-selected": st.tab === key ? "true" : "false", text: label,
    onclick: () => { st.tab = key; paintStats(); }
  })));

  let content;
  if (st.tab === "list") content = listTab();
  else if (st.error) content = h("div", { class: "error", text: st.error });
  else if (!st.overview) content = h("p", { class: "empty", text: "Đang tải số liệu…" });
  else {
    const builders = { overview: overviewTab, users: usersTab, assessment: assessmentTab, focus: focusTab, journal: journalTab, classes: classesTab };
    content = builders[st.tab](st.overview, st);
  }
  statsRoot.replaceChildren(header, tabs, h("div", { class: st.loading && st.overview ? "loading" : "" }, content));
}

function renderStatsView() {
  const box = h("div", { class: "stats-wrap" });
  statsRoot = box;
  const st = statsState();
  paintStats();
  if (!st.overview && !st.loading) loadStatsOverview();
  return box;
}
