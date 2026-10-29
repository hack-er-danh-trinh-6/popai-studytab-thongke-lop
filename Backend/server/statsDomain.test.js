import test from "node:test";
import assert from "node:assert/strict";
import { csvCell, escapeLike, fillBuckets, parseRange, parseUserListQuery, scoreBucket, toCsv, USER_SORTS } from "./statsDomain.js";

test("parseRange chỉ nhận 7/30/90 ngày, còn lại về 30", () => {
  assert.equal(parseRange("7"), 7);
  assert.equal(parseRange(90), 90);
  assert.equal(parseRange("15"), 30);
  assert.equal(parseRange("abc"), 30);
  assert.equal(parseRange(undefined), 30);
});

test("parseUserListQuery dùng whitelist cho cột sắp xếp và giới hạn trang", () => {
  const bad = parseUserListQuery({ sort: "u.id; DROP TABLE pop_users", dir: "sideways", role: "ADMIN", pageSize: "9999", page: "-3" });
  assert.equal(bad.sort, "createdAt");
  assert.equal(bad.dir, "desc");
  assert.equal(bad.role, "");
  assert.equal(bad.pageSize, 100);
  assert.equal(bad.page, 1);
  const ok = parseUserListQuery({ sort: "focusMinutes", dir: "asc", role: "TEACHER", q: "  an  ", page: "3", pageSize: "20" });
  assert.deepEqual([ok.sort, ok.dir, ok.role, ok.search, ok.offset], ["focusMinutes", "asc", "TEACHER", "an", 40]);
  assert.ok(Object.hasOwn(USER_SORTS, ok.sort));
  assert.equal(parseUserListQuery({ all: "1" }).pageSize, 5000);
});

test("escapeLike vô hiệu các ký tự đại diện của LIKE", () => {
  assert.equal(escapeLike("50%_a\\b"), "50\\%\\_a\\\\b");
});

test("scoreBucket làm tròn và kẹp trong 1..5", () => {
  assert.equal(scoreBucket(2.4), 2);
  assert.equal(scoreBucket(2.5), 3);
  assert.equal(scoreBucket(0), 1);
  assert.equal(scoreBucket(9), 5);
  assert.equal(scoreBucket("x"), null);
});

test("fillBuckets bổ sung mốc thiếu bằng 0", () => {
  assert.deepEqual(fillBuckets([{ bucket: 2, count: 3 }], [1, 2, 3]), [
    { key: "1", count: 0 }, { key: "2", count: 3 }, { key: "3", count: 0 }
  ]);
});

test("csvCell chặn formula injection và escape dấu nháy/phẩy/xuống dòng", () => {
  assert.equal(csvCell("=HYPERLINK(\"x\")"), "\"'=HYPERLINK(\"\"x\"\")\"");
  assert.equal(csvCell("+84 123"), "'+84 123");
  assert.equal(csvCell("a,b"), "\"a,b\"");
  assert.equal(csvCell(null), "");
  assert.equal(csvCell(12), "12");
  assert.equal(toCsv(["a", "b"], [[1, "x\ny"]]), "\uFEFFa,b\r\n1,\"x\ny\"");
});
