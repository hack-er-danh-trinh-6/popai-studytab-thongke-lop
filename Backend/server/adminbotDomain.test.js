import test from "node:test";
import assert from "node:assert/strict";
import {
  ADMINBOT_ROSTER, ADMINBOT_TEACHER_EMAIL, adminbotStudentEmail
} from "./adminbotDomain.js";

test("Adminbot roster contains 38 distinct students and reserved aliases", () => {
  assert.equal(ADMINBOT_ROSTER.length, 38);
  assert.equal(new Set(ADMINBOT_ROSTER).size, 38);
  assert.equal(ADMINBOT_TEACHER_EMAIL, "teacher@adminbot.invalid");
  const emails = ADMINBOT_ROSTER.map((_name, index) => adminbotStudentEmail(index));
  assert.equal(new Set(emails).size, 38);
  assert.equal(emails[0], "student01@adminbot.invalid");
  assert.equal(emails[37], "student38@adminbot.invalid");
  assert.ok(emails.every((email) => email.endsWith("@adminbot.invalid")));
});

test("Adminbot email helper rejects invalid indices", () => {
  assert.throws(() => adminbotStudentEmail(-1), RangeError);
  assert.throws(() => adminbotStudentEmail(38), RangeError);
  assert.throws(() => adminbotStudentEmail(1.5), RangeError);
});
