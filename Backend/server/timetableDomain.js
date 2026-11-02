import { createHash, createHmac, randomBytes, timingSafeEqual } from "node:crypto";

// ---------------------------------------------------------------------------
// Hằng số và kiểm tra dữ liệu thời khóa biểu (hàm thuần, có test)
// ---------------------------------------------------------------------------
export const WEEKDAYS = [2, 3, 4, 5, 6];
export const SESSIONS = ["MORNING", "AFTERNOON"];
export const MAX_PERIODS = 5;
export const MAX_SUBJECT_LENGTH = 40;

const WEEKDAY_LABEL = { 2: "Thứ 2", 3: "Thứ 3", 4: "Thứ 4", 5: "Thứ 5", 6: "Thứ 6" };
const SESSION_LABEL = { MORNING: "Sáng", AFTERNOON: "Chiều" };
const SESSION_ORDER = { MORNING: 0, AFTERNOON: 1 };

export const weekdayLabel = (weekday) => WEEKDAY_LABEL[weekday] ?? `Thứ ${weekday}`;

/**
 * Kiểm tra danh sách tiết học gửi từ trang admin: [{ weekday, session, period, subject }].
 * Ô trống (subject rỗng) bị bỏ qua. Trả { entries } đã chuẩn hoá và sắp xếp, hoặc { error }.
 */
export function validateTimetable(input) {
  if (!Array.isArray(input)) return { error: "Thời khóa biểu không hợp lệ." };
  if (input.length > WEEKDAYS.length * SESSIONS.length * MAX_PERIODS) {
    return { error: "Thời khóa biểu có quá nhiều tiết." };
  }
  const seen = new Set();
  const entries = [];
  for (const raw of input) {
    const weekday = raw?.weekday;
    const session = raw?.session;
    const period = raw?.period;
    if (!WEEKDAYS.includes(weekday)) return { error: "Ngày học phải từ Thứ 2 đến Thứ 6." };
    if (!SESSIONS.includes(session)) return { error: "Buổi học phải là sáng hoặc chiều." };
    if (!Number.isInteger(period) || period < 1 || period > MAX_PERIODS) {
      return { error: `Mỗi buổi chỉ có tối đa ${MAX_PERIODS} tiết.` };
    }
    const subject = typeof raw.subject === "string" ? raw.subject.replace(/\s+/g, " ").trim() : "";
    if (!subject) continue;
    if (subject.length > MAX_SUBJECT_LENGTH) {
      return { error: `Tên môn học tối đa ${MAX_SUBJECT_LENGTH} ký tự.` };
    }
    const key = `${weekday}:${session}:${period}`;
    if (seen.has(key)) return { error: "Có tiết học bị khai báo trùng." };
    seen.add(key);
    entries.push({ weekday, session, period, subject });
  }
  entries.sort((a, b) =>
    a.weekday - b.weekday || SESSION_ORDER[a.session] - SESSION_ORDER[b.session] || a.period - b.period);
  return { entries };
}

/** Cấu hình gửi: { sendTime: "HH:MM", enabled: boolean }. */
export function validateSettings(input) {
  const sendTime = input?.sendTime;
  if (typeof sendTime !== "string" || !/^([01]\d|2[0-3]):[0-5]\d$/.test(sendTime)) {
    return { error: "Giờ gửi phải có dạng HH:MM (00:00 - 23:59)." };
  }
  if (typeof input.enabled !== "boolean") return { error: "Trạng thái bật/tắt không hợp lệ." };
  return { sendTime, enabled: input.enabled };
}

const NOTIFICATION_BODY_MAX = 500;
const NOTIFICATION_TITLE_MAX = 80;

/**
 * Soạn thông báo "môn học hôm nay" từ các tiết của đúng ngày đó.
 * Trả null nếu hôm đó lớp không có tiết nào (khi đó không gửi gì).
 */
export function buildDailyMessage(weekday, entries) {
  const today = entries.filter((entry) => entry.weekday === weekday);
  if (today.length === 0) return null;
  const lines = [];
  for (const session of SESSIONS) {
    const items = today
      .filter((entry) => entry.session === session)
      .sort((a, b) => a.period - b.period)
      .map((entry) => `Tiết ${entry.period}: ${entry.subject}`);
    if (items.length > 0) lines.push(`${SESSION_LABEL[session]}: ${items.join(" · ")}`);
  }
  const title = `Thời khóa biểu ${weekdayLabel(weekday)}`.slice(0, NOTIFICATION_TITLE_MAX);
  let body = lines.join("\n");
  if (body.length > NOTIFICATION_BODY_MAX) body = `${body.slice(0, NOTIFICATION_BODY_MAX - 1)}…`;
  return { title, body };
}

