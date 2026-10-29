import { randomUUID } from "node:crypto";
import { pool, query } from "./db.js";
import { validateTimeZone } from "./classroomDomain.js";
import { buildDailyMessage, isWithinSendWindow, localClock } from "./timetableDomain.js";

const DEFAULT_TIME_ZONE = "Asia/Ho_Chi_Minh";
const TICK_MS = 60 * 1000;

/** Múi giờ của trường (biến TIMETABLE_TIME_ZONE, mặc định Asia/Ho_Chi_Minh). */
export function schoolTimeZone() {
  return validateTimeZone(process.env.TIMETABLE_TIME_ZONE, DEFAULT_TIME_ZONE) ?? DEFAULT_TIME_ZONE;
}

export async function loadSettings() {
  const result = await query("SELECT send_time, enabled FROM pop_timetable_settings WHERE id = 1");
  const row = result.rows[0] ?? { send_time: "06:00", enabled: true };
  return { sendTime: row.send_time, enabled: row.enabled };
}

export async function saveSettings({ sendTime, enabled }) {
  await query(
    `INSERT INTO pop_timetable_settings (id, send_time, enabled) VALUES (1, $1, $2)
     ON CONFLICT (id) DO UPDATE SET send_time = EXCLUDED.send_time, enabled = EXCLUDED.enabled, updated_at = NOW()`,
    [sendTime, enabled]
  );
}

/**
 * Tạo thông báo "môn học hôm nay" cho học sinh đã được duyệt của một lớp.
 * Thông báo nằm trong hộp thư (pop_notifications, loại SYSTEM); app Android lấy về và hiện như thông báo điện thoại.
 *
 * - Tự động (force = false): mỗi lớp tối đa một lần mỗi ngày; khoá chính của pop_timetable_dispatches bảo đảm điều đó
 *   kể cả khi nhiều máy chủ cùng chạy hoặc máy chủ khởi động lại.
 * - Admin bấm gửi ngay (force = true): luôn gửi và ghi đè nhật ký ngày đó.
 *
 * Trả { status: "SENT" | "ALREADY_SENT" | "NO_LESSONS" | "NO_STUDENTS", recipientCount? }.
 */
export async function sendDailyTimetable(classId, { date, weekday, force = false }) {
  const entriesResult = await query(
    "SELECT weekday, session, period, subject FROM pop_timetable_entries WHERE class_id = $1 AND weekday = $2",
    [classId, weekday]
  );
  const message = buildDailyMessage(weekday, entriesResult.rows);
  if (!message) return { status: "NO_LESSONS" };

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    if (!force) {
      const claimed = await client.query(
        `INSERT INTO pop_timetable_dispatches (class_id, send_date) VALUES ($1, $2)
         ON CONFLICT (class_id, send_date) DO NOTHING RETURNING class_id`,
        [classId, date]
      );
      if (claimed.rowCount === 0) {
        await client.query("ROLLBACK");
        return { status: "ALREADY_SENT" };
      }
    }
    const members = await client.query(
      "SELECT user_id FROM pop_class_members WHERE class_id = $1 AND status = 'APPROVED'",
      [classId]
    );
    if (members.rowCount === 0) {
      await client.query("ROLLBACK");
      return { status: "NO_STUDENTS" };
    }
    const userIds = members.rows.map((row) => row.user_id);
    const notificationId = randomUUID();
    await client.query(
      `INSERT INTO pop_notifications (id, class_id, sender_id, kind, title, body)
       VALUES ($1, $2, NULL, 'SYSTEM', $3, $4)`,
      [notificationId, classId, message.title, message.body]
    );
    await client.query(
      `INSERT INTO pop_notification_recipients (notification_id, user_id)
       SELECT $1, unnest($2::uuid[])`,
      [notificationId, userIds]
    );
    await client.query(
      `INSERT INTO pop_timetable_dispatches (class_id, send_date, notification_id, recipient_count)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (class_id, send_date) DO UPDATE
         SET notification_id = EXCLUDED.notification_id, recipient_count = EXCLUDED.recipient_count, sent_at = NOW()`,
      [classId, date, notificationId, userIds.length]
    );
    await client.query("COMMIT");
    return { status: "SENT", recipientCount: userIds.length };
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

/** Một lượt kiểm tra của bộ lập lịch: gửi cho mọi lớp có tiết học hôm nay mà chưa được gửi. */
export async function runDailyDispatch(now = new Date()) {
  const settings = await loadSettings();
  if (!settings.enabled) return { sent: 0 };
  const clock = localClock(now, schoolTimeZone());
  if (!isWithinSendWindow(clock, settings.sendTime)) return { sent: 0 };

  const pending = await query(
    `SELECT DISTINCT e.class_id
       FROM pop_timetable_entries e
      WHERE e.weekday = $1
        AND NOT EXISTS (
          SELECT 1 FROM pop_timetable_dispatches d WHERE d.class_id = e.class_id AND d.send_date = $2::date
        )`,
    [clock.weekday, clock.date]
  );
  let sent = 0;
  for (const row of pending.rows) {
    try {
      const result = await sendDailyTimetable(row.class_id, { date: clock.date, weekday: clock.weekday });
      if (result.status === "SENT") sent += 1;
    } catch (error) {
      // Lỗi một lớp không chặn các lớp khác; lượt sau sẽ thử lại vì lớp đó chưa được ghi nhận đã gửi.
      console.error("timetable_dispatch_failed", { code: error?.code ?? "application_error" });
    }
  }
  return { sent };
}

export function startTimetableScheduler() {
  if (process.env.TIMETABLE_SCHEDULER === "off") {
    console.log("Timetable scheduler disabled (TIMETABLE_SCHEDULER=off)");
    return () => {};
  }
  let running = false;
  const tick = async () => {
    if (running) return;
    running = true;
    try {
      await runDailyDispatch();
    } catch (error) {
      console.error("timetable_scheduler_failed", { code: error?.code ?? "application_error" });
    } finally {
      running = false;
    }
  };
  const timer = setInterval(tick, TICK_MS);
  timer.unref();
  void tick();
  console.log(`Timetable scheduler started (time zone ${schoolTimeZone()})`);
  return () => clearInterval(timer);
}
