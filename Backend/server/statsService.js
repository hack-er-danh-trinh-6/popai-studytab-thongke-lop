import { query } from "./db.js";
import { GROUPS, USER_SORTS, escapeLike, fillBuckets, round1 } from "./statsDomain.js";

// Truy vấn tổng hợp cho trang thống kê sysadmin.
// Chỉ trả số liệu và nhãn phân loại (nền tảng, lý do gián đoạn, yếu tố gây xao nhãng); KHÔNG trả nội dung tự do
// như tên nhiệm vụ, nhật ký phản tư hay câu hỏi gửi POP-AI.
// $1 = múi giờ, $2 = số ngày của khoảng thời gian đang xem.

const TODAY = "(NOW() AT TIME ZONE $1)::date";
const START = `(${TODAY} - ($2::int - 1))`;
const SERIES = `SELECT generate_series(${START}, ${TODAY}, interval '1 day')::date AS day`;
const FOCUS_IN_RANGE = `(created_at AT TIME ZONE $1)::date >= ${START}`;

const num = (value) => (value === null || value === undefined ? 0 : Number(value));
const labelCount = (rows) => rows.map((row) => ({ label: row.label, count: num(row.count) }));

export async function loadOverview(days, timeZone) {
  const p = [timeZone, days];
  const [
    roles, signups, activity, activeCounts, assessTotals, assessPerDay, assessAvg, assessDist,
    planTotals, planDuration, planTarget, planLimit, planPlatforms,
    focusStatus, focusTotals, focusPerDay, focusHours, focusRating, focusReasons, focusTarget,
    journalTotals, journalPerDay, journalBuckets, journalDistractors,
    classroom, memberStatus, classes
  ] = await Promise.all([
    query("SELECT role AS label, COUNT(*)::int AS count FROM pop_users GROUP BY role ORDER BY count DESC"),
    query(
      `SELECT d.day::text AS day, COUNT(u.id)::int AS count
         FROM (${SERIES}) d LEFT JOIN pop_users u ON (u.created_at AT TIME ZONE $1)::date = d.day
        GROUP BY d.day ORDER BY d.day`, p),
    query(
      `WITH act AS (
         SELECT user_id, (created_at AT TIME ZONE $1)::date AS day FROM pop_focus_sessions WHERE ${FOCUS_IN_RANGE}
         UNION
         SELECT user_id, (updated_at AT TIME ZONE $1)::date FROM pop_journals WHERE (updated_at AT TIME ZONE $1)::date >= ${START}
         UNION
         SELECT user_id, (completed_at AT TIME ZONE $1)::date FROM pop_assessment_results WHERE (completed_at AT TIME ZONE $1)::date >= ${START}
       )
       SELECT d.day::text AS day, COUNT(DISTINCT a.user_id)::int AS count
         FROM (${SERIES}) d LEFT JOIN act a ON a.day = d.day
        GROUP BY d.day ORDER BY d.day`, p),
    query(
      `WITH act AS (
         SELECT user_id, created_at AS at FROM pop_focus_sessions
         UNION ALL SELECT user_id, updated_at FROM pop_journals
         UNION ALL SELECT user_id, completed_at FROM pop_assessment_results
       )
       SELECT (SELECT COUNT(*) FROM pop_users)::int AS total_users,
              COUNT(DISTINCT user_id) FILTER (WHERE at >= NOW() - interval '1 day')::int AS active_1,
              COUNT(DISTINCT user_id) FILTER (WHERE at >= NOW() - interval '7 days')::int AS active_7,
              COUNT(DISTINCT user_id) FILTER (WHERE at >= NOW() - interval '30 days')::int AS active_30,
              COUNT(DISTINCT user_id)::int AS ever_active,
              (SELECT COUNT(DISTINCT user_id) FROM pop_class_members WHERE status = 'APPROVED')::int AS with_class,
              (SELECT COUNT(DISTINCT user_id) FROM pop_assessment_results)::int AS with_assessment,
              (SELECT COUNT(*) FROM pop_personal_plans)::int AS with_plan,
              (SELECT COUNT(DISTINCT user_id) FROM pop_focus_sessions)::int AS with_focus,
              (SELECT COUNT(DISTINCT user_id) FROM pop_journals)::int AS with_journal
         FROM act`),
    query("SELECT COUNT(*)::int AS total, COUNT(DISTINCT user_id)::int AS users FROM pop_assessment_results"),
    query(
      `SELECT d.day::text AS day, COUNT(r.id)::int AS count
         FROM (${SERIES}) d LEFT JOIN pop_assessment_results r ON (r.completed_at AT TIME ZONE $1)::date = d.day
        GROUP BY d.day ORDER BY d.day`, p),
    query(
      `WITH latest AS (SELECT DISTINCT ON (user_id) scores FROM pop_assessment_results ORDER BY user_id, completed_at DESC)
       SELECT g.code AS label, ROUND(AVG((latest.scores->>g.code)::numeric), 2) AS avg
         FROM latest CROSS JOIN (VALUES ('VN'), ('PT'), ('PB'), ('TT')) g(code)
        WHERE latest.scores ->> g.code IS NOT NULL GROUP BY g.code`),
    query(
      `WITH latest AS (SELECT DISTINCT ON (user_id) scores FROM pop_assessment_results ORDER BY user_id, completed_at DESC)
       SELECT g.code AS grp,
              LEAST(5, GREATEST(1, ROUND((latest.scores->>g.code)::numeric)))::int AS bucket, COUNT(*)::int AS count
         FROM latest CROSS JOIN (VALUES ('VN'), ('PT'), ('PB'), ('TT')) g(code)
        WHERE latest.scores ->> g.code IS NOT NULL GROUP BY g.code, bucket`),
    query(
      `SELECT COUNT(*)::int AS total,
              COALESCE(ROUND(AVG((plan->>'dailyShortVideoLimitMinutes')::numeric)), 0)::int AS avg_limit
         FROM pop_personal_plans`),
    query("SELECT plan->>'sessionDurationMinutes' AS bucket, COUNT(*)::int AS count FROM pop_personal_plans GROUP BY 1"),
    query("SELECT plan->>'targetDays' AS bucket, COUNT(*)::int AS count FROM pop_personal_plans GROUP BY 1"),
    query(
      `SELECT CASE WHEN lim <= 30 THEN '0-30' WHEN lim <= 60 THEN '31-60' WHEN lim <= 120 THEN '61-120' ELSE '>120' END AS bucket,
              COUNT(*)::int AS count
         FROM (SELECT (plan->>'dailyShortVideoLimitMinutes')::int AS lim FROM pop_personal_plans) t GROUP BY 1`),
    query(
      `SELECT MIN(trim(p)) AS label, COUNT(*)::int AS count
         FROM pop_personal_plans,
              jsonb_array_elements_text(CASE WHEN jsonb_typeof(plan->'trackedPlatforms') = 'array' THEN plan->'trackedPlatforms' ELSE '[]'::jsonb END) p
        WHERE trim(p) <> '' GROUP BY lower(trim(p)) ORDER BY count DESC, label LIMIT 8`),
    query(`SELECT status AS label, COUNT(*)::int AS count FROM pop_focus_sessions WHERE ${FOCUS_IN_RANGE} GROUP BY status`, p),
    query(
      `SELECT COUNT(DISTINCT user_id)::int AS users, COUNT(*)::int AS sessions,
              COALESCE(SUM(actual_minutes) FILTER (WHERE status = 'COMPLETED'), 0)::int AS minutes,
              COALESCE(SUM(interruption_count), 0)::int AS interruptions,
              COALESCE(ROUND(AVG(LEAST(actual_minutes::numeric / target_minutes, 1) * 100) FILTER (WHERE status = 'COMPLETED')), 0)::int AS completion_rate,
              ROUND(AVG(self_rating) FILTER (WHERE self_rating > 0), 2) AS avg_rating
         FROM pop_focus_sessions WHERE ${FOCUS_IN_RANGE}`, p),
    query(
      `SELECT d.day::text AS day,
              COUNT(f.id) FILTER (WHERE f.status = 'COMPLETED')::int AS completed,
              COALESCE(SUM(f.actual_minutes) FILTER (WHERE f.status = 'COMPLETED'), 0)::int AS minutes
         FROM (${SERIES}) d LEFT JOIN pop_focus_sessions f ON (f.created_at AT TIME ZONE $1)::date = d.day
        GROUP BY d.day ORDER BY d.day`, p),
    query(
      `SELECT EXTRACT(HOUR FROM created_at AT TIME ZONE $1)::int AS bucket, COUNT(*)::int AS count
         FROM pop_focus_sessions WHERE ${FOCUS_IN_RANGE} GROUP BY 1`, p),
    query(
      `SELECT self_rating AS bucket, COUNT(*)::int AS count FROM pop_focus_sessions
        WHERE self_rating > 0 AND ${FOCUS_IN_RANGE} GROUP BY 1`, p),
    query(
      `SELECT MIN(trim(r)) AS label, COUNT(*)::int AS count
         FROM pop_focus_sessions,
              jsonb_array_elements_text(CASE WHEN jsonb_typeof(interruption_reasons) = 'array' THEN interruption_reasons ELSE '[]'::jsonb END) r
        WHERE ${FOCUS_IN_RANGE} AND trim(r) <> '' GROUP BY lower(trim(r)) ORDER BY count DESC, label LIMIT 8`, p),
    query(
      `SELECT target_minutes AS bucket, COUNT(*)::int AS count FROM pop_focus_sessions
        WHERE ${FOCUS_IN_RANGE} GROUP BY 1`, p),
    query(
      `SELECT COUNT(DISTINCT user_id)::int AS users, COUNT(*)::int AS entries,
              ROUND(AVG(estimated_short_video_minutes))::int AS avg_video,
              ROUND(AVG(focus_minutes))::int AS avg_focus,
              ROUND(AVG(interruption_count), 1) AS avg_interruptions
         FROM pop_journals WHERE entry_date >= ${START}`, p),
    query(
      `SELECT d.day::text AS day, COUNT(j.id)::int AS entries,
              COALESCE(ROUND(AVG(j.estimated_short_video_minutes)), 0)::int AS avg_video,
              COALESCE(ROUND(AVG(j.focus_minutes)), 0)::int AS avg_focus
         FROM (${SERIES}) d LEFT JOIN pop_journals j ON j.entry_date = d.day
        GROUP BY d.day ORDER BY d.day`, p),
    query(
      `SELECT CASE WHEN estimated_short_video_minutes <= 30 THEN '0-30' WHEN estimated_short_video_minutes <= 60 THEN '31-60'
                   WHEN estimated_short_video_minutes <= 120 THEN '61-120' WHEN estimated_short_video_minutes <= 180 THEN '121-180'
                   ELSE '>180' END AS bucket, COUNT(*)::int AS count
         FROM pop_journals WHERE entry_date >= ${START} GROUP BY 1`, p),
    query(
      `SELECT MIN(trim(x)) AS label, COUNT(*)::int AS count
         FROM pop_journals,
              jsonb_array_elements_text(CASE WHEN jsonb_typeof(distractors) = 'array' THEN distractors ELSE '[]'::jsonb END) x
        WHERE entry_date >= ${START} AND trim(x) <> '' GROUP BY lower(trim(x)) ORDER BY count DESC, label LIMIT 8`, p),
    query(
      `SELECT (SELECT COUNT(*) FROM pop_schools)::int AS schools,
              (SELECT COUNT(*) FROM pop_classes)::int AS classes,
              (SELECT COUNT(DISTINCT class_id) FROM pop_timetable_entries)::int AS classes_with_timetable,
              (SELECT COUNT(*) FROM pop_notifications)::int AS notifications,
              (SELECT COUNT(*) FROM pop_notification_recipients)::int AS recipients,
              (SELECT COUNT(*) FROM pop_notification_recipients WHERE delivered_at IS NOT NULL)::int AS delivered,
              (SELECT COUNT(*) FROM pop_notification_recipients WHERE read_at IS NOT NULL)::int AS read,
              (SELECT COUNT(*) FROM pop_users u WHERE u.role = 'STUDENT'
                  AND NOT EXISTS (SELECT 1 FROM pop_class_members m WHERE m.user_id = u.id AND m.status = 'APPROVED'))::int AS students_without_class`),
    query("SELECT status AS label, COUNT(*)::int AS count FROM pop_class_members GROUP BY status"),
    query(
      `SELECT c.id, c.name, s.name AS school_name, t.full_name AS teacher_name,
              (SELECT COUNT(*)::int FROM pop_class_members m WHERE m.class_id = c.id AND m.status = 'APPROVED') AS students,
              (SELECT COUNT(*)::int FROM pop_class_members m WHERE m.class_id = c.id AND m.status = 'PENDING') AS pending,
              (SELECT COUNT(DISTINCT f.user_id)::int FROM pop_focus_sessions f
                 JOIN pop_class_members m ON m.user_id = f.user_id AND m.class_id = c.id AND m.status = 'APPROVED'
                WHERE (f.created_at AT TIME ZONE $1)::date >= ${START}) AS active_students,
              (SELECT COALESCE(SUM(f.actual_minutes), 0)::int FROM pop_focus_sessions f
                 JOIN pop_class_members m ON m.user_id = f.user_id AND m.class_id = c.id AND m.status = 'APPROVED'
                WHERE f.status = 'COMPLETED' AND (f.created_at AT TIME ZONE $1)::date >= ${START}) AS focus_minutes,
              (SELECT ROUND(AVG(j.estimated_short_video_minutes))::int FROM pop_journals j
                 JOIN pop_class_members m ON m.user_id = j.user_id AND m.class_id = c.id AND m.status = 'APPROVED'
                WHERE j.entry_date >= ${START}) AS avg_video
         FROM pop_classes c
         JOIN pop_schools s ON s.id = c.school_id
         JOIN pop_users t ON t.id = c.teacher_id
        ORDER BY students DESC, s.name, c.name LIMIT 200`, p)
  ]);

  const act = activeCounts.rows[0];
  const ft = focusTotals.rows[0];
  const jt = journalTotals.rows[0];
  const cl = classroom.rows[0];
  const dist = {};
  for (const group of GROUPS) {
    dist[group] = fillBuckets(
      assessDist.rows.filter((row) => row.grp === group).map((row) => ({ bucket: row.bucket, count: row.count })),
      [1, 2, 3, 4, 5]
    ).map((item) => item.count);
  }

  return {
    days,
    timeZone,
    generatedAt: new Date().toISOString(),
    users: {
      total: act.total_users,
      byRole: labelCount(roles.rows),
      active1: act.active_1,
      active7: act.active_7,
      active30: act.active_30,
      neverActive: Math.max(0, act.total_users - act.ever_active),
      funnel: [
        { label: "Đã đăng ký", count: act.total_users },
        { label: "Đã vào lớp", count: act.with_class },
        { label: "Đã làm bài đánh giá", count: act.with_assessment },
        { label: "Đã lập kế hoạch", count: act.with_plan },
        { label: "Đã tập trung", count: act.with_focus },
        { label: "Đã viết nhật ký", count: act.with_journal }
      ],
      signupsPerDay: signups.rows,
      activePerDay: activity.rows,
      newInRange: signups.rows.reduce((sum, row) => sum + row.count, 0)
    },
    assessments: {
      total: assessTotals.rows[0].total,
      users: assessTotals.rows[0].users,
      perDay: assessPerDay.rows,
      avg: Object.fromEntries(GROUPS.map((group) => [group, round1(assessAvg.rows.find((row) => row.label === group)?.avg)])),
      distribution: dist
    },
    plans: {
      total: planTotals.rows[0].total,
      avgLimitMinutes: planTotals.rows[0].avg_limit,
      sessionDuration: planDuration.rows.map((row) => ({ label: `${row.bucket} phút`, count: row.count, sort: Number(row.bucket) })).sort((a, b) => a.sort - b.sort).map(({ label, count }) => ({ label, count })),
      targetDays: planTarget.rows.map((row) => ({ label: `${row.bucket} ngày`, count: row.count })),
      limitBuckets: fillBuckets(planLimit.rows, ["0-30", "31-60", "61-120", ">120"]).map((item) => ({ label: `${item.key} phút`, count: item.count })),
      platforms: labelCount(planPlatforms.rows)
    },
    focus: {
      users: ft.users,
      sessions: ft.sessions,
      completedMinutes: ft.minutes,
      interruptions: ft.interruptions,
      completionRate: ft.completion_rate,
      avgRating: round1(ft.avg_rating),
      byStatus: labelCount(focusStatus.rows),
      perDay: focusPerDay.rows,
      byHour: fillBuckets(focusHours.rows, Array.from({ length: 24 }, (_, hour) => hour)).map((item) => item.count),
      rating: fillBuckets(focusRating.rows, [1, 2, 3, 4, 5]).map((item) => item.count),
      reasons: labelCount(focusReasons.rows),
      targetMinutes: focusTarget.rows
        .map((row) => ({ label: `${row.bucket} phút`, count: row.count, sort: Number(row.bucket) }))
        .sort((a, b) => a.sort - b.sort).map(({ label, count }) => ({ label, count }))
    },
    journals: {
      users: jt.users,
      entries: jt.entries,
      avgVideoMinutes: jt.avg_video ?? 0,
      avgFocusMinutes: jt.avg_focus ?? 0,
      avgInterruptions: round1(jt.avg_interruptions) ?? 0,
      perDay: journalPerDay.rows,
      videoBuckets: fillBuckets(journalBuckets.rows, ["0-30", "31-60", "61-120", "121-180", ">180"]).map((item) => ({ label: `${item.key} phút`, count: item.count })),
      distractors: labelCount(journalDistractors.rows)
    },
    classroom: {
      schools: cl.schools,
      classes: cl.classes,
      classesWithTimetable: cl.classes_with_timetable,
      notifications: cl.notifications,
      recipients: cl.recipients,
      delivered: cl.delivered,
      read: cl.read,
      studentsWithoutClass: cl.students_without_class,
      members: labelCount(memberStatus.rows),
      table: classes.rows.map((row) => ({
        id: row.id, name: row.name, schoolName: row.school_name, teacherName: row.teacher_name,
        students: row.students, pending: row.pending, activeStudents: row.active_students,
        focusMinutes: row.focus_minutes, avgVideoMinutes: row.avg_video
      }))
    }
  };
}

