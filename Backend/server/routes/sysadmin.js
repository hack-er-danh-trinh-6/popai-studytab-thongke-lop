import { Router } from "express";
import rateLimit from "express-rate-limit";
import { pool, query } from "../db.js";
import { isUuid, nameKey } from "../classroomDomain.js";
import {
  issueAdminToken, localClock, safeEqualText, validateSettings, validateTimetable, verifyAdminToken, WEEKDAYS
} from "../timetableDomain.js";
import { loadSettings, saveSettings, schoolTimeZone, sendDailyTimetable } from "../timetableService.js";
import { parseRange, parseUserListQuery } from "../statsDomain.js";
import { listUsers, loadOverview, loadUserDetail } from "../statsService.js";

// API của trang admin hệ thống (/sysadmin). Tách hoàn toàn khỏi API giáo viên: mật khẩu riêng SYSTEM_ADMIN_PASSWORD.
const router = Router();

const NOT_CONFIGURED = "Máy chủ chưa cấu hình mật khẩu admin hệ thống (SYSTEM_ADMIN_PASSWORD).";
const tokenOptions = () => ({ secret: process.env.SYSTEM_ADMIN_SECRET });

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { message: "Nhập sai mật khẩu quá nhiều lần. Vui lòng chờ rồi thử lại." }
});
const sendLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  limit: 60,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { message: "Bạn đã gửi quá nhiều thông báo trong một giờ. Vui lòng thử lại sau." }
});

router.post("/login", loginLimiter, (req, res) => {
  const password = process.env.SYSTEM_ADMIN_PASSWORD;
  if (!password) return res.status(503).json({ message: NOT_CONFIGURED });
  const supplied = req.body?.password;
  if (typeof supplied !== "string" || supplied.length > 200 || !safeEqualText(supplied, password)) {
    return res.status(401).json({ message: "Mật khẩu admin không đúng." });
  }
  const { token, expiresAt } = issueAdminToken(password, tokenOptions());
  return res.json({ token, expiresAt: new Date(expiresAt).toISOString() });
});

function requireAdmin(req, res, next) {
  const password = process.env.SYSTEM_ADMIN_PASSWORD;
  if (!password) return res.status(503).json({ message: NOT_CONFIGURED });
  const match = /^Bearer\s+(\S+)$/i.exec(req.get("authorization") ?? "");
  if (!match || !verifyAdminToken(match[1], password, tokenOptions())) {
    return res.status(401).json({ message: "Phiên đăng nhập admin đã hết hạn. Vui lòng đăng nhập lại." });
  }
  return next();
}
router.use(requireAdmin);

const classDto = (row) => ({
  id: row.id,
  name: row.name,
  schoolId: row.school_id,
  schoolName: row.school_name,
  teacherName: row.teacher_name,
  studentCount: row.student_count,
  lessonCount: row.lesson_count
});

router.get("/classes", async (req, res, next) => {
  try {
    const keyword = nameKey(typeof req.query.q === "string" ? req.query.q.slice(0, 100) : "");
    const result = await query(
      `SELECT c.id, c.name, c.school_id, s.name AS school_name, t.full_name AS teacher_name,
              (SELECT COUNT(*)::int FROM pop_class_members m WHERE m.class_id = c.id AND m.status = 'APPROVED') AS student_count,
              (SELECT COUNT(*)::int FROM pop_timetable_entries e WHERE e.class_id = c.id) AS lesson_count
         FROM pop_classes c
         JOIN pop_schools s ON s.id = c.school_id
         JOIN pop_users t ON t.id = c.teacher_id
        WHERE $1 = '' OR position($1 in c.name_key) > 0 OR position($1 in s.name_key) > 0
        ORDER BY s.name, c.name
        LIMIT 500`,
      [keyword]
    );
    return res.json(result.rows.map(classDto));
  } catch (error) {
    return next(error);
  }
});

async function loadClass(req, res) {
  if (!isUuid(req.params.classId)) {
    res.status(404).json({ message: "Không tìm thấy lớp." });
    return null;
  }
  const result = await query(
    `SELECT c.id, c.name, c.school_id, s.name AS school_name, t.full_name AS teacher_name,
            (SELECT COUNT(*)::int FROM pop_class_members m WHERE m.class_id = c.id AND m.status = 'APPROVED') AS student_count,
            (SELECT COUNT(*)::int FROM pop_timetable_entries e WHERE e.class_id = c.id) AS lesson_count
       FROM pop_classes c
       JOIN pop_schools s ON s.id = c.school_id
       JOIN pop_users t ON t.id = c.teacher_id
      WHERE c.id = $1`,
    [req.params.classId]
  );
  if (result.rowCount !== 1) {
    res.status(404).json({ message: "Không tìm thấy lớp." });
    return null;
  }
  return result.rows[0];
}

const entryDto = (row) => ({ weekday: row.weekday, session: row.session, period: row.period, subject: row.subject });

