import { scoreAssessment } from "./domain.js";

/**
 * Logic thuần cho trang /adminbot: kiểm tra tham số và sinh dữ liệu học tập giả
 * (phiên tập trung, nhật ký, bài tự đánh giá, kế hoạch cá nhân) cho tài khoản bot.
 * Không truy cập database để có thể kiểm thử độc lập.
 */

export const SEED_DEFAULTS = {
  days: 14,
  sessionsPerDay: 2,
  activityRate: 0.75,
  assessmentsPerStudent: 1,
  includeJournals: true,
  includeAssessments: true,
  includePlans: true,
  resetFirst: true,
  alignJoinDate: true
};

export const SEED_LIMITS = {
  days: { min: 1, max: 90 },
  sessionsPerDay: { min: 0, max: 6 },
  assessmentsPerStudent: { min: 0, max: 5 }
};

const clampInt = (value, { min, max }, fallback) => {
  if (value === undefined || value === null || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.trunc(parsed)));
};

const clampRate = (value, fallback) => {
  if (value === undefined || value === null || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(1, Math.max(0, parsed));
};

const asBool = (value, fallback) => (typeof value === "boolean" ? value : fallback);

/** Chuẩn hoá tuỳ chọn gửi từ trang adminbot. Không bao giờ ném lỗi. */
export function parseSeedOptions(body) {
  const source = body && typeof body === "object" ? body : {};
  return {
    days: clampInt(source.days, SEED_LIMITS.days, SEED_DEFAULTS.days),
    sessionsPerDay: clampInt(source.sessionsPerDay, SEED_LIMITS.sessionsPerDay, SEED_DEFAULTS.sessionsPerDay),
    assessmentsPerStudent: clampInt(
      source.assessmentsPerStudent,
      SEED_LIMITS.assessmentsPerStudent,
      SEED_DEFAULTS.assessmentsPerStudent
    ),
    activityRate: clampRate(source.activityRate, SEED_DEFAULTS.activityRate),
    includeJournals: asBool(source.includeJournals, SEED_DEFAULTS.includeJournals),
    includeAssessments: asBool(source.includeAssessments, SEED_DEFAULTS.includeAssessments),
    includePlans: asBool(source.includePlans, SEED_DEFAULTS.includePlans),
    resetFirst: asBool(source.resetFirst, SEED_DEFAULTS.resetFirst),
    // Trang giáo viên chỉ tính dữ liệu từ lúc học sinh được duyệt vào lớp, nên với lớp bot
    // cần lùi ngày tham gia để các phiên vừa sinh nằm trong khoảng thống kê.
    alignJoinDate: asBool(source.alignJoinDate, SEED_DEFAULTS.alignJoinDate)
  };
}

/** PRNG nhỏ, xác định theo seed để dữ liệu sinh ra ổn định giữa các lần chạy. */
export function createRandom(seedText) {
  let hash = 2166136261;
  for (const char of String(seedText)) {
    hash ^= char.codePointAt(0);
    hash = Math.imul(hash, 16777619);
  }
  let state = hash >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const intBetween = (random, min, max) => min + Math.floor(random() * (max - min + 1));
const pick = (random, list) => list[Math.floor(random() * list.length)];

export const TASK_NAMES = [
  "Toán - Giải tích",
  "Toán - Hình học",
  "Ngữ văn - Nghị luận",
  "Tiếng Anh - Reading",
  "Tiếng Anh - Listening",
  "Vật lý - Bài tập chương",
  "Hóa học - Ôn tập",
  "Sinh học - Ghi nhớ",
  "Lịch sử - Sơ đồ tư duy",
  "Địa lý - Kỹ năng bản đồ",
  "Làm đề tham khảo",
  "Ôn tập giữa kỳ"
];

export const INTERRUPTION_REASONS = [
  "Thông báo điện thoại",
  "Mở mạng xã hội",
  "Người khác gọi",
  "Mệt, cần nghỉ ngắn",
  "Tìm tài liệu",
  "Tiếng ồn xung quanh"
];

export const DISTRACTORS = [
  "TikTok",
  "YouTube Shorts",
  "Facebook",
  "Instagram",
  "Game mobile",
  "Nhóm chat lớp"
];

export const TARGET_DURATIONS = [15, 25, 30, 45];

const dayStartUtc = (now, daysAgo) => {
  const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  date.setUTCDate(date.getUTCDate() - daysAgo);
  return date;
};

/**
 * Sinh danh sách phiên tập trung cho một học sinh.
 * `clientSessionId` có dạng bot-<email>-<ngày>-<số thứ tự> nên chạy lại không tạo bản ghi trùng.
 */
export function buildFocusSessions({ email, options, now = new Date() }) {
  const random = createRandom(`focus:${email}:${options.days}:${options.sessionsPerDay}:${options.activityRate}`);
  const sessions = [];
  for (let dayOffset = options.days - 1; dayOffset >= 0; dayOffset -= 1) {
    if (random() > options.activityRate) continue;
    const day = dayStartUtc(now, dayOffset);
    const dayKey = day.toISOString().slice(0, 10);
    const sessionCount = Math.max(1, intBetween(random, 1, Math.max(1, options.sessionsPerDay)));
    let hour = intBetween(random, 7, 15);
    for (let index = 0; index < sessionCount; index += 1) {
      const targetMinutes = pick(random, TARGET_DURATIONS);
      const completed = random() < 0.9;
      const actualMinutes = completed
        ? Math.max(1, Math.min(600, targetMinutes + intBetween(random, -5, 10)))
        : Math.max(0, Math.min(targetMinutes - 1, intBetween(random, 1, targetMinutes - 1)));
      const interruptionCount = intBetween(random, 0, completed ? 4 : 8);
      const reasons = Array.from({ length: Math.min(interruptionCount, 3) }, () => pick(random, INTERRUPTION_REASONS));
      const createdAt = new Date(day);
      createdAt.setUTCHours(Math.min(22, hour), intBetween(random, 0, 59), 0, 0);
      hour = Math.min(22, hour + intBetween(random, 1, 3));
      const clampedCreatedAt = createdAt.getTime() > now.getTime()
        ? new Date(now.getTime() - intBetween(random, 5, 120) * 60000)
        : createdAt;
      sessions.push({
        clientSessionId: `bot-${email}-${dayKey}-${index + 1}`,
        taskName: pick(random, TASK_NAMES),
        targetMinutes,
        actualMinutes,
        interruptionCount,
        interruptionReasons: reasons,
        selfRating: completed ? intBetween(random, 3, 5) : intBetween(random, 1, 3),
        status: completed ? "COMPLETED" : "PAUSED",
        createdAt: clampedCreatedAt
      });
    }
  }
  return sessions;
}

/** Sinh nhật ký theo ngày, dựa trên chính các phiên tập trung đã sinh. */
export function buildJournals({ email, options, sessions, now = new Date() }) {
  const random = createRandom(`journal:${email}:${options.days}`);
  const byDay = new Map();
  for (const session of sessions) {
    const key = session.createdAt.toISOString().slice(0, 10);
    const bucket = byDay.get(key) ?? { focusMinutes: 0, interruptions: 0 };
    if (session.status === "COMPLETED") bucket.focusMinutes += session.actualMinutes;
    bucket.interruptions += session.interruptionCount;
    byDay.set(key, bucket);
  }
  return [...byDay.entries()].map(([day, bucket]) => ({
    entryDate: day,
    estimatedShortVideoMinutes: intBetween(random, 20, 180),
    focusMinutes: Math.min(1440, bucket.focusMinutes),
    interruptionCount: bucket.interruptions,
    distractors: Array.from({ length: intBetween(random, 0, 2) }, () => pick(random, DISTRACTORS)),
    mostProductiveSession: pick(random, TASK_NAMES),
    tomorrowAdjustment: pick(random, [
      "Đặt điện thoại xa bàn học.",
      "Chia nhỏ phiên 25 phút, nghỉ 5 phút.",
      "Tắt thông báo mạng xã hội trong giờ học.",
      "Học nhóm buổi tối với bạn cùng tổ."
    ])
  }));
}

/** Sinh một bài tự đánh giá đủ 20 câu (VN/PT/PB/TT, mỗi nhóm 5 câu). */
export function buildAssessment({ email, index, now = new Date() }) {
  const random = createRandom(`assessment:${email}:${index}`);
  const answers = [];
  for (const group of ["VN", "PT", "PB", "TT"]) {
    for (let question = 1; question <= 5; question += 1) {
      answers.push({ questionId: `${group}${question}`, score: intBetween(random, 1, 5) });
    }
  }
  const completedAt = new Date(now.getTime() - intBetween(random, 1, 30) * 86400000);
  return { answers, scores: scoreAssessment(answers), completedAt };
}

/** Sinh kế hoạch cá nhân hợp lệ theo ràng buộc của API /plan. */
export function buildPersonalPlan({ email, now = new Date() }) {
  const random = createRandom(`plan:${email}`);
  const tasks = Array.from(new Set(Array.from({ length: intBetween(random, 3, 5) }, () => pick(random, TASK_NAMES))));
  return {
    dailyShortVideoLimitMinutes: intBetween(random, 20, 90),
    trackedPlatforms: Array.from({ length: intBetween(random, 1, 3) }, () => pick(random, DISTRACTORS)),
    focusProtectionHours: pick(random, ["19:00 - 22:00", "20:00 - 23:00", "05:30 - 07:00", "14:00 - 17:00"]),
    studyTasks: tasks,
    sessionDurationMinutes: pick(random, [10, 15, 25, 30, 45]),
    targetDays: pick(random, [7, 30]),
    updatedAt: now.toISOString()
  };
}

/** Sinh toàn bộ dữ liệu giả cho một học sinh. */
export function buildStudentData({ email, options, now = new Date() }) {
  const sessions = options.sessionsPerDay > 0 ? buildFocusSessions({ email, options, now }) : [];
  return {
    sessions,
    journals: options.includeJournals ? buildJournals({ email, options, sessions, now }) : [],
    assessments: options.includeAssessments
      ? Array.from({ length: options.assessmentsPerStudent }, (_, index) => buildAssessment({ email, index, now }))
      : [],
    plan: options.includePlans ? buildPersonalPlan({ email, now }) : null
  };
}

/** Tổng hợp nhanh để trả về cho trang admin sau khi tạo dữ liệu. */
export function summarizeGenerated(results) {
  return results.reduce(
    (sum, entry) => ({
      students: sum.students + 1,
      sessions: sum.sessions + entry.sessions,
      journals: sum.journals + entry.journals,
      assessments: sum.assessments + entry.assessments,
      plans: sum.plans + entry.plans
    }),
    { students: 0, sessions: 0, journals: 0, assessments: 0, plans: 0 }
  );
}