const USER_SELECT = `
  WITH f AS (
    SELECT user_id, COUNT(*)::int AS sessions, COUNT(*) FILTER (WHERE status = 'COMPLETED')::int AS completed,
           COALESCE(SUM(actual_minutes) FILTER (WHERE status = 'COMPLETED'), 0)::int AS minutes,
           COALESCE(SUM(interruption_count), 0)::int AS interruptions, MAX(created_at) AS last_at
      FROM pop_focus_sessions GROUP BY user_id),
  j AS (
    SELECT user_id, COUNT(*)::int AS days, ROUND(AVG(estimated_short_video_minutes))::int AS avg_video, MAX(updated_at) AS last_at
      FROM pop_journals GROUP BY user_id),
  a AS (SELECT user_id, COUNT(*)::int AS cnt, MAX(completed_at) AS last_at FROM pop_assessment_results GROUP BY user_id),
  la AS (SELECT DISTINCT ON (user_id) user_id, scores FROM pop_assessment_results ORDER BY user_id, completed_at DESC)
  SELECT u.id, u.email, u.full_name, u.role, u.created_at,
         c.name AS class_name, s.name AS school_name,
         COALESCE(f.sessions, 0) AS focus_sessions, COALESCE(f.completed, 0) AS completed_sessions,
         COALESCE(f.minutes, 0) AS focus_minutes, COALESCE(f.interruptions, 0) AS interruptions,
         COALESCE(j.days, 0) AS journal_days, j.avg_video AS avg_video_minutes,
         COALESCE(a.cnt, 0) AS assessment_count, la.scores AS latest_scores,
         (p.user_id IS NOT NULL) AS has_plan,
         GREATEST(f.last_at, j.last_at, a.last_at) AS last_active,
         COUNT(*) OVER()::int AS total
    FROM pop_users u
    LEFT JOIN f ON f.user_id = u.id
    LEFT JOIN j ON j.user_id = u.id
    LEFT JOIN a ON a.user_id = u.id
    LEFT JOIN la ON la.user_id = u.id
    LEFT JOIN pop_personal_plans p ON p.user_id = u.id
    LEFT JOIN pop_class_members m ON m.user_id = u.id AND m.status = 'APPROVED'
    LEFT JOIN pop_classes c ON c.id = m.class_id
    LEFT JOIN pop_schools s ON s.id = c.school_id`;

