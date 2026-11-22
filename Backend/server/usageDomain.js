// Hàm thuần cho thống kê TikTok/YouTube: kiểm tra dữ liệu học sinh gửi lên và tổng hợp cho giáo viên.

export const USAGE_APPS = ["tiktok", "youtube"];
const MAX_ENTRIES = 14;

/** Ngày YYYY-MM-DD hợp lệ, không ở tương lai quá 1 ngày (lệch múi giờ) và không quá 30 ngày trước. */
export function isValidUsageDate(value, now = Date.now()) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const time = Date.parse(`${value}T00:00:00Z`);
  if (Number.isNaN(time) || new Date(time).toISOString().slice(0, 10) !== value) return false;
  return time <= now + 36 * 3600 * 1000 && time >= now - 30 * 86400000;
}

/** Trả { entries } hoặc { error }. Mỗi phần tử: { date, app, minutes }. */
export function parseUsageUpload(body, now = Date.now()) {
  const list = body && Array.isArray(body.entries) ? body.entries : null;
  if (!list || list.length === 0) return { error: "Thiếu danh sách entries." };
  if (list.length > MAX_ENTRIES * USAGE_APPS.length) return { error: "Gửi quá nhiều bản ghi." };
  const entries = [];
  for (const item of list) {
    const app = item && typeof item.app === "string" ? item.app : "";
    const minutes = item ? item.minutes : undefined;
    if (!USAGE_APPS.includes(app)) return { error: "Ứng dụng không hợp lệ." };
    if (!isValidUsageDate(item.date, now)) return { error: "Ngày không hợp lệ." };
    if (!Number.isInteger(minutes) || minutes < 0 || minutes > 1440) return { error: "Số phút không hợp lệ." };
    entries.push({ date: item.date, app, minutes });
  }
  return { entries };
}

const round1 = (n) => Math.round(n * 10) / 10;

/**
 * rows: [{ userId, fullName, email, tiktok, youtube, lastReport }] (phút đã cộng dồn trong kỳ).
 * Trả tổng hợp cả lớp.
 */
export function summarizeUsage(rows, days) {
  const count = rows.length;
  const tiktok = rows.reduce((s, r) => s + r.tiktok, 0);
  const youtube = rows.reduce((s, r) => s + r.youtube, 0);
  const reporting = rows.filter((r) => r.lastReport).length;
  const perDay = (total) => (count > 0 && days > 0 ? round1(total / count / days) : 0);
  return {
    studentCount: count,
    reportingStudents: reporting,
    tiktokMinutes: tiktok,
    youtubeMinutes: youtube,
    totalMinutes: tiktok + youtube,
    tiktokAvgPerStudentPerDay: perDay(tiktok),
    youtubeAvgPerStudentPerDay: perDay(youtube)
  };
}

/** Xếp học sinh theo tổng phút giảm dần; người chưa có dữ liệu xuống cuối. */
export function rankByUsage(rows) {
  return [...rows]
    .map((r) => ({ ...r, totalMinutes: r.tiktok + r.youtube }))
    .sort((a, b) => b.totalMinutes - a.totalMinutes || a.fullName.localeCompare(b.fullName, "vi"));
}

/** Đủ `days` ngày kết thúc ở `endDate` (YYYY-MM-DD), ngày thiếu = 0. */
export function fillUsageDays(rows, endDate, days) {
  const map = new Map(rows.map((r) => [r.date, r]));
  const end = Date.parse(`${endDate}T00:00:00Z`);
  return Array.from({ length: days }, (_, i) => {
    const date = new Date(end - (days - 1 - i) * 86400000).toISOString().slice(0, 10);
    const row = map.get(date);
    return { date, tiktok: row ? row.tiktok : 0, youtube: row ? row.youtube : 0 };
  });
}
