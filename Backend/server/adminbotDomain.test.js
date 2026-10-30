import test from "node:test";
import assert from "node:assert/strict";
import {
  SEED_DEFAULTS, buildAssessment, buildFocusSessions, buildJournals, buildPersonalPlan,
  buildStudentData, createRandom, parseSeedOptions, summarizeGenerated
} from "./adminbotDomain.js";

const NOW = new Date("2026-10-30T12:00:00Z");

test("parseSeedOptions giới hạn giá trị ngoài khoảng và giữ mặc định", () => {
  const options = parseSeedOptions({ days: 500, sessionsPerDay: -3, activityRate: 5, assessmentsPerStudent: "2" });
  assert.equal(options.days, 90);
  assert.equal(options.sessionsPerDay, 0);
  assert.equal(options.activityRate, 1);
  assert.equal(options.assessmentsPerStudent, 2);
  assert.deepEqual(parseSeedOptions(null), SEED_DEFAULTS);
});

test("createRandom sinh số xác định theo seed", () => {
  const first = createRandom("hocsinh.bot01@popai.test");
  const second = createRandom("hocsinh.bot01@popai.test");
  assert.deepEqual([first(), first(), first()], [second(), second(), second()]);
  assert.notEqual(createRandom("khac")(), createRandom("seed-khac")());
});

test("phiên tập trung nằm trong ràng buộc của API /focus/session", () => {
  const options = parseSeedOptions({ days: 7, sessionsPerDay: 3, activityRate: 1 });
  const sessions = buildFocusSessions({ email: "hocsinh.bot02@popai.test", options, now: NOW });
  assert.ok(sessions.length > 0);
  for (const session of sessions) {
    assert.ok(session.targetMinutes >= 1 && session.targetMinutes <= 600);
    assert.ok(session.actualMinutes >= 0 && session.actualMinutes <= 600);
    assert.ok(session.interruptionCount >= 0);
    assert.ok(session.selfRating >= 0 && session.selfRating <= 5);
    assert.ok(["NOT_STARTED", "RUNNING", "PAUSED", "COMPLETED"].includes(session.status));
    assert.ok(session.createdAt.getTime() <= NOW.getTime());
    assert.ok(session.clientSessionId.startsWith("bot-"));
  }
  const ids = new Set(sessions.map((s) => s.clientSessionId));
  assert.equal(ids.size, sessions.length, "clientSessionId phải duy nhất trong một học sinh");
});

test("cùng seed cho ra cùng dữ liệu (chạy lại không sinh khác)", () => {
  const options = parseSeedOptions({ days: 5, sessionsPerDay: 2 });
  const a = buildFocusSessions({ email: "hocsinh.bot03@popai.test", options, now: NOW });
  const b = buildFocusSessions({ email: "hocsinh.bot03@popai.test", options, now: NOW });
  assert.deepEqual(a, b);
});

test("nhật ký khớp số phút tập trung của các phiên hoàn thành", () => {
  const options = parseSeedOptions({ days: 10, sessionsPerDay: 2, activityRate: 1 });
  const sessions = buildFocusSessions({ email: "hocsinh.bot04@popai.test", options, now: NOW });
  const journals = buildJournals({ email: "hocsinh.bot04@popai.test", options, sessions, now: NOW });
  assert.ok(journals.length > 0);
  const expected = sessions
    .filter((s) => s.status === "COMPLETED")
    .reduce((sum, s) => sum + s.actualMinutes, 0);
  const actual = journals.reduce((sum, j) => sum + j.focusMinutes, 0);
  assert.equal(actual, expected);
  for (const journal of journals) {
    assert.match(journal.entryDate, /^\d{4}-\d{2}-\d{2}$/);
    assert.ok(journal.focusMinutes >= 0 && journal.focusMinutes <= 1440);
  }
});

test("bài tự đánh giá đủ 20 câu và chấm điểm đúng 4 nhóm", () => {
  const assessment = buildAssessment({ email: "hocsinh.bot05@popai.test", index: 0, now: NOW });
  assert.equal(assessment.answers.length, 20);
  assert.deepEqual(Object.keys(assessment.scores).sort(), ["PB", "PT", "TT", "VN"]);
  for (const value of Object.values(assessment.scores)) assert.ok(value >= 1 && value <= 5);
  assert.ok(assessment.completedAt.getTime() <= NOW.getTime());
});

test("kế hoạch cá nhân thoả ràng buộc của API /plan", () => {
  const plan = buildPersonalPlan({ email: "hocsinh.bot06@popai.test", now: NOW });
  assert.ok(plan.dailyShortVideoLimitMinutes >= 0 && plan.dailyShortVideoLimitMinutes <= 1440);
  assert.ok([10, 15, 25, 30, 45].includes(plan.sessionDurationMinutes));
  assert.ok([7, 30].includes(plan.targetDays));
  assert.ok(Array.isArray(plan.studyTasks) && plan.studyTasks.length > 0);
  assert.ok(plan.trackedPlatforms.length <= 10);
});

test("buildStudentData tôn trọng tuỳ chọn tắt và summarizeGenerated cộng đúng", () => {
  const options = parseSeedOptions({
    days: 3, sessionsPerDay: 2, assessmentsPerStudent: 0,
    includeJournals: false, includePlans: false
  });
  const data = buildStudentData({ email: "hocsinh.bot07@popai.test", options, now: NOW });
  assert.equal(data.journals.length, 0);
  assert.equal(data.assessments.length, 0);
  assert.equal(data.plan, null);
  const summary = summarizeGenerated([
    { sessions: 4, journals: 0, assessments: 0, plans: 0 },
    { sessions: 2, journals: 3, assessments: 1, plans: 1 }
  ]);
  assert.deepEqual(summary, { students: 2, sessions: 6, journals: 3, assessments: 1, plans: 1 });
});
