export function normalizeEmail(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

export function parseJournalDate(value) {
  if (typeof value !== "string") return null;
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return null;
  const [, dayText, monthText, yearText] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) return null;
  return `${yearText}-${monthText}-${dayText}`;
}

export function formatJournalDate(isoDate) {
  const [year, month, day] = isoDate.slice(0, 10).split("-");
  return `${day}/${month}/${year}`;
}

export const SURVEY_PHASES = { PRE: "PRE", POST: "POST" };
export const SURVEY_GROUPS = ["VN", "PT", "PB", "TT"];

export function scoreSurvey(phase, answers, includeDg = phase === SURVEY_PHASES.POST) {
  const isPost = phase === SURVEY_PHASES.POST;
  const expectedCount = isPost && includeDg ? 25 : 20;
  if (!Array.isArray(answers) || answers.length !== expectedCount) {
    throw new Error(`Vui lòng gửi đủ ${expectedCount} câu trả lời.`);
  }
  const seen = new Set();
  const groups = { VN: [], PT: [], PB: [], TT: [], ...(isPost && includeDg ? { DG: [] } : {}) };
  for (const answer of answers) {
    const questionId = answer?.questionId;
    const score = Number(answer?.score);
    if (
      typeof questionId !== "string" ||
      !/^(VN|PT|PB|TT|DG)[1-5]$/.test(questionId) ||
      (!isPost && questionId.startsWith("DG")) ||
      (!includeDg && questionId.startsWith("DG")) ||
      (isPost && !questionId.startsWith("DG") && !SURVEY_GROUPS.some((group) => questionId.startsWith(group))) ||
      !Number.isInteger(score) || score < 1 || score > 5 ||
      seen.has(questionId)
    ) throw new Error("Câu trả lời không hợp lệ.");
    seen.add(questionId);
    groups[questionId.slice(0, 2)].push(score);
  }
  for (const group of Object.values(groups)) {
    if (group.length !== 5) throw new Error("Bộ câu hỏi không đầy đủ.");
  }
  return Object.fromEntries(
    Object.entries(groups).map(([key, values]) => [
      key,
      Math.round((values.reduce((sum, value) => sum + value, 0) / values.length) * 10) / 10
    ])
  );
}

export function scoreAssessment(answers) {
  return scoreSurvey(SURVEY_PHASES.PRE, answers);
}

/** Một phiên tập trung hoàn thành dài từ ngần này phút trở lên sẽ được tính 1 streak cho ngày đó. */
export const FOCUS_STREAK_MINUTES = 60;

/**
 * Đếm chuỗi ngày liên tiếp. `dates` là các ngày yyyy-MM-dd khác nhau, sắp xếp giảm dần.
 * Chuỗi còn hiệu lực nếu ngày mới nhất là hôm nay hoặc hôm qua (hôm nay chưa tập thì chuỗi chưa đứt).
 */
export function computeStreak(dates, endDate) {
  const dayMs = 86400000;
  const shift = (isoDate, days) => new Date(Date.parse(`${isoDate}T00:00:00Z`) + days * dayMs).toISOString().slice(0, 10);
  const yesterday = shift(endDate, -1);
  if (!dates.length || (dates[0] !== endDate && dates[0] !== yesterday)) return 0;
  let streak = 0;
  let expected = dates[0];
  for (const date of dates) {
    if (date !== expected) break;
    streak += 1;
    expected = shift(expected, -1);
  }
  return streak;
}
