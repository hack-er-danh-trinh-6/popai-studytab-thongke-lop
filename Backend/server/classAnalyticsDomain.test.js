import test from "node:test";
import assert from "node:assert/strict";
import {
  attentionList, fillHours, fillRatings, levelDistribution, levelOf, summarize, topStudents
} from "./classAnalyticsDomain.js";

const mk = (over = {}) => ({
  userId: "u", fullName: "A", email: "a@x.vn", focusMinutes: 0, completedSessions: 0, totalSessions: 0,
  interruptions: 0, activeDays: 0, lastActivity: null, ratingSum: 0, ratingCount: 0, ...over
});

test("levelOf phân mức theo phút trung bình mỗi ngày", () => {
  assert.equal(levelOf(0, 7), "none");
  assert.equal(levelOf(70, 7), "low");
  assert.equal(levelOf(140, 7), "mid");
  assert.equal(levelOf(280, 7), "good");
  assert.equal(levelOf(420, 7), "great");
});

test("levelDistribution đếm đủ các mức", () => {
  const result = levelDistribution([mk(), mk({ focusMinutes: 420 }), mk({ focusMinutes: 430 })], 7);
  assert.equal(result.find((r) => r.key === "none").count, 1);
  assert.equal(result.find((r) => r.key === "great").count, 2);
  assert.equal(result.reduce((s, r) => s + r.count, 0), 3);
});

test("summarize xử lý lớp rỗng không chia cho 0", () => {
  const s = summarize([], 7);
  assert.equal(s.studentCount, 0);
  assert.equal(s.completionRate, 0);
  assert.equal(s.avgRating, 0);
});

test("summarize tính tỉ lệ tham gia và hoàn thành", () => {
  const s = summarize([
    mk({ focusMinutes: 100, completedSessions: 2, totalSessions: 4, interruptions: 4, ratingSum: 8, ratingCount: 2 }),
    mk({})
  ], 10);
  assert.equal(s.participationRate, 50);
  assert.equal(s.completionRate, 50);
  assert.equal(s.interruptionsPerSession, 1);
  assert.equal(s.avgRating, 4);
  assert.equal(s.avgMinutesPerStudent, 50);
  assert.equal(s.avgMinutesPerStudentPerDay, 5);
});

test("topStudents bỏ người 0 phút và sắp xếp giảm dần", () => {
  const list = topStudents([mk({ fullName: "B", focusMinutes: 10 }), mk({ fullName: "C", focusMinutes: 50 }), mk({ fullName: "D" })]);
  assert.deepEqual(list.map((s) => s.fullName), ["C", "B"]);
});

test("attentionList nhận diện các lý do", () => {
  const now = new Date("2026-10-29T00:00:00Z").getTime();
  const list = attentionList([
    mk({ userId: "1", fullName: "Không HĐ" }),
    mk({ userId: "2", fullName: "Lâu rồi", totalSessions: 1, completedSessions: 1, lastActivity: "2026-10-20T00:00:00Z" }),
    mk({ userId: "3", fullName: "Hay bị ngắt", totalSessions: 3, completedSessions: 3, interruptions: 12, lastActivity: "2026-10-28T00:00:00Z" }),
    mk({ userId: "4", fullName: "Ổn", totalSessions: 3, completedSessions: 3, interruptions: 1, lastActivity: "2026-10-28T00:00:00Z" }),
    mk({ userId: "5", fullName: "Bỏ dở", totalSessions: 4, completedSessions: 1, lastActivity: "2026-10-28T00:00:00Z" })
  ], now);
  const byId = Object.fromEntries(list.map((i) => [i.userId, i.reasons]));
  assert.deepEqual(byId["1"], ["NO_ACTIVITY"]);
  assert.deepEqual(byId["2"], ["INACTIVE"]);
  assert.deepEqual(byId["3"], ["INTERRUPTED"]);
  assert.deepEqual(byId["5"], ["LOW_COMPLETION"]);
  assert.equal(byId["4"], undefined);
  assert.equal(list[0].userId, "1");
});

test("fillHours và fillRatings điền đủ mốc", () => {
  const hours = fillHours([{ hour: 20, focus_minutes: "45" }]);
  assert.equal(hours.length, 24);
  assert.equal(hours[20].focusMinutes, 45);
  assert.equal(hours[3].focusMinutes, 0);
  assert.deepEqual(fillRatings([{ rating: 5, count: "2" }]).map((r) => r.count), [0, 0, 0, 0, 2]);
});
