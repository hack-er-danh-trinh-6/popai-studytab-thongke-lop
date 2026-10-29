import assert from "node:assert/strict";
import test from "node:test";
import {
  ADMIN_TTL_MS, buildDailyMessage, isWithinSendWindow, issueAdminToken, localClock,
  validateSettings, validateTimetable, verifyAdminToken
} from "./timetableDomain.js";
import { issueGateToken, verifyGateToken } from "./classroomDomain.js";

test("validateTimetable chuẩn hoá, bỏ ô trống và sắp xếp", () => {
  const result = validateTimetable([
    { weekday: 3, session: "AFTERNOON", period: 2, subject: "  Tin   học " },
    { weekday: 2, session: "MORNING", period: 5, subject: "Toán" },
    { weekday: 2, session: "MORNING", period: 1, subject: "Văn" },
    { weekday: 2, session: "MORNING", period: 2, subject: "   " }
  ]);
  assert.deepEqual(result.entries.map((e) => `${e.weekday}${e.session[0]}${e.period}:${e.subject}`),
    ["2M1:Văn", "2M5:Toán", "3A2:Tin học"]);
});

test("validateTimetable từ chối dữ liệu sai", () => {
  const ok = { weekday: 2, session: "MORNING", period: 1, subject: "Toán" };
  assert.ok(validateTimetable("x").error);
  assert.ok(validateTimetable([{ ...ok, weekday: 7 }]).error);
  assert.ok(validateTimetable([{ ...ok, weekday: 1 }]).error);
  assert.ok(validateTimetable([{ ...ok, session: "EVENING" }]).error);
  assert.ok(validateTimetable([{ ...ok, period: 6 }]).error);
  assert.ok(validateTimetable([{ ...ok, period: 0 }]).error);
  assert.ok(validateTimetable([{ ...ok, period: "1" }]).error);
  assert.ok(validateTimetable([{ ...ok, subject: "x".repeat(41) }]).error);
  assert.ok(validateTimetable([ok, ok]).error);
  assert.deepEqual(validateTimetable([]).entries, []);
});

test("validateSettings", () => {
  assert.deepEqual(validateSettings({ sendTime: "06:30", enabled: true }), { sendTime: "06:30", enabled: true });
  assert.ok(validateSettings({ sendTime: "6:30", enabled: true }).error);
  assert.ok(validateSettings({ sendTime: "24:00", enabled: true }).error);
  assert.ok(validateSettings({ sendTime: "06:30", enabled: "yes" }).error);
});

test("buildDailyMessage: chỉ lấy đúng ngày, có/không có buổi chiều", () => {
  const entries = [
    { weekday: 2, session: "MORNING", period: 1, subject: "Toán" },
    { weekday: 2, session: "MORNING", period: 2, subject: "Văn" },
    { weekday: 2, session: "AFTERNOON", period: 1, subject: "Anh" },
    { weekday: 3, session: "MORNING", period: 1, subject: "Lý" }
  ];
  assert.deepEqual(buildDailyMessage(2, entries), {
    title: "Thời khóa biểu Thứ 2", body: "Sáng: Tiết 1: Toán · Tiết 2: Văn\nChiều: Tiết 1: Anh"
  });
  assert.equal(buildDailyMessage(3, entries).body, "Sáng: Tiết 1: Lý");
  assert.equal(buildDailyMessage(4, entries), null);
});

test("buildDailyMessage luôn vừa giới hạn của thông báo (tối đa 10 tiết x 40 ký tự)", () => {
  const entries = [];
  for (const session of ["MORNING", "AFTERNOON"]) {
    for (let period = 1; period <= 5; period++) entries.push({ weekday: 4, session, period, subject: "Ä".repeat(40) });
  }
  const message = buildDailyMessage(4, entries);
  assert.ok(message.body.length <= 500 && message.body.length > 0);
  assert.ok(message.title.length <= 80);
});

test("localClock theo múi giờ Việt Nam (UTC+7), qua nửa đêm UTC", () => {
  // 2026-10-20 18:30 UTC = 2026-10-21 01:30 giờ VN (Thứ 4)
  const clock = localClock(new Date("2026-10-20T18:30:00Z"), "Asia/Ho_Chi_Minh");
  assert.deepEqual(clock, { date: "2026-10-21", weekday: 4, minutes: 90 });
  // Chủ nhật 2026-10-25 -> 8; Thứ 2 2026-10-26 -> 2
  assert.equal(localClock(new Date("2026-10-25T03:00:00Z"), "Asia/Ho_Chi_Minh").weekday, 8);
  assert.equal(localClock(new Date("2026-10-26T03:00:00Z"), "Asia/Ho_Chi_Minh").weekday, 2);
  // Nửa đêm không bị hiểu thành 24:xx
  assert.equal(localClock(new Date("2026-10-20T17:05:00Z"), "Asia/Ho_Chi_Minh").minutes, 5);
});

test("isWithinSendWindow: chỉ Thứ 2-6, sau giờ gửi và trong cửa sổ bù", () => {
  const at = (weekday, hhmm) => ({ date: "2026-10-21", weekday, minutes: Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3)) });
  assert.equal(isWithinSendWindow(at(4, "05:59"), "06:00"), false);
  assert.equal(isWithinSendWindow(at(4, "06:00"), "06:00"), true);
  assert.equal(isWithinSendWindow(at(4, "09:59"), "06:00"), true);
  assert.equal(isWithinSendWindow(at(4, "10:00"), "06:00"), false);
  assert.equal(isWithinSendWindow(at(7, "06:30"), "06:00"), false);
  assert.equal(isWithinSendWindow(at(8, "06:30"), "06:00"), false);
});

test("token admin: hợp lệ, hết hạn, sai mật khẩu và không dùng lẫn với cổng giáo viên", () => {
  const now = 1_000_000;
  const { token } = issueAdminToken("secret-pass", { now });
  assert.equal(verifyAdminToken(token, "secret-pass", { now: now + 1000 }), true);
  assert.equal(verifyAdminToken(token, "other-pass", { now: now + 1000 }), false);
  assert.equal(verifyAdminToken(token, "secret-pass", { now: now + ADMIN_TTL_MS + 1 }), false);
  assert.equal(verifyAdminToken(token + "x", "secret-pass", { now }), false);
  assert.equal(verifyAdminToken("", "secret-pass", { now }), false);
  assert.equal(verifyAdminToken(token, "", { now }), false);
  // Cùng mật khẩu nhưng token giáo viên không được chấp nhận làm token admin (và ngược lại)
  const gate = issueGateToken("secret-pass", { now });
  assert.equal(verifyAdminToken(gate.token, "secret-pass", { now }), false);
  assert.equal(verifyGateToken(token, "secret-pass", { now }), false);
});
