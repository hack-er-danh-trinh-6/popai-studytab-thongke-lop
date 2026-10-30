import { randomUUID } from "node:crypto";
import { Router } from "express";
import rateLimit from "express-rate-limit";
import { pool, query } from "../db.js";
import { issueGateToken, safeEqualText, verifyGateToken } from "../classroomDomain.js";
import { buildStudentData, parseSeedOptions, summarizeGenerated } from "../adminbotDomain.js";

/**
 * API của trang /adminbot: xem danh sách tài khoản bot và bấm nút sinh dữ liệu giả
 * để app Android (và trang giáo viên) có dữ liệu học tập hiển thị.
 *
 * An toàn dữ liệu: mọi thao tác ghi đều giới hạn trong các tài khoản có `is_synthetic = true`
 * thuộc lớp bot. Tài khoản thật không bao giờ bị chạm tới.
 */
const router = Router();

const NOT_CONFIGURED =
  "Máy chủ chưa cấu hình mật khẩu adminbot (ADMINBOT_PASSWORD hoặc TEACHER_ADMIN_PASSWORD).";
const adminPassword = () => process.env.ADMINBOT_PASSWORD || process.env.TEACHER_ADMIN_PASSWORD;
const gateOptions = () => ({ secret: process.env.ADMINBOT_SECRET || process.env.TEACHER_GATE_SECRET });

const gateLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { message: "Nhập sai mật khẩu quá nhiều lần. Vui lòng chờ rồi thử lại." }
});
const seedLimiter = rateLimit({
  windowMs: 5 * 60 * 1000,
  limit: 20,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { message: "Bạn thao tác quá nhanh. Vui lòng chờ vài phút rồi thử lại." }
});

router.post("/gate", gateLimiter, (req, res) => {
  const password = adminPassword();
  if (!password) return res.status(503).json({ message: NOT_CONFIGURED });
  const supplied = req.body?.password;
  if (typeof supplied !== "string" || supplied.length > 200 || !safeEqualText(supplied, password)) {
    return res.status(401).json({ message: "Mật khẩu admin không đúng." });
  }
  const { token, expiresAt } = issueGateToken(password, gateOptions());
  return res.json({ gateToken: token, expiresAt: new Date(expiresAt).toISOString() });
});

function requireGate(req, res, next) {
  const password = adminPassword();
  if (!password) return res.status(503).json({ message: NOT_CONFIGURED });
  if (!verifyGateToken(req.get("x-gate-token"), password, gateOptions())) {
    return res.status(403).json({ message: "Phiên nhập mật khẩu admin đã hết hạn. Vui lòng nhập lại." });
  }
  return next();
}

router.use(requireGate);

/** Lớp bot + giáo viên bot đang gắn trong pop_adminbot_state (fallback: giáo viên synthetic đầu tiên). */
async function loadBotContext() {
  const state = await query(
    `SELECT id, teacher_id, class_id, is_running, seeded_at, started_at, stopped_at,
            last_generated_at, generated_sessions
       FROM pop_adminbot_state WHERE id = 1`
  );
  let teacherId = state.rows[0]?.teacher_id ?? null;
  let classId = state.rows[0]?.class_id ?? null;
  if (!teacherId || !classId) {
    const fallback = await query(
      `SELECT c.teacher_id, c.id AS class_id
         FROM pop_classes c JOIN pop_users t ON t.id = c.teacher_id
        WHERE t.is_synthetic = true ORDER BY c.created_at LIMIT 1`
    );
    if (fallback.rowCount !== 1) return null;
    teacherId = fallback.rows[0].teacher_id;
    classId = fallback.rows[0].class_id;
  }
  const context = await query(
    `SELECT c.id AS class_id, c.name AS class_name, s.name AS school_name,
            t.id AS teacher_id, t.full_name AS teacher_name, t.email AS teacher_email
       FROM pop_classes c
       JOIN pop_schools s ON s.id = c.school_id
       JOIN pop_users t ON t.id = c.teacher_id
      WHERE c.id = $1 AND t.id = $2`,
    [classId, teacherId]
  );
  if (context.rowCount !== 1) return null;
  return { ...context.rows[0], state: state.rows[0] ?? null };
}

const botStudents = (classId) =>
  query(
    `SELECT u.id, u.full_name, u.email
       FROM pop_class_members m
       JOIN pop_users u ON u.id = m.user_id
      WHERE m.class_id = $1 AND m.status = 'APPROVED' AND u.is_synthetic = true
      ORDER BY u.email`,
    [classId]
  );

