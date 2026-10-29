import test from "node:test";
import assert from "node:assert/strict";
import { FOCUS_STREAK_MINUTES, computeStreak, formatJournalDate, normalizeEmail, parseJournalDate, scoreAssessment } from "./domain.js";

test("normalizes account email without altering its content", () => {
  assert.equal(normalizeEmail("  Test@Example.COM "), "test@example.com");
});

test("accepts and formats valid dd/MM/yyyy journal dates", () => {
  assert.equal(parseJournalDate("22/07/2026"), "2026-07-22");
  assert.equal(formatJournalDate("2026-07-22"), "22/07/2026");
});

test("rejects impossible journal dates", () => {
  assert.equal(parseJournalDate("31/02/2026"), null);
  assert.equal(parseJournalDate("2026-02-31"), null);
});

test("calculates the four 5-question averages with one decimal", () => {
  const answers = ["VN", "PT", "PB", "TT"].flatMap((group) =>
    [1, 2, 3, 4, 5].map((n) => ({ questionId: `${group}${n}`, score: n }))
  );
  assert.deepEqual(scoreAssessment(answers), { VN: 3, PT: 3, PB: 3, TT: 3 });
});

test("rejects incomplete, duplicate, or out-of-range assessment answers", () => {
  const answers = ["VN", "PT", "PB", "TT"].flatMap((group) =>
    [1, 2, 3, 4, 5].map((n) => ({ questionId: `${group}${n}`, score: n }))
  );
  const duplicateAnswers = [...answers];
  duplicateAnswers[19] = answers[0];
  assert.throws(() => scoreAssessment(answers.slice(1)), /đủ 20/);
  assert.throws(() => scoreAssessment(duplicateAnswers), /không hợp lệ/);
  assert.throws(() => scoreAssessment(answers.map((answer, index) => index === 0 ? { ...answer, score: 6 } : answer)), /không hợp lệ/);
});

test("focus streak needs a 60-minute session", () => {
  assert.equal(FOCUS_STREAK_MINUTES, 60);
});

test("computeStreak counts consecutive days ending today or yesterday", () => {
  assert.equal(computeStreak([], "2026-10-21"), 0);
  assert.equal(computeStreak(["2026-10-21", "2026-10-20", "2026-10-19"], "2026-10-21"), 3);
  // Hôm nay chưa tập thì chuỗi vẫn còn nếu hôm qua đã có.
  assert.equal(computeStreak(["2026-10-20", "2026-10-19"], "2026-10-21"), 2);
});

test("computeStreak resets after a gap and across month boundaries", () => {
  assert.equal(computeStreak(["2026-10-18", "2026-10-17"], "2026-10-21"), 0);
  assert.equal(computeStreak(["2026-10-21", "2026-10-19"], "2026-10-21"), 1);
  assert.equal(computeStreak(["2026-03-01", "2026-02-28", "2026-02-27"], "2026-03-01"), 3);
});
