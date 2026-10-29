import { randomBytes, randomUUID } from "node:crypto";
import bcrypt from "bcryptjs";
import { pool, query } from "./db.js";
import { nameKey } from "./classroomDomain.js";
import {
  ADMINBOT_CLASS, ADMINBOT_INTERVAL_MS, ADMINBOT_ROSTER, ADMINBOT_SCHOOL,
  ADMINBOT_TEACHER, ADMINBOT_TEACHER_EMAIL, adminbotStudentEmail
} from "./adminbotDomain.js";

const newPassword = () => randomBytes(18).toString("base64url");
const iso = (value) => value ? new Date(value).toISOString() : null;
const fail = (status, message) => Object.assign(new Error(message), { status });
const studentEmails = () => ADMINBOT_ROSTER.map((_name, index) => adminbotStudentEmail(index));

export async function provisionAdminbotCohort() {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query("SELECT pg_advisory_xact_lock(hashtext($1))", ["pop_ai_adminbot_provision"]);

    const existingState = await client.query("SELECT class_id FROM pop_adminbot_state WHERE id = 1");
    if (existingState.rowCount) {
      await client.query("ROLLBACK");
      return { created: false, message: "Cohort Adminbot đã được tạo trước đó. Không tạo tài khoản trùng." };
    }

    const credentials = [
      { role: "TEACHER", fullName: ADMINBOT_TEACHER, email: ADMINBOT_TEACHER_EMAIL, password: newPassword() },
      ...ADMINBOT_ROSTER.map((fullName, index) => ({
        role: "STUDENT", fullName, email: adminbotStudentEmail(index), password: newPassword()
      }))
    ];
    const emails = credentials.map((entry) => entry.email);
    const conflicts = await client.query("SELECT email FROM pop_users WHERE email = ANY($1::text[])", [emails]);
    if (conflicts.rowCount) {
      throw fail(409, "Một hoặc nhiều email alias Adminbot đã tồn tại. Không có tài khoản nào được thay đổi.");
    }

    const schoolKey = nameKey(ADMINBOT_SCHOOL);
    let school = await client.query("SELECT id FROM pop_schools WHERE name_key = $1", [schoolKey]);
    const teacherId = randomUUID();
    const teacherHash = await bcrypt.hash(credentials[0].password, 12);
    await client.query(
      `INSERT INTO pop_users (id, email, full_name, password_hash, role, is_synthetic)
       VALUES ($1, $2, $3, $4, 'TEACHER', TRUE)`,
      [teacherId, credentials[0].email, credentials[0].fullName, teacherHash]
    );

    if (!school.rowCount) {
      const schoolId = randomUUID();
      await client.query(
        `INSERT INTO pop_schools (id, name, name_key, created_by) VALUES ($1, $2, $3, $4)`,
        [schoolId, ADMINBOT_SCHOOL, schoolKey, teacherId]
      );
      school = { rows: [{ id: schoolId }], rowCount: 1 };
    }

    const classKey = nameKey(ADMINBOT_CLASS);
    const classConflict = await client.query(
      "SELECT id FROM pop_classes WHERE school_id = $1 AND name_key = $2",
      [school.rows[0].id, classKey]
    );
    if (classConflict.rowCount) {
      throw fail(409, "Lớp 11TN2 tại THPT Tây Đô đã tồn tại. Không thêm dữ liệu mô phỏng vào lớp có sẵn.");
    }

    const classId = randomUUID();
    await client.query(
      `INSERT INTO pop_classes (id, school_id, name, name_key, teacher_id)
       VALUES ($1, $2, $3, $4, $5)`,
      [classId, school.rows[0].id, ADMINBOT_CLASS, classKey, teacherId]
    );

    const students = credentials.slice(1);
    const hashes = await Promise.all(students.map((entry) => bcrypt.hash(entry.password, 12)));
    const studentIds = students.map(() => randomUUID());
    await client.query(
      `INSERT INTO pop_users (id, email, full_name, password_hash, role, created_at, is_synthetic)
       SELECT x.id::uuid, x.email, x.full_name, x.password_hash, 'STUDENT', NOW() - INTERVAL '7 days', TRUE
         FROM unnest($1::uuid[], $2::text[], $3::text[], $4::text[])
              AS x(id, email, full_name, password_hash)`,
      [studentIds, students.map((entry) => entry.email), students.map((entry) => entry.fullName), hashes]
    );
    await client.query(
      `INSERT INTO pop_class_members (class_id, user_id, status, requested_at, decided_at, decided_by)
       SELECT $1, student_id, 'APPROVED', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days', $2
         FROM unnest($3::uuid[]) AS t(student_id)`,
      [classId, teacherId, studentIds]
    );
    await client.query(
      `INSERT INTO pop_adminbot_state (id, teacher_id, class_id, is_running, generated_sessions)
       VALUES (1, $1, $2, FALSE, 0)`,
      [teacherId, classId]
    );
    await client.query("COMMIT");
    return {
      created: true,
      school: ADMINBOT_SCHOOL,
      className: ADMINBOT_CLASS,
      teacher: ADMINBOT_TEACHER,
      credentials
    };
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

export async function rotateAdminbotCredentials() {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const stateResult = await client.query("SELECT * FROM pop_adminbot_state WHERE id = 1 FOR UPDATE");
    if (!stateResult.rowCount) throw fail(409, "Chưa có cohort Adminbot để đặt lại mật khẩu.");
    const state = stateResult.rows[0];
    const accounts = await client.query(
      `SELECT u.id, u.email, u.full_name, u.role
         FROM pop_users u
         LEFT JOIN pop_class_members m
           ON m.user_id = u.id AND m.class_id = $1 AND m.status = 'APPROVED'
        WHERE (u.id = $2 AND u.role = 'TEACHER' AND u.is_synthetic = TRUE)
           OR (m.user_id IS NOT NULL AND u.role = 'STUDENT' AND u.is_synthetic = TRUE
               AND u.email = ANY($3::text[]))
        ORDER BY CASE WHEN u.id = $2 THEN 0 ELSE 1 END, u.email`,
      [state.class_id, state.teacher_id, studentEmails()]
    );
    if (accounts.rowCount !== ADMINBOT_ROSTER.length + 1) {
      throw fail(409, "Cohort không còn đủ đúng 39 tài khoản mô phỏng; không đổi mật khẩu nào.");
    }

    const credentials = accounts.rows.map((account) => ({
      role: account.role,
      fullName: account.full_name,
      email: account.email,
      password: newPassword()
    }));
    const hashes = await Promise.all(credentials.map((entry) => bcrypt.hash(entry.password, 12)));
    const ids = accounts.rows.map((account) => account.id);
    await client.query(
      `UPDATE pop_users u SET password_hash = x.password_hash
         FROM unnest($1::uuid[], $2::text[]) AS x(id, password_hash)
        WHERE u.id = x.id`,
      [ids, hashes]
    );
    await client.query("DELETE FROM pop_sessions WHERE user_id = ANY($1::uuid[])", [ids]);
    await client.query("COMMIT");
    return { credentials };
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

export async function loadAdminbotState() {
  const stateResult = await query(
    `SELECT b.is_running, b.seeded_at, b.started_at, b.stopped_at, b.last_generated_at,
            b.generated_sessions, c.id AS class_id, c.name AS class_name,
            s.name AS school_name, t.id AS teacher_id, t.full_name AS teacher_name, t.email AS teacher_email
       FROM pop_adminbot_state b
       JOIN pop_classes c ON c.id = b.class_id
       JOIN pop_schools s ON s.id = c.school_id
       JOIN pop_users t ON t.id = b.teacher_id
      WHERE b.id = 1`
  );
  if (!stateResult.rowCount) {
    return {
      provisioned: false,
      running: false,
      school: ADMINBOT_SCHOOL,
      className: ADMINBOT_CLASS,
      teacher: { fullName: ADMINBOT_TEACHER, email: ADMINBOT_TEACHER_EMAIL, role: "TEACHER", created: false },
      users: ADMINBOT_ROSTER.map((fullName, index) => ({
        fullName, email: adminbotStudentEmail(index), role: "STUDENT", created: false,
        focusMinutes: 0, completedSessions: 0
      })),
      syntheticSessions: 0,
      syntheticFocusMinutes: 0
    };
  }

  const state = stateResult.rows[0];
  const [studentsResult, totalsResult] = await Promise.all([
    query(
      `SELECT u.full_name, u.email, u.role,
              COALESCE(SUM(f.actual_minutes) FILTER (WHERE f.status = 'COMPLETED'), 0)::int AS focus_minutes,
              COUNT(f.id) FILTER (WHERE f.status = 'COMPLETED')::int AS completed_sessions
         FROM pop_class_members m
         JOIN pop_users u ON u.id = m.user_id AND u.role = 'STUDENT'
          AND u.is_synthetic = TRUE AND u.email = ANY($2::text[])
         LEFT JOIN pop_focus_sessions f ON f.user_id = u.id AND f.is_synthetic = TRUE
        WHERE m.class_id = $1 AND m.status = 'APPROVED'
        GROUP BY u.id
        ORDER BY u.full_name`,
      [state.class_id, studentEmails()]
    ),
    query(
      `SELECT COUNT(*)::int AS sessions,
              COALESCE(SUM(f.actual_minutes) FILTER (WHERE f.status = 'COMPLETED'), 0)::int AS minutes
         FROM pop_focus_sessions f
         JOIN pop_class_members m ON m.user_id = f.user_id AND m.class_id = $1 AND m.status = 'APPROVED'
         JOIN pop_users u ON u.id = f.user_id AND u.role = 'STUDENT'
          AND u.is_synthetic = TRUE AND u.email = ANY($2::text[])
        WHERE f.is_synthetic = TRUE`,
      [state.class_id, studentEmails()]
    )
  ]);

  return {
    provisioned: true,
    running: state.is_running,
    school: state.school_name,
    className: state.class_name,
    teacher: { fullName: state.teacher_name, email: state.teacher_email, role: "TEACHER", created: true },
    users: studentsResult.rows.map((row) => ({
      fullName: row.full_name,
      email: row.email,
      role: row.role,
      created: true,
      focusMinutes: row.focus_minutes,
      completedSessions: row.completed_sessions
    })),
    seededAt: iso(state.seeded_at),
    startedAt: iso(state.started_at),
    stoppedAt: iso(state.stopped_at),
    lastGeneratedAt: iso(state.last_generated_at),
    generatedSessions: state.generated_sessions,
    syntheticSessions: totalsResult.rows[0].sessions,
    syntheticFocusMinutes: totalsResult.rows[0].minutes
  };
}

export async function startAdminbot() {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const found = await client.query("SELECT * FROM pop_adminbot_state WHERE id = 1 FOR UPDATE");
    if (!found.rowCount) throw fail(409, "Hãy tạo cohort Adminbot trước khi bấm Start.");
    const rosterCheck = await client.query(
      `SELECT COUNT(*)::int AS count
         FROM pop_class_members m JOIN pop_users u ON u.id = m.user_id
        WHERE m.class_id = $1 AND m.status = 'APPROVED' AND u.role = 'STUDENT'
          AND u.is_synthetic = TRUE AND u.email = ANY($2::text[])`,
      [found.rows[0].class_id, studentEmails()]
    );
    if (rosterCheck.rows[0].count !== ADMINBOT_ROSTER.length) {
      throw fail(409, "Lớp không còn đủ đúng 38 tài khoản học sinh synthetic đã duyệt; không bật bot.");
    }

    let seeded = 0;
    if (!found.rows[0].seeded_at) {
      const inserted = await client.query(
        `INSERT INTO pop_focus_sessions
          (id, user_id, client_session_id, task_name, target_minutes, actual_minutes,
           interruption_count, interruption_reasons, self_rating, status, created_at, is_synthetic)
         SELECT gen_random_uuid(), m.user_id,
                'adminbot:seed:' || d.day_offset::text || ':' || gen_random_uuid(),
                (ARRAY['Mô phỏng · Toán', 'Mô phỏng · Ngữ văn', 'Mô phỏng · Tiếng Anh', 'Mô phỏng · Vật lí', 'Mô phỏng · Hóa học'])
                  [1 + floor(random() * 5)::int],
                60, 15 + floor(random() * 46)::int, floor(random() * 4)::int,
                CASE WHEN random() < 0.3 THEN '["Mất tập trung tạm thời"]'::jsonb ELSE '[]'::jsonb END,
                3 + floor(random() * 3)::int, 'COMPLETED',
                CASE WHEN d.day_offset = 0 THEN NOW()
                     ELSE (((NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date - d.day_offset)
                           + TIME '09:00' + random() * INTERVAL '8 hours') AT TIME ZONE 'Asia/Ho_Chi_Minh'
                END, TRUE
           FROM pop_class_members m
           JOIN pop_users u ON u.id = m.user_id AND u.role = 'STUDENT'
            AND u.is_synthetic = TRUE AND u.email = ANY($2::text[])
           CROSS JOIN generate_series(0, 6) AS d(day_offset)
          WHERE m.class_id = $1 AND m.status = 'APPROVED'`,
        [found.rows[0].class_id, studentEmails()]
      );
      seeded = inserted.rowCount;
    }

    await client.query(
      `UPDATE pop_adminbot_state
          SET is_running = TRUE, started_at = NOW(), stopped_at = NULL,
              last_generated_at = NOW(),
              seeded_at = COALESCE(seeded_at, NOW()),
              generated_sessions = generated_sessions + $1, updated_at = NOW()
        WHERE id = 1`,
      [seeded]
    );
    await client.query("COMMIT");
    return { running: true, seededSessions: seeded };
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

export async function stopAdminbot() {
  const result = await query(
    `UPDATE pop_adminbot_state SET is_running = FALSE, stopped_at = NOW(), updated_at = NOW()
      WHERE id = 1 RETURNING id`
  );
  if (!result.rowCount) throw fail(409, "Chưa có cohort Adminbot để dừng.");
  return { running: false };
}

async function tickAdminbot() {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const found = await client.query(
      "SELECT * FROM pop_adminbot_state WHERE id = 1 AND is_running = TRUE FOR UPDATE SKIP LOCKED"
    );
    if (!found.rowCount) {
      await client.query("COMMIT");
      return;
    }
    const state = found.rows[0];
    if (state.last_generated_at && Date.now() - new Date(state.last_generated_at).getTime() < ADMINBOT_INTERVAL_MS) {
      await client.query("COMMIT");
      return;
    }

    const pick = await client.query(
      `SELECT user_id FROM (
         SELECT m.user_id, COUNT(*) OVER() AS roster_count
           FROM pop_class_members m
           JOIN pop_users u ON u.id = m.user_id AND u.role = 'STUDENT'
            AND u.is_synthetic = TRUE AND u.email = ANY($2::text[])
          WHERE m.class_id = $1 AND m.status = 'APPROVED'
       ) roster
        WHERE roster_count = $3
        ORDER BY random() LIMIT 1`,
      [state.class_id, studentEmails(), ADMINBOT_ROSTER.length]
    );
    if (!pick.rowCount) {
      await client.query("COMMIT");
      return;
    }
    const targetMinutes = [25, 45, 60][Math.floor(Math.random() * 3)];
    const actualMinutes = 10 + Math.floor(Math.random() * (targetMinutes - 9));
    const taskName = ["Mô phỏng · Ôn tập Toán", "Mô phỏng · Đọc hiểu", "Mô phỏng · Bài tập tiếng Anh", "Mô phỏng · Ôn tập Vật lí"][Math.floor(Math.random() * 4)];
    await client.query(
      `INSERT INTO pop_focus_sessions
        (id, user_id, client_session_id, task_name, target_minutes, actual_minutes,
         interruption_count, interruption_reasons, self_rating, status, is_synthetic)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9, 'COMPLETED', TRUE)`,
      [randomUUID(), pick.rows[0].user_id, `adminbot:live:${randomUUID()}`, taskName,
        targetMinutes, actualMinutes, Math.floor(Math.random() * 4), "[]", 2 + Math.floor(Math.random() * 4)]
    );
    await client.query(
      `UPDATE pop_adminbot_state
          SET last_generated_at = NOW(), generated_sessions = generated_sessions + 1, updated_at = NOW()
        WHERE id = 1`
    );
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

export function startAdminbotScheduler() {
  if (scheduler) return () => clearInterval(scheduler);
  scheduler = setInterval(() => {
    tickAdminbot().catch((error) => {
      console.error("adminbot_tick_failed", { code: error?.code ?? "adminbot_tick_error" });
    });
  }, 15_000);
  scheduler.unref?.();
  return () => {
    clearInterval(scheduler);
    scheduler = null;
  };
}

let scheduler = null;