router.get("/overview", async (req, res, next) => {
  try {
    const context = await loadBotContext();
    if (!context) return res.status(404).json({ message: "Chưa có lớp bot nào trong hệ thống." });
    const students = await query(
      `SELECT u.id, u.full_name, u.email,
              (SELECT COUNT(*)::int FROM pop_focus_sessions f WHERE f.user_id = u.id) AS sessions,
              (SELECT COUNT(*)::int FROM pop_focus_sessions f WHERE f.user_id = u.id AND f.status = 'COMPLETED') AS completed_sessions,
              (SELECT COALESCE(SUM(f.actual_minutes), 0)::int FROM pop_focus_sessions f
                WHERE f.user_id = u.id AND f.status = 'COMPLETED') AS focus_minutes,
              (SELECT COUNT(*)::int FROM pop_journals j WHERE j.user_id = u.id) AS journals,
              (SELECT COUNT(*)::int FROM pop_assessment_results a WHERE a.user_id = u.id) AS assessments,
              EXISTS(SELECT 1 FROM pop_personal_plans p WHERE p.user_id = u.id) AS has_plan,
              (SELECT MAX(f.created_at) FROM pop_focus_sessions f WHERE f.user_id = u.id) AS last_activity
         FROM pop_class_members m
         JOIN pop_users u ON u.id = m.user_id
        WHERE m.class_id = $1 AND m.status = 'APPROVED' AND u.is_synthetic = true
        ORDER BY u.full_name`,
      [context.class_id]
    );
    const totals = students.rows.reduce(
      (sum, row) => ({
        sessions: sum.sessions + row.sessions,
        journals: sum.journals + row.journals,
        assessments: sum.assessments + row.assessments,
        focusMinutes: sum.focusMinutes + row.focus_minutes,
        plans: sum.plans + (row.has_plan ? 1 : 0)
      }),
      { sessions: 0, journals: 0, assessments: 0, focusMinutes: 0, plans: 0 }
    );
    return res.json({
      teacher: {
        id: context.teacher_id,
        fullName: context.teacher_name,
        email: context.teacher_email
      },
      klass: {
        id: context.class_id,
        name: context.class_name,
        schoolName: context.school_name
      },
      state: {
        isRunning: Boolean(context.state?.is_running),
        seededAt: context.state?.seeded_at ? context.state.seeded_at.toISOString() : null,
        startedAt: context.state?.started_at ? context.state.started_at.toISOString() : null,
        stoppedAt: context.state?.stopped_at ? context.state.stopped_at.toISOString() : null,
        lastGeneratedAt: context.state?.last_generated_at
          ? context.state.last_generated_at.toISOString()
          : null,
        generatedSessions: context.state?.generated_sessions ?? 0
      },
      totals: { ...totals, students: students.rows.length },
      students: students.rows.map((row) => ({
        userId: row.id,
        fullName: row.full_name,
        email: row.email,
        sessions: row.sessions,
        completedSessions: row.completed_sessions,
        focusMinutes: row.focus_minutes,
        journals: row.journals,
        assessments: row.assessments,
        hasPlan: row.has_plan,
        lastActivity: row.last_activity ? row.last_activity.toISOString() : null
      }))
    });
  } catch (error) {
    return next(error);
  }
});

/** Xoá toàn bộ dữ liệu học tập của các tài khoản bot trong lớp bot. */
async function clearBotData(client, userIds) {
  if (!userIds.length) return;
  await client.query("DELETE FROM pop_focus_sessions WHERE user_id = ANY($1::uuid[])", [userIds]);
  await client.query("DELETE FROM pop_journals WHERE user_id = ANY($1::uuid[])", [userIds]);
  await client.query("DELETE FROM pop_assessment_results WHERE user_id = ANY($1::uuid[])", [userIds]);
  await client.query("DELETE FROM pop_personal_plans WHERE user_id = ANY($1::uuid[])", [userIds]);
}

