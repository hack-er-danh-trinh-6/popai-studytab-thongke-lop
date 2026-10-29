// Hàm thuần cho trang thống kê sysadmin: kiểm tra tham số, tên cột sắp xếp (whitelist) và định dạng CSV.

export const RANGE_OPTIONS = [7, 30, 90];
export const DEFAULT_RANGE = 30;
export const ROLES = ["STUDENT", "TEACHER"];
export const GROUPS = ["VN", "PT", "PB", "TT"];

// Chỉ cho phép các cột này trong ORDER BY: không bao giờ ghép chuỗi người dùng gửi vào SQL.
export const USER_SORTS = {
  name: "u.full_name",
  email: "u.email",
  role: "u.role",
  createdAt: "u.created_at",
  lastActive: "last_active",
  assessments: "assessment_count",
  focusMinutes: "focus_minutes",
  completedSessions: "completed_sessions",
  journalDays: "journal_days",
  avgVideoMinutes: "avg_video_minutes"
};

export function parseRange(value) {
  const number = Number(value);
  return RANGE_OPTIONS.includes(number) ? number : DEFAULT_RANGE;
}

export function escapeLike(text) {
  return String(text).replace(/[\\%_]/g, (char) => `\\${char}`);
}

export function parseUserListQuery(params = {}) {
  const search = typeof params.q === "string" ? params.q.trim().slice(0, 100) : "";
  const role = ROLES.includes(params.role) ? params.role : "";
  const sort = Object.hasOwn(USER_SORTS, params.sort) ? params.sort : "createdAt";
  const dir = params.dir === "asc" ? "asc" : "desc";
  const all = params.all === "1";
  const pageSize = all ? 5000 : Math.min(100, Math.max(1, Math.trunc(Number(params.pageSize)) || 20));
  const page = all ? 1 : Math.max(1, Math.trunc(Number(params.page)) || 1);
  return { search, role, sort, dir, all, pageSize, page, offset: (page - 1) * pageSize };
}

export const round1 = (value) => (value === null || value === undefined ? null : Math.round(Number(value) * 10) / 10);

/** Làm tròn điểm trung bình (1-5) về nhóm nguyên 1..5 để vẽ phân bố. */
export function scoreBucket(score) {
  const number = Number(score);
  if (!Number.isFinite(number)) return null;
  return Math.min(5, Math.max(1, Math.round(number)));
}

/** Biến danh sách {bucket,count} thành mảng đủ các mốc, mốc thiếu = 0. */
export function fillBuckets(rows, keys) {
  const map = new Map(rows.map((row) => [String(row.bucket), Number(row.count)]));
  return keys.map((key) => ({ key: String(key), count: map.get(String(key)) ?? 0 }));
}

/** Chặn CSV/Excel formula injection: ô bắt đầu bằng = + - @ hoặc tab/CR sẽ được thêm dấu nháy đơn. */
export function csvCell(value) {
  if (value === null || value === undefined) return "";
  let text = String(value);
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(headers, rows) {
  const lines = [headers.map(csvCell).join(",")];
  for (const row of rows) lines.push(row.map(csvCell).join(","));
  return `\uFEFF${lines.join("\r\n")}`;
}
