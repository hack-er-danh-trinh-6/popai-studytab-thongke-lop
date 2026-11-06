import { randomUUID } from "node:crypto";
import { Router } from "express";
import rateLimit from "express-rate-limit";
import { pool, query } from "../db.js";
import { appHubUrl, buildUpdateMessage, VERSION_PATTERN } from "../updateappDomain.js";
import { issueAdminToken, safeEqualText, verifyAdminToken } from "../timetableDomain.js";

// API của trang /updateapp: admin bấm nút để gửi thông báo "có bản cập nhật mới" tới mọi học sinh.
// Dùng chung mật khẩu SYSTEM_ADMIN_PASSWORD với /sysadmin (token hợp lệ ở cả hai trang).
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
  limit: 5,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { message: "Bạn đã gửi quá nhiều thông báo cập nhật trong một giờ. Vui lòng thử lại sau." }
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

router.get("/status", async (_req, res, next) => {
  try {
    const [recipients, history] = await Promise.all([
      query("SELECT COUNT(*)::int AS total FROM pop_users WHERE role = 'STUDENT'"),
      query(
        `SELECT n.id, n.title, n.body, n.created_at,
                COUNT(r.user_id)::int AS recipient_count,
                COUNT(r.delivered_at)::int AS delivered_count,
                COUNT(r.read_at)::int AS read_count
           FROM pop_notifications n
           LEFT JOIN pop_notification_recipients r ON r.notification_id = n.id
          WHERE n.class_id IS NULL AND n.link_url IS NOT NULL
          GROUP BY n.id
          ORDER BY n.created_at DESC
          LIMIT 10`
      )
    ]);
    return res.json({
      linkUrl: appHubUrl(),
      userCount: recipients.rows[0].total,
      history: history.rows.map((row) => ({
        id: row.id,
        title: row.title,
        body: row.body,
        createdAt: row.created_at.toISOString(),
        recipientCount: row.recipient_count,
        deliveredCount: row.delivered_count,
        readCount: row.read_count
      }))
    });
  } catch (error) {
    return next(error);
  }
});

router.post("/send", sendLimiter, async (req, res, next) => {
  const rawVersion = req.body?.version;
  if (rawVersion !== undefined && rawVersion !== null && typeof rawVersion !== "string") {
    return res.status(400).json({ message: "Phiên bản không hợp lệ." });
  }
  const version = (rawVersion ?? "").trim();
  if (version && !VERSION_PATTERN.test(version)) {
    return res.status(400).json({ message: "Phiên bản chỉ gồm chữ, số, dấu chấm, gạch và tối đa 30 ký tự (ví dụ 1.4.0)." });
  }
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const message = buildUpdateMessage(version);
    const notificationId = randomUUID();
    await client.query(
      `INSERT INTO pop_notifications (id, class_id, sender_id, kind, title, body, link_url)
       VALUES ($1, NULL, NULL, 'SYSTEM', $2, $3, $4)`,
      [notificationId, message.title, message.body, appHubUrl()]
    );
    const recipients = await client.query(
      `INSERT INTO pop_notification_recipients (notification_id, user_id)
       SELECT $1, id FROM pop_users WHERE role = 'STUDENT'`,
      [notificationId]
    );
    if (recipients.rowCount === 0) {
      await client.query("ROLLBACK");
      return res.status(409).json({ message: "Chưa có người dùng nào để nhận thông báo." });
    }
    await client.query("COMMIT");
    return res.status(201).json({ recipientCount: recipients.rowCount, linkUrl: appHubUrl() });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    return next(error);
  } finally {
    client.release();
  }
});

export default router;