router.post("/seed", seedLimiter, async (req, res, next) => {
  const options = parseSeedOptions(req.body);
  const client = await pool.connect();
  try {
    const context = await loadBotContext();
    if (!context) return res.status(404).json({ message: "Chưa có lớp bot nào trong hệ thống." });
    const members = await botStudents(context.class_id);
    if (!members.rowCount) {
      return res.status(404).json({ message: "Lớp bot chưa có tài khoản học sinh bot nào." });
    }
    const now = new Date();
    await client.query("BEGIN");
    if (options.resetFirst) {
      await clearBotData(client, members.rows.map((row) => row.id));
    }
    const summary = [];
    let generatedSessions = 0;
    for (const student of members.rows) {
      const data = buildStudentData({ email: student.email, options, now });
      for (const session of data.sessions) {
        await client.query(
          `INSERT INTO pop_focus_sessions
             (id, user_id, client_session_id, task_name, target_minutes, actual_minutes,
              interruption_count, interruption_reasons, self_rating, status, created_at)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9, $10, $11)
           ON CONFLICT (user_id, client_session_id) DO UPDATE SET
             task_name = EXCLUDED.task_name,
             target_minutes = EXCLUDED.target_minutes,
             actual_minutes = EXCLUDED.actual_minutes,
             interruption_count = EXCLUDED.interruption_count,
             interruption_reasons = EXCLUDED.interruption_reasons,
             self_rating = EXCLUDED.self_rating,
             status = EXCLUDED.status,
             created_at = EXCLUDED.created_at`,
          [
            randomUUID(), student.id, session.clientSessionId, session.taskName, session.targetMinutes,
            session.actualMinutes, session.interruptionCount, JSON.stringify(session.interruptionReasons),
            session.selfRating, session.status, session.createdAt
          ]
        );
      }
      for (const journal of data.journals) {
        await client.query(
          `INSERT INTO pop_journals
             (id, user_id, entry_date, estimated_short_video_minutes, focus_minutes, interruption_count,
              distractors, most_productive_session, tomorrow_adjustment, updated_at)
           VALUES ($1, $2, $3::date, $4, $5, $6, $7::jsonb, $8, $9, NOW())
           ON CONFLICT (user_id, entry_date) DO UPDATE SET
             estimated_short_video_minutes = EXCLUDED.estimated_short_video_minutes,
             focus_minutes = EXCLUDED.focus_minutes,
             interruption_count = EXCLUDED.interruption_count,
             distractors = EXCLUDED.distractors,
             most_productive_session = EXCLUDED.most_productive_session,
             tomorrow_adjustment = EXCLUDED.tomorrow_adjustment,
             updated_at = NOW()`,
          [
            randomUUID(), student.id, journal.entryDate, journal.estimatedShortVideoMinutes,
            journal.focusMinutes, journal.interruptionCount, JSON.stringify(journal.distractors),
            journal.mostProductiveSession, journal.tomorrowAdjustment
          ]
        );
      }
      for (const assessment of data.assessments) {
        await client.query(
          `INSERT INTO pop_assessment_results (id, user_id, answers, scores, note, completed_at)
           VALUES ($1, $2, $3::jsonb, $4::jsonb, $5, $6)`,
          [
            randomUUID(), student.id, JSON.stringify(assessment.answers),
            JSON.stringify(assessment.scores), "Chỉ để tự theo dõi, không phải chẩn đoán",
            assessment.completedAt
          ]
        );
      }
      if (data.plan) {
        const { updatedAt, ...plan } = data.plan;
        await client.query(
          `INSERT INTO pop_personal_plans (user_id, plan, updated_at)
           VALUES ($1, $2::jsonb, $3)
           ON CONFLICT (user_id) DO UPDATE SET plan = EXCLUDED.plan, updated_at = EXCLUDED.updated_at`,
          [student.id, JSON.stringify(plan), updatedAt]
        );
      }
      generatedSessions += data.sessions.length;
      summary.push({
        userId: student.id,
        fullName: student.full_name,
        email: student.email,
        sessions: data.sessions.length,
        journals: data.journals.length,
        assessments: data.assessments.length,
        plans: data.plan ? 1 : 0
      });
    }
    // Trang /admin và API thống kê chỉ tính phiên tập trung kể từ `decided_at` của học sinh,
    // nên lùi ngày tham gia của lớp bot về trước khoảng dữ liệu vừa sinh.
    if (options.alignJoinDate) {
      const joinedAt = new Date(now.getTime() - (options.days + 1) * 86400000);
      await client.query(
        `UPDATE pop_class_members m
            SET requested_at = LEAST(m.requested_at, $3::timestamptz),
                decided_at = LEAST(COALESCE(m.decided_at, $3::timestamptz), $3::timestamptz)
          FROM pop_users u
         WHERE m.user_id = u.id AND m.class_id = $1 AND u.is_synthetic = true
           AND m.user_id = ANY($2::uuid[])`,
        [context.class_id, members.rows.map((row) => row.id), joinedAt]
      );
    }
    await client.query(
      `UPDATE pop_adminbot_state
          SET last_generated_at = NOW(),
              seeded_at = COALESCE(seeded_at, NOW()),
              generated_sessions = generated_sessions + $1,
              updated_at = NOW()
        WHERE id = 1`,
      [generatedSessions]
    );
    await client.query("COMMIT");
    return res.json({ options, summary: summarizeGenerated(summary), students: summary });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    return next(error);
  } finally {
    client.release();
  }
});

router.post("/reset", seedLimiter, async (req, res, next) => {
  const client = await pool.connect();
  try {
    const context = await loadBotContext();
    if (!context) return res.status(404).json({ message: "Chưa có lớp bot nào trong hệ thống." });
    const members = await botStudents(context.class_id);
    await client.query("BEGIN");
    await clearBotData(client, members.rows.map((row) => row.id));
    await client.query(
      `UPDATE pop_adminbot_state
          SET generated_sessions = 0, last_generated_at = NULL, updated_at = NOW()
        WHERE id = 1`
    );
    await client.query("COMMIT");
    return res.json({ clearedStudents: members.rowCount });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    return next(error);
  } finally {
    client.release();
  }
});

router.post("/state", seedLimiter, async (req, res, next) => {
  try {
    const context = await loadBotContext();
    if (!context) return res.status(404).json({ message: "Chưa có lớp bot nào trong hệ thống." });
    const running = req.body?.running === true;
    await query(
      `UPDATE pop_adminbot_state
          SET is_running = $1,
              started_at = CASE WHEN $1 THEN NOW() ELSE started_at END,
              stopped_at = CASE WHEN $1 THEN stopped_at ELSE NOW() END,
              updated_at = NOW()
        WHERE id = 1`,
      [running]
    );
    return res.json({ isRunning: running });
  } catch (error) {
    return next(error);
  }
});

export default router;