const scoresDto = (scores) => (scores && typeof scores === "object"
  ? Object.fromEntries(GROUPS.map((group) => [group, scores[group] === undefined ? null : round1(scores[group])]))
  : null);

const userDto = (row) => ({
  id: row.id,
  email: row.email,
  fullName: row.full_name,
  role: row.role,
  createdAt: row.created_at,
  lastActive: row.last_active,
  className: row.class_name,
  schoolName: row.school_name,
  focusSessions: row.focus_sessions,
  completedSessions: row.completed_sessions,
  focusMinutes: row.focus_minutes,
  interruptions: row.interruptions,
  journalDays: row.journal_days,
  avgVideoMinutes: row.avg_video_minutes,
  assessmentCount: row.assessment_count,
  latestScores: scoresDto(row.latest_scores),
  hasPlan: row.has_plan
});

export async function listUsers(options) {
  const { search, role, sort, dir, pageSize, offset } = options;
  const column = USER_SORTS[sort];
  const direction = dir === "asc" ? "ASC" : "DESC";
  const pattern = `%${escapeLike(search)}%`;
  const result = await query(
    `${USER_SELECT}
      WHERE ($1 = '' OR u.full_name ILIKE $2 OR u.email ILIKE $2) AND ($3 = '' OR u.role = $3)
      ORDER BY ${column} ${direction} NULLS LAST, u.id
      LIMIT $4 OFFSET $5`,
    [search, pattern, role, pageSize, offset]
  );
  return { total: result.rows[0]?.total ?? 0, users: result.rows.map(userDto) };
}

