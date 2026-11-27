import assert from "node:assert/strict";
import test from "node:test";

import {
  appHubUrl,
  buildUpdateMessage,
  buildUpdateMessageWithLink,
  buildAppVersionRow,
  validateVersion,
  validateVersionCode,
  DEFAULT_APP_HUB_URL,
  VERSION_PATTERN
} from "./updateappDomain.js";

test("thông báo cập nhật có/không số phiên bản", () => {
  assert.match(buildUpdateMessage("1.4.0").body, /POP AI 1\.4\.0 đã sẵn sàng/);
  assert.match(buildUpdateMessage("").body, /POP AI đã sẵn sàng/);
  assert.ok(buildUpdateMessage("").title.length <= 80);
  assert.ok(buildUpdateMessage("x".repeat(30)).body.length <= 500);
});

test("liên kết App Hub mặc định và chỉ chấp nhận https", () => {
  delete process.env.APP_HUB_URL;
  assert.equal(appHubUrl(), DEFAULT_APP_HUB_URL);
  assert.equal(DEFAULT_APP_HUB_URL, "https://apphub-pop.onrender.com");
  process.env.APP_HUB_URL = "http://evil.example";
  assert.equal(appHubUrl(), DEFAULT_APP_HUB_URL);
  process.env.APP_HUB_URL = "https://hub.example.com/pop";
  assert.equal(appHubUrl(), "https://hub.example.com/pop");
  delete process.env.APP_HUB_URL;
});

test("số phiên bản hợp lệ", () => {
  for (const ok of ["1.4.0", "2", "v1.0-beta", "1.0.0+12"]) assert.ok(VERSION_PATTERN.test(ok), ok);
  for (const bad of ["", "<b>", "1.0\n", "x".repeat(31), "-1"]) assert.ok(!VERSION_PATTERN.test(bad), bad);
});

test("validateVersion chấp nhận/chối", () => {
  assert.equal(validateVersion("1.4.0").ok, true);
  assert.equal(validateVersion("1.4.0").version, "1.4.0");
  assert.equal(validateVersion("").ok, false);
  assert.equal(validateVersion(null).ok, false);
  assert.equal(validateVersion("x".repeat(31)).ok, false);
  assert.equal(validateVersion("<script>").ok, false);
});

test("validateVersionCode chấp nhận/chối", () => {
  assert.equal(validateVersionCode(undefined).ok, true);
  assert.equal(validateVersionCode(null).ok, true);
  assert.equal(validateVersionCode("").ok, true);
  assert.equal(validateVersionCode(2).ok, true);
  assert.equal(validateVersionCode(2).versionCode, 2);
  assert.equal(validateVersionCode(0).ok, false);
  assert.equal(validateVersionCode(-1).ok, false);
  assert.equal(validateVersionCode("abc").ok, false);
});

test("buildUpdateMessageWithLink gán linkUrl", () => {
  const msg = buildUpdateMessageWithLink("1.4.0", "https://example.com/app.apk");
  assert.equal(msg.title, "Có bản cập nhật POP AI mới");
  assert.match(msg.body, /POP AI 1\.4\.0 đã sẵn sàng/);
  assert.equal(msg.linkUrl, "https://example.com/app.apk");
});

test("buildAppVersionRow tạo đối tượng phiên bản", () => {
  const row = buildAppVersionRow("1.4.0", 2, "app.apk", "https://example.com/app.apk", 12345, "admin");
  assert.equal(row.version, "1.4.0");
  assert.equal(row.versionCode, 2);
  assert.equal(row.apkFileName, "app.apk");
  assert.equal(row.apkUrl, "https://example.com/app.apk");
  assert.equal(row.apkSize, 12345);
  assert.equal(row.isActive, true);
  assert.equal(row.uploadedBy, "admin");
});
