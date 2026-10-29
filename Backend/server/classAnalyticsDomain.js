// Hàm thuần cho tab "Thống kê lớp" của trang giáo viên: phân nhóm học sinh, xếp hạng, cảnh báo cần quan tâm.

/** Các mức tập trung theo số phút trung bình mỗi ngày trong kỳ. */
export const LEVELS = [
  { key: "none", label: "Chưa tập trung", min: 0, max: 0 },
  { key: "low", label: "Dưới 15 phút/ngày", min: 0, max: 15 },
  { key: "mid", label: "15–30 phút/ngày", min: 15, max: 30 },
  { key: "good", label: "30–60 phút/ngày", min: 30, max: 60 },
  { key: "great", label: "Từ 60 phút/ngày", min: 60, max: Infinity }
];

export function levelOf(focusMinutes, days) {
  const perDay = days > 0 ? focusMinutes / days : 0;
  if (focusMinutes <= 0) return "none";
  if (perDay < 15) return "low";
  if (perDay < 30) return "mid";
  if (perDay < 60) return "good";
  return "great";
}

export function levelDistribution(students, days) {
  const counts = new Map(LEVELS.map((level) => [level.key, 0]));
  for (const student of students) {
    const key = levelOf(student.focusMinutes, days);
    counts.set(key, counts.get(key) + 1);
  }
  return LEVELS.map((level) => ({ key: level.key, label: level.label, count: counts.get(level.key) }));
}

const ratio = (a, b) => (b > 0 ? Math.round((a / b) * 1000) / 10 : 0);
const avg = (a, b, digits = 1) => (b > 0 ? Math.round((a / b) * 10 ** digits) / 10 ** digits : 0);

/** Số liệu tổng hợp cả lớp từ danh sách học sinh đã cộng dồn. */
export function summarize(students, days) {
  const total = students.reduce((sum, s) => ({
    focusMinutes: sum.focusMinutes + s.focusMinutes,
    completedSessions: sum.completedSessions + s.completedSessions,
    totalSessions: sum.totalSessions + s.totalSessions,
    interruptions: sum.interruptions + s.interruptions,
    activeStudents: sum.activeStudents + (s.totalSessions > 0 ? 1 : 0),
    ratingSum: sum.ratingSum + s.ratingSum,
    ratingCount: sum.ratingCount + s.ratingCount
  }), { focusMinutes: 0, completedSessions: 0, totalSessions: 0, interruptions: 0, activeStudents: 0, ratingSum: 0, ratingCount: 0 });
  const count = students.length;
  return {
    studentCount: count,
    activeStudents: total.activeStudents,
    inactiveStudents: count - total.activeStudents,
    participationRate: ratio(total.activeStudents, count),
    focusMinutes: total.focusMinutes,
    avgMinutesPerStudent: avg(total.focusMinutes, count),
    avgMinutesPerStudentPerDay: avg(total.focusMinutes, count * days),
    completedSessions: total.completedSessions,
    totalSessions: total.totalSessions,
    completionRate: ratio(total.completedSessions, total.totalSessions),
    interruptions: total.interruptions,
    interruptionsPerSession: avg(total.interruptions, total.totalSessions),
    avgRating: avg(total.ratingSum, total.ratingCount)
  };
}

/** Top học sinh theo phút tập trung (chỉ người có phút > 0). */
export function topStudents(students, limit = 5) {
  return [...students]
    .filter((s) => s.focusMinutes > 0)
    .sort((a, b) => b.focusMinutes - a.focusMinutes || b.activeDays - a.activeDays || a.fullName.localeCompare(b.fullName, "vi"))
    .slice(0, limit);
}

/**
 * Học sinh cần giáo viên quan tâm. Lý do (mỗi học sinh có thể nhiều lý do):
 * - NO_ACTIVITY: chưa có phiên nào trong kỳ
 * - INACTIVE: phiên gần nhất cách đây >= inactiveDays
 * - INTERRUPTED: trung bình >= 3 lần bị ngắt mỗi phiên (từ 3 phiên trở lên)
 * - LOW_COMPLETION: hoàn thành dưới 50% số phiên (từ 3 phiên trở lên)
 */
export function attentionList(students, now = Date.now(), inactiveDays = 3) {
  const out = [];
  for (const s of students) {
    const reasons = [];
    if (s.totalSessions === 0) {
      reasons.push("NO_ACTIVITY");
    } else {
      const idleDays = s.lastActivity ? (now - new Date(s.lastActivity).getTime()) / 86400000 : Infinity;
      if (idleDays >= inactiveDays) reasons.push("INACTIVE");
      if (s.totalSessions >= 3 && s.interruptions / s.totalSessions >= 3) reasons.push("INTERRUPTED");
      if (s.totalSessions >= 3 && s.completedSessions / s.totalSessions < 0.5) reasons.push("LOW_COMPLETION");
    }
    if (reasons.length) out.push({ userId: s.userId, fullName: s.fullName, email: s.email, reasons, lastActivity: s.lastActivity, totalSessions: s.totalSessions });
  }
  const weight = (item) => (item.reasons.includes("NO_ACTIVITY") ? 100 : 0) + item.reasons.length;
  return out.sort((a, b) => weight(b) - weight(a) || a.fullName.localeCompare(b.fullName, "vi"));
}

/** Đủ 24 giờ, giờ thiếu = 0. */
export function fillHours(rows) {
  const map = new Map(rows.map((row) => [Number(row.hour), Number(row.focus_minutes)]));
  return Array.from({ length: 24 }, (_, hour) => ({ hour, focusMinutes: map.get(hour) ?? 0 }));
}

/** Đủ các mức 1..5, mức thiếu = 0. */
export function fillRatings(rows) {
  const map = new Map(rows.map((row) => [Number(row.rating), Number(row.count)]));
  return [1, 2, 3, 4, 5].map((rating) => ({ rating, count: map.get(rating) ?? 0 }));
}