export async function loadUserDetail(userId, timeZone) {
  const found = await query(`${USER_SELECT} WHERE u.id = $1`, [userId]);
  if (found.rowCount !== 1) return null;
  const p = [timeZone, 30];
  const [assessments, focusDays, journalDays, plan, statuses] = await Promise.all([
    query("SELECT completed_at, scores FROM pop_assessment_results WHERE user_id = $1 ORDER BY completed_at DESC LIMIT 20", [userId]),
    query(
      `SELECT d.day::text AS day, COALESCE(SUM(f.actual_minutes) FILTER (WHERE f.status = 'COMPLETED'), 0)::int AS minutes
         FROM (${SERIES}) d
         LEFT JOIN pop_focus_sessions f ON f.user_id = $3 AND (f.created_at AT TIME ZONE $1)::date = d.day
        GROUP BY d.day ORDER BY d.day`, [...p, userId]),
    query(
      `SELECT d.day::text AS day, j.estimated_short_video_minutes AS video, j.focus_minutes AS focus
         FROM (${SERIES}) d LEFT JOIN pop_journals j ON j.user_id = $3 AND j.entry_date = d.day
        ORDER BY d.day`, [...p, userId]),
    query(
      `SELECT plan->'dailyShortVideoLimitMinutes' AS limit_minutes, plan->'sessionDurationMinutes' AS session_minutes,
              plan->'targetDays' AS target_days, plan->'trackedPlatforms' AS platforms, updated_at
         FROM pop_personal_plans WHERE user_id = $1`, [userId]),
    query("SELECT status AS label, COUNT(*)::int AS count FROM pop_focus_sessions WHERE user_id = $1 GROUP BY status", [userId])
  ]);
  const planRow = plan.rows[0];
  return {
    user: userDto(found.rows[0]),
    assessments: assessments.rows.map((row) => ({ completedAt: row.completed_at, scores: scoresDto(row.scores) })),
    focusPerDay: focusDays.rows,
    journalPerDay: journalDays.rows.map((row) => ({ day: row.day, video: row.video, focus: row.focus })),
    focusByStatus: labelCount(statuses.rows),
    plan: planRow ? {
      dailyShortVideoLimitMinutes: planRow.limit_minutes,
      sessionDurationMinutes: planRow.session_minutes,
      targetDays: planRow.target_days,
      trackedPlatforms: Array.isArray(planRow.platforms) ? planRow.platforms : [],
      updatedAt: planRow.updated_at
    } : null
  };
}