router.get("/classes/:classId/timetable", async (req, res, next) => {
  try {
    const klass = await loadClass(req, res);
    if (!klass) return;
    const entries = await query(
      `SELECT weekday, session, period, subject FROM pop_timetable_entries
        WHERE class_id = $1
        ORDER BY weekday, session DESC, period`,
      [klass.id]
    );
    return res.json({ class: classDto(klass), entries: entries.rows.map(entryDto) });
  } catch (error) {
    return next(error);
  }
});

// Thay toàn bộ thời khóa biểu của lớp bằng danh sách gửi lên (ô trống bị bỏ qua).
router.put("/classes/:classId/timetable", async (req, res, next) => {
  const client = await pool.connect();
  try {
    const klass = await loadClass(req, res);
    if (!klass) return;
    const parsed = validateTimetable(req.body?.entries);
    if (parsed.error) return res.status(400).json({ message: parsed.error });

    await client.query("BEGIN");
    await client.query("DELETE FROM pop_timetable_entries WHERE class_id = $1", [klass.id]);
    if (parsed.entries.length > 0) {
      await client.query(
        `INSERT INTO pop_timetable_entries (class_id, weekday, session, period, subject)
         SELECT $1, w, s, p, subj
           FROM unnest($2::smallint[], $3::text[], $4::smallint[], $5::text[]) AS t(w, s, p, subj)`,
        [
          klass.id,
          parsed.entries.map((entry) => entry.weekday),
          parsed.entries.map((entry) => entry.session),
          parsed.entries.map((entry) => entry.period),
          parsed.entries.map((entry) => entry.subject)
        ]
      );
    }
    await client.query("COMMIT");
    return res.json({ lessonCount: parsed.entries.length });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    return next(error);
  } finally {
    client.release();
  }
});

router.delete("/classes/:classId/timetable", async (req, res, next) => {
  try {
    const klass = await loadClass(req, res);
    if (!klass) return;
    await query("DELETE FROM pop_timetable_entries WHERE class_id = $1", [klass.id]);
    return res.status(204).end();
  } catch (error) {
    return next(error);
  }
});

const SEND_MESSAGES = {
  NO_LESSONS: "Lớp không có tiết học nào vào ngày này nên không có gì để gửi.",
  NO_STUDENTS: "Lớp chưa có học sinh nào được duyệt để nhận thông báo."
};

// Gửi ngay thông báo của một ngày Thứ 2-6 chọn trước tới học sinh lớp: dùng để thử hoặc gửi bù.
router.post("/classes/:classId/timetable/send-now", sendLimiter, async (req, res, next) => {
  try {
    const klass = await loadClass(req, res);
    if (!klass) return;
    const clock = localClock(new Date(), schoolTimeZone());
    const weekday = req.body?.weekday ?? clock.weekday;
    if (!WEEKDAYS.includes(weekday)) {
      return res.status(400).json({ message: "Hôm nay là cuối tuần. Hãy chọn một ngày từ Thứ 2 đến Thứ 6 để gửi thử." });
    }
    const result = await sendDailyTimetable(klass.id, { date: clock.date, weekday, force: true });
    if (result.status !== "SENT") {
      return res.status(409).json({ message: SEND_MESSAGES[result.status] ?? "Không gửi được thông báo." });
    }
    return res.status(201).json({ recipientCount: result.recipientCount });
  } catch (error) {
    return next(error);
  }
});

router.get("/settings", async (_req, res, next) => {
  try {
    return res.json({ ...(await loadSettings()), timeZone: schoolTimeZone() });
  } catch (error) {
    return next(error);
  }
});

router.put("/settings", async (req, res, next) => {
  try {
    const parsed = validateSettings(req.body);
    if (parsed.error) return res.status(400).json({ message: parsed.error });
    await saveSettings(parsed);
    return res.json({ sendTime: parsed.sendTime, enabled: parsed.enabled, timeZone: schoolTimeZone() });
  } catch (error) {
    return next(error);
  }
});

// ---------- Thống kê dữ liệu người dùng (chỉ số liệu tổng hợp, không trả nội dung tự do) ----------
router.get("/stats/overview", async (req, res, next) => {
  try {
    return res.json(await loadOverview(parseRange(req.query.days), schoolTimeZone()));
  } catch (error) {
    return next(error);
  }
});

router.get("/stats/users", async (req, res, next) => {
  try {
    const options = parseUserListQuery(req.query);
    const { total, users } = await listUsers(options);
    return res.json({ total, page: options.page, pageSize: options.pageSize, users });
  } catch (error) {
    return next(error);
  }
});

router.get("/stats/users/:userId", async (req, res, next) => {
  try {
    if (!isUuid(req.params.userId)) return res.status(404).json({ message: "Không tìm thấy người dùng." });
    const detail = await loadUserDetail(req.params.userId, schoolTimeZone());
    if (!detail) return res.status(404).json({ message: "Không tìm thấy người dùng." });
    return res.json(detail);
  } catch (error) {
    return next(error);
  }
});

export default router;
