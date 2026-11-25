import test from "node:test";
import assert from "node:assert/strict";
import {
  describeDbError, isSensitiveColumn, isWritableColumn, normalizeInput, parseListQuery, stripSensitive, validateKey, validateValues
} from "./dbAdminDomain.js";

const columns = [
  { name: "id", type: "uuid", generated: false },
  { name: "minutes", type: "integer", generated: false },
  { name: "note", type: "text", generated: false },
  { name: "password_hash", type: "text", generated: false },
  { name: "access_token_hash", type: "character(64)", generated: false },
  { name: "total", type: "integer", generated: true }
];

test("parseListQuery dùng whitelist cột và giới hạn trang", () => {
  const names = ["id", "email"];
  const bad = parseListQuery({ sort: "id; DROP TABLE x", dir: "up", pageSize: "9999", page: "-2", q: "x".repeat(300) }, names);
  assert.deepEqual([bad.sort, bad.dir, bad.pageSize, bad.page, bad.offset, bad.search.length], [null, "desc", 50, 1, 0, 100]);
  const ok = parseListQuery({ sort: "email", dir: "ASC", pageSize: "100", page: "3", q: " an " }, names);
  assert.deepEqual([ok.sort, ok.dir, ok.pageSize, ok.offset, ok.search], ["email", "asc", 100, 200, "an"]);
});

test("cột hash/token bị ẩn, chỉ password_hash được ghi", () => {
  assert.ok(isSensitiveColumn("password_hash") && isSensitiveColumn("access_token_hash") && isSensitiveColumn("refresh_token_hash"));
  assert.ok(isWritableColumn("password_hash"));
  assert.ok(!isWritableColumn("access_token_hash"));
  assert.deepEqual(stripSensitive({ id: "1", password_hash: "x", access_token_hash: "y" }), { id: "1" });
});

test("normalizeInput: ô trống của cột không phải chữ là NULL, cột chữ giữ chuỗi rỗng", () => {
  assert.equal(normalizeInput("", "integer"), null);
  assert.equal(normalizeInput("  ", "timestamp with time zone"), null);
  assert.equal(normalizeInput("", "text"), "");
  assert.equal(normalizeInput("", "character varying(20)"), "");
  assert.equal(normalizeInput(5, "integer"), "5");
  assert.equal(normalizeInput(null, "text"), null);
  assert.throws(() => normalizeInput({}, "text"));
});

test("validateValues từ chối cột lạ, cột bảo vệ, cột tự tính", () => {
  assert.match(validateValues({ nope: "1" }, columns).error, /không có cột/);
  assert.match(validateValues({ access_token_hash: "1" }, columns).error, /bảo vệ/);
  assert.match(validateValues({ total: "1" }, columns).error, /tự tính/);
  assert.match(validateValues({}, columns).error, /Chưa có giá trị/);
  assert.match(validateValues([], columns).error, /không hợp lệ/);
  assert.equal(validateValues({}, columns, { allowEmpty: true }).entries.length, 0);
  const ok = validateValues({ minutes: "", note: "", password_hash: "abc12345" }, columns);
  assert.deepEqual(ok.entries.map((entry) => entry.value), [null, "", "abc12345"]);
});

test("validateKey yêu cầu đủ và đúng các cột khóa chính", () => {
  assert.match(validateKey({ id: "1" }, []).error, /không có khóa chính/);
  assert.ok(validateKey({ a: "1", b: "2" }, ["a", "b"]).key);
  assert.ok(validateKey({ a: "1" }, ["a", "b"]).error);
  assert.ok(validateKey({ a: "1", c: "2" }, ["a", "b"]).error);
  assert.ok(validateKey({ a: 1 }, ["a"]).error);
  assert.ok(validateKey({ a: "" }, ["a"]).error);
});

test("describeDbError dịch lỗi ràng buộc và bỏ qua lỗi hệ thống", () => {
  assert.equal(describeDbError({ code: "23505", constraint: "u" }).status, 409);
  assert.equal(describeDbError({ code: "23503" }).status, 409);
  assert.match(describeDbError({ code: "23502", column: "email" }).message, /email/);
  assert.equal(describeDbError({ code: "22P02", message: "bad uuid" }).status, 400);
  assert.equal(describeDbError({ code: "XX000" }), null);
  assert.equal(describeDbError(new Error("boom")), null);
});