export async function loadSurveyOverview() {
  const result = await query(
    `SELECT a.id, a.user_id, u.full_name, u.email, u.role, a.phase, a.completed_at,
            a.answers, a.scores, a.profile, a.open_answers, c.name AS class_name, s.name AS school_name
       FROM pop_assessment_results a
       JOIN pop_users u ON u.id = a.user_id
       LEFT JOIN pop_class_members m ON m.user_id = u.id AND m.status = 'APPROVED'
       LEFT JOIN pop_classes c ON c.id = m.class_id
       LEFT JOIN pop_schools s ON s.id = c.school_id
      ORDER BY a.completed_at DESC
      LIMIT 10000`
  );
  const groups = ["VN", "PT", "PB", "TT", "DG"];
  const averages = (phase) => Object.fromEntries(groups.map((group) => {
    const values = result.rows.filter((row) => row.phase === phase).map((row) => Number(row.scores?.[group])).filter(Number.isFinite);
    return [group, values.length ? Number((values.reduce((a, b) => a + b, 0) / values.length).toFixed(2)) : null];
  }));
  return {
    total: result.rowCount,
    phases: { PRE: result.rows.filter((row) => row.phase === "PRE").length, POST: result.rows.filter((row) => row.phase === "POST").length },
    averages: { PRE: averages("PRE"), POST: averages("POST") },
    records: result.rows.map((row) => ({
      id: row.id, userId: row.user_id, fullName: row.full_name, email: row.email, role: row.role,
      phase: row.phase, completedAt: row.completed_at, className: row.class_name, schoolName: row.school_name,
      answers: row.answers, scores: row.scores, profile: row.profile, openAnswers: row.open_answers
    }))
  };
}
