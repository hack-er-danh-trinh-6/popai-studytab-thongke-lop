import test from "node:test";
import assert from "node:assert/strict";
import { fillUsageDays, isValidUsageDate, parseUsageUpload, rankByUsage, summarizeUsage } from "./usageDomain.js";

const NOW = Date.parse("2026-11-22T05:00:00Z");

test("isValidUsageDate từ chối ngày sai định dạng, tương lai xa và quá cũ", () => {
  assert.equal(isValidUsageDate("2026-11-22", NOW), true);
  assert.equal(isValidUsageDate("2026-11-23", NOW), true);
  assert.equal(isValidUsageDate("2026-11-30", NOW), false);
  assert.equal(isValidUsageDate("2026-09-01", NOW), false);
  assert.equal(isValidUsageDate("2026-02-30", NOW), false);
  assert.equal(isValidUsageDate("22/11/2026", NOW), false);
});

test("parseUsageUpload kiểm tra app, phút và danh sách", () => {
  assert.ok(parseUsageUpload({ entries: [{ date: "2026-11-22", app: "tiktok", minutes: 30 }] }, NOW).entries);
  assert.ok(parseUsageUpload({}, NOW).error);
  assert.ok(parseUsageUpload({ entries: [{ date: "2026-11-22", app: "facebook", minutes: 5 }] }, NOW).error);
  assert.ok(parseUsageUpload({ entries: [{ date: "2026-11-22", app: "youtube", minutes: -1 }] }, NOW).error);
  assert.ok(parseUsageUpload({ entries: [{ date: "2026-11-22", app: "youtube", minutes: 1.5 }] }, NOW).error);
  assert.ok(parseUsageUpload({ entries: [{ date: "2026-11-22", app: "youtube", minutes: 2000 }] }, NOW).error);
});

test("summarizeUsage xử lý lớp rỗng và tính trung bình mỗi ngày", () => {
  assert.equal(summarizeUsage([], 7).totalMinutes, 0);
  const s = summarizeUsage([
    { tiktok: 70, youtube: 0, lastReport: "x" },
    { tiktok: 0, youtube: 140, lastReport: null }
  ], 7);
  assert.equal(s.totalMinutes, 210);
  assert.equal(s.reportingStudents, 1);
  assert.equal(s.tiktokAvgPerStudentPerDay, 5);
  assert.equal(s.youtubeAvgPerStudentPerDay, 10);
});

test("rankByUsage xếp giảm dần", () => {
  const r = rankByUsage([
    { fullName: "B", tiktok: 5, youtube: 0 },
    { fullName: "A", tiktok: 10, youtube: 20 },
    { fullName: "C", tiktok: 0, youtube: 0 }
  ]);
  assert.deepEqual(r.map((x) => x.fullName), ["A", "B", "C"]);
});

test("fillUsageDays đủ ngày, thiếu = 0", () => {
  const r = fillUsageDays([{ date: "2026-11-22", tiktok: 5, youtube: 7 }], "2026-11-22", 3);
  assert.deepEqual(r.map((x) => x.date), ["2026-11-20", "2026-11-21", "2026-11-22"]);
  assert.equal(r[2].youtube, 7);
  assert.equal(r[0].tiktok, 0);
});
