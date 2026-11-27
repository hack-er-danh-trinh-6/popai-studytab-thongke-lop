import { randomUUID } from "node:crypto";
import { Router } from "express";
import rateLimit from "express-rate-limit";
import multer from "multer";
import { pool, query } from "../db.js";
import {
  appHubUrl,
  buildUpdateMessage,
  buildUpdateMessageWithLink,
  validateVersion,
  validateVersionCode,
  buildAppVersionRow,
  VERSION_PATTERN
} from "../updateappDomain.js";
import { issueAdminToken, safeEqualText, verifyAdminToken } from "../timetableDomain.js";

// API của trang /updateapp: admin tải APK lên, điền số phiên bản, gửi thông báo "có bản cập nhật mới" tới mọi học sinh.
// Dùng chung mật khẩu SYSTEM_ADMIN_PASSWORD với /sysadmin (token hợp lệ ở cả hai trang).
const router = Router();

const UPLOAD_DIR = process.env.APP_UPDATE_UPLOAD_DIR ?? "media/app-updates";
const MAX_APK_SIZE = 200 * 1024 * 1024; // 200 MB

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => {
    const safeName = (file.originalname ?? "app.apk").replace(/[^A-Za-z0-9.\-_]/g, "_");
    cb(null, `${randomUUID()}_${safeName}`);
  }
});
const upload = multer({
  storage,
  limits: { fileSize: MAX_APK_SIZE },
  fileFilter: (_req, file, cb) => {
    const name = (file.originalname ?? "").toLowerCase();
    if (name.endsWith(".apk") || name.endsWith(".aab") || !file.mimetype || file.mimetype.includes("android")) {
      cb(null, true);
    } else {
      cb(new Error("Chỉ chấp nhận file APK hoặc AAB."));
    }
  }
});

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

// === Công khai: app Android gọi để kiểm tra phiên bản mới khi mở (không cần đăng nhập) ===
router.get("/latest", async (_req, res, next) => {
  try {
    const result = await query(
      `SELECT version, version_code AS versionCode, apk_url AS apkUrl, apk_file_name AS apkFileName,
              apk_size AS apkSize, created_at
         FROM pop_app_versions
        WHERE is_active = TRUE
        ORDER BY created_at DESC
        LIMIT 1`
    );
    if (result.rowCount === 0) {
      return res.json({ latest: null });
    }
    const row = result.rows[0];
    return res.json({
      latest: {
        version: row.version,
        versionCode: row.versioncode,
        apkUrl: row.apkurl,
        apkFileName: row.apkfilename,
        apkSize: row.apksize,
        createdAt: row.created_at.toISOString()
      }
    });
  } catch (error) {
    return next(error);
  }
});

// === Đăng nhập không yêu cầu token cũ; các route quản trị bên dưới mới yêu cầu token ===
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

// === Quản trị: tất cả route sau yêu cầu đăng nhập admin hệ thống ===
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
    const [recipients, history, versions] = await Promise.all([
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
      ),
      query(
        `SELECT version, version_code AS versionCode, apk_file_name AS apkFileName,
                apk_size AS apkSize, is_active, created_at
           FROM pop_app_versions
           ORDER BY created_at DESC
           LIMIT 20`
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
      })),
      versions: versions.rows.map((row) => ({
        version: row.version,
        versionCode: row.versioncode,
        apkFileName: row.apkfilename,
        apkSize: row.apksize,
        isActive: row.isactive,
        createdAt: row.created_at.toISOString()
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

/** Admin tải file APK lên và đăng ký phiên bản mới. */
router.post("/upload", upload.single("apk"), async (req, res, next) => {
  const versionResult = validateVersion(req.body?.version);
  if (!versionResult.ok) return res.status(400).json({ message: versionResult.message });
  const codeResult = validateVersionCode(req.body?.versionCode);
  if (!codeResult.ok) return res.status(400).json({ message: codeResult.message });

  const file = req.file;
  if (!file) {
    return res.status(400).json({ message: "Vui lòng chọn file APK để tải lên." });
  }

  const baseUrl = appHubUrl();
  const apkUrl = `${baseUrl.replace(/\/$/, "")}/media/app-updates/${encodeURIComponent(file.filename)}`;
  const uploadedBy = req.body?.uploadedBy ?? null;

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    // Đánh cũ hết phiên bản active trước đó (chỉ giữ một bản active mới nhất).
    await client.query("UPDATE pop_app_versions SET is_active = FALSE WHERE is_active = TRUE");
    const id = randomUUID();
    const result = await client.query(
      `INSERT INTO pop_app_versions
          (id, version, version_code, apk_file_name, apk_url, apk_size, is_active, uploaded_by)
       VALUES ($1, $2, $3, $4, $5, $6, TRUE, $7)
       RETURNING id, version, version_code, apk_file_name, apk_url, apk_size, created_at`,
      [id, versionResult.version, codeResult.versionCode, file.filename, apkUrl, file.size, uploadedBy]
    );
    await client.query("COMMIT");
    const row = result.rows[0];
    return res.status(201).json({
      id: row.id,
      version: row.version,
      versionCode: row.version_code,
      apkFileName: row.apk_file_name,
      apkUrl: row.apk_url,
      apkSize: row.apk_size,
      createdAt: row.created_at.toISOString()
    });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    // Xoá file đã lưu nếu lỗi DB.
    try { await import("node:fs/promises").then((fs) => fs.unlink(file.path)); } catch { /* bỏ qua */ }
    return next(error);
  } finally {
    client.release();
  }
});

/** Admin liệt kê các phiên bản đã đăng ký. */
router.get("/versions", async (_req, res, next) => {
  try {
    const result = await query(
      `SELECT id, version, version_code AS versionCode, apk_file_name AS apkFileName,
              apk_size AS apkSize, is_active, created_at
         FROM pop_app_versions
         ORDER BY created_at DESC`
    );
    return res.json({
      versions: result.rows.map((row) => ({
        id: row.id,
        version: row.version,
        versionCode: row.versioncode,
        apkFileName: row.apkfilename,
        apkSize: row.apksize,
        isActive: row.isactive,
        createdAt: row.created_at.toISOString()
      }))
    });
  } catch (error) {
    return next(error);
  }
});

export default router;