// ---------------------------------------------------------------------------
// Ngày giờ theo múi giờ của trường
// ---------------------------------------------------------------------------
const JS_WEEKDAY_TO_SCHOOL = { Mon: 2, Tue: 3, Wed: 4, Thu: 5, Fri: 6, Sat: 7, Sun: 8 };

/** Trả { date: "YYYY-MM-DD", weekday: 2..8 (Thứ 2 = 2, Chủ nhật = 8), minutes: phút kể từ 00:00 } tại múi giờ đó. */
export function localClock(now, timeZone) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone, hourCycle: "h23", weekday: "short",
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"
  }).formatToParts(now);
  const get = (type) => parts.find((part) => part.type === type)?.value;
  return {
    date: `${get("year")}-${get("month")}-${get("day")}`,
    weekday: JS_WEEKDAY_TO_SCHOOL[get("weekday")],
    minutes: Number(get("hour")) * 60 + Number(get("minute"))
  };
}

/** Trả ngày học kế tiếp cần gửi trước một ngày; Chủ nhật gửi cho Thứ 2. */
export function nextTimetableDay(clock) {
  const nextWeekday = { 2: 3, 3: 4, 4: 5, 5: 6, 8: 2 }[clock.weekday];
  if (!nextWeekday) return null;
  const nextDate = new Date(`${clock.date}T00:00:00Z`);
  nextDate.setUTCDate(nextDate.getUTCDate() + 1);
  return { date: nextDate.toISOString().slice(0, 10), weekday: nextWeekday };
}

export const sendTimeToMinutes = (sendTime) => {
  const [hour, minute] = sendTime.split(":").map(Number);
  return hour * 60 + minute;
};

/** Hệ thống chỉ gửi bù trong khoảng này sau giờ gửi (ví dụ máy chủ khởi động lại muộn). */
export const CATCH_UP_WINDOW_MINUTES = 4 * 60;

/** Có nên gửi lịch của ngày kế tiếp ở thời điểm này không (chưa xét lớp đã gửi hay chưa). */
export function isWithinSendWindow(clock, sendTime) {
  if (!nextTimetableDay(clock)) return false;
  const start = sendTimeToMinutes(sendTime);
  return clock.minutes >= start && clock.minutes < start + CATCH_UP_WINDOW_MINUTES;
}

// ---------------------------------------------------------------------------
// Phiên đăng nhập admin hệ thống (tách biệt với cổng mật khẩu giáo viên)
// Token không lưu DB: "hạn.nonce.chữ ký HMAC". Khoá có tiền tố riêng nên token giáo viên không dùng được ở đây và ngược lại.
// ---------------------------------------------------------------------------
export const ADMIN_TTL_MS = 8 * 60 * 60 * 1000;

const sha256 = (text) => createHash("sha256").update(text, "utf8").digest();

export function safeEqualText(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  return timingSafeEqual(sha256(a), sha256(b));
}

const adminKey = (password, secret) =>
  secret ? sha256(`pop-sysadmin-secret:${secret}`) : sha256(`pop-sysadmin:${password}`);

export function issueAdminToken(password, { secret, now = Date.now() } = {}) {
  const expiresAt = now + ADMIN_TTL_MS;
  const payload = `${expiresAt}.${randomBytes(12).toString("base64url")}`;
  const signature = createHmac("sha256", adminKey(password, secret)).update(payload).digest("base64url");
  return { token: `${payload}.${signature}`, expiresAt };
}

export function verifyAdminToken(token, password, { secret, now = Date.now() } = {}) {
  if (!password || typeof token !== "string" || token.length > 200) return false;
  const parts = token.split(".");
  if (parts.length !== 3) return false;
  const [expiresText, nonce, signature] = parts;
  const expiresAt = Number(expiresText);
  if (!Number.isFinite(expiresAt) || expiresAt < now || !nonce) return false;
  const expected = createHmac("sha256", adminKey(password, secret))
    .update(`${expiresText}.${nonce}`).digest("base64url");
  return safeEqualText(expected, signature);
}
